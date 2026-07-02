package com.margelo.nitro.nitrolocationtracking

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Per-fix HTTP POST that runs entirely on the native thread, so it keeps
 * delivering while the JS thread is suspended (screen off / backgrounded /
 * Doze — the location foreground service keeps this process and its network
 * access alive). Fed by LocationEngine.onLocationNative.
 *
 * Two delivery modes, chosen by [LivePushConfig.persistQueue]:
 *
 *  - **Fire-and-forget** (default): one async POST per fix, dropped on failure.
 *  - **Durable queue** (persistQueue=true): each fix is written to a SQLite
 *    queue ([LocationQueue]) and a background drainer POSTs it — deleting rows
 *    only on 2xx, retrying with backoff on failure. Survives kill / reboot and
 *    supports batching ([LivePushConfig.batchSize]).
 *
 * Generic by design: the lib owns transport, the app owns schema. The per-fix
 * body is the parsed [LivePushConfig.extraFieldsJson] object with the location
 * fields merged on top. Batched POSTs send a JSON array of those objects.
 */
class LivePusher {
  companion object {
    private const val TAG = "LivePusher"
    private const val DEFAULT_MAX_QUEUE = 10000
    private const val RESULT_BUFFER_MAX = 50
    private const val BACKOFF_START_MS = 2000L
    private const val BACKOFF_MAX_MS = 60000L
  }

  private val client = OkHttpClient.Builder()
    .callTimeout(30, TimeUnit.SECONDS)
    .build()
  private val jsonMediaType = "application/json".toMediaType()

  @Volatile private var config: LivePushConfig? = null
  @Volatile private var enabled = false

  // Durable-queue plumbing. The queue is created lazily once a Context is
  // attached (ensureInitialized). All DB access + draining runs on this single
  // serial executor so rows are never touched concurrently.
  private var queue: LocationQueue? = null
  private val drainExecutor = Executors.newSingleThreadExecutor()
  @Volatile private var draining = false
  @Volatile private var backoffMs = BACKOFF_START_MS

  /**
   * Observer of each POST outcome. When null (JS suspended) outcomes are held
   * in [resultBuffer] and replayed when a callback is next registered.
   */
  @Volatile private var onResult: ((LivePushResult) -> Unit)? = null
  private val resultBuffer = ArrayDeque<LivePushResult>()
  private val resultLock = Any()

  fun attachContext(context: Context) {
    if (queue == null) queue = LocationQueue(context.applicationContext)
  }

  fun configure(c: LivePushConfig) { config = c }
  fun setEnabled(value: Boolean) { enabled = value }
  fun clear() { config = null; enabled = false }

  fun setOnResult(cb: ((LivePushResult) -> Unit)?) {
    onResult = cb
    // Replay anything buffered while JS was away, in order.
    if (cb != null) {
      val pending: List<LivePushResult>
      synchronized(resultLock) {
        pending = resultBuffer.toList()
        resultBuffer.clear()
      }
      pending.forEach { cb.invoke(it) }
    }
  }

  private fun emitResult(result: LivePushResult) {
    val cb = onResult
    if (cb != null) {
      cb.invoke(result)
    } else {
      synchronized(resultLock) {
        resultBuffer.addLast(result)
        while (resultBuffer.size > RESULT_BUFFER_MAX) resultBuffer.removeFirst()
      }
    }
  }

  // ── Ingest ────────────────────────────────────────────────────────────────

  fun push(loc: LocationData) {
    if (!enabled) return
    val cfg = config ?: return
    if (cfg.url.isBlank()) return

    val body = buildBody(cfg, loc) ?: return

    if (cfg.persistQueue == true) {
      val q = queue
      if (q == null) {
        Log.w(TAG, "persistQueue set but no Context attached — dropping fix")
        return
      }
      drainExecutor.execute {
        q.enqueue(body, System.currentTimeMillis())
        q.trimToMax((cfg.maxQueueSize?.toInt() ?: DEFAULT_MAX_QUEUE))
      }
      scheduleDrain()
    } else {
      sendSingle(cfg, body) // fire-and-forget
    }
  }

  private fun buildBody(cfg: LivePushConfig, loc: LocationData): String? {
    return try {
      val obj = if (cfg.extraFieldsJson.isNotBlank()) JSONObject(cfg.extraFieldsJson) else JSONObject()
      obj.put("latitude", loc.latitude)
      obj.put("longitude", loc.longitude)
      obj.put("timestamp", loc.timestamp)
      if (cfg.includeFullPoint) {
        obj.put("speed", loc.speed)
        obj.put("bearing", loc.bearing)
        obj.put("accuracy", loc.accuracy)
        obj.put("altitude", loc.altitude)
      }
      obj.toString()
    } catch (e: Exception) {
      Log.w(TAG, "invalid extraFieldsJson: ${e.message}")
      null
    }
  }

  // ── Fire-and-forget path ────────────────────────────────────────────────────

  private fun sendSingle(cfg: LivePushConfig, body: String) {
    val req = buildRequest(cfg, body)
    client.newCall(req).enqueue(object : Callback {
      override fun onResponse(call: Call, response: Response) {
        response.use {
          val code = it.code
          val ok = code in 200..299
          emitResult(LivePushResult(ok, code.toDouble(), if (ok) "" else code.toString()))
          if (code == 401) { Log.w(TAG, "401 — clearing stale config"); config = null }
        }
      }
      override fun onFailure(call: Call, e: IOException) {
        Log.w(TAG, "push failed: ${e.message}")
        emitResult(LivePushResult(false, 0.0, e.message ?: "network error"))
      }
    })
  }

  // ── Durable-queue drain path ────────────────────────────────────────────────

  private fun scheduleDrain() {
    if (draining) return
    val cfg = config ?: return
    val delay = (cfg.batchMaxDelayMs?.toLong() ?: 0L).coerceAtLeast(0L)
    draining = true
    if (delay > 0) {
      drainExecutor.execute { try { Thread.sleep(delay) } catch (_: InterruptedException) {} ; drainOnce() }
    } else {
      drainExecutor.execute { drainOnce() }
    }
  }

  /** Blocking (runs on drainExecutor): returns true when the queue is empty. */
  fun forceSyncBlocking(): Boolean {
    return try {
      // Explicit Callable — a bare lambda is ambiguous between submit(Runnable)
      // and submit(Callable<T>), which fails type inference.
      drainExecutor.submit(Callable { drainLoop() }).get(35, TimeUnit.SECONDS)
    } catch (e: Exception) {
      Log.w(TAG, "forceSync failed: ${e.message}")
      false
    }
  }

  fun queuedCount(): Int = queue?.count() ?: 0

  private fun drainOnce() {
    draining = false
    drainLoop()
  }

  /** Drains synchronously on the current (drainExecutor) thread. */
  private fun drainLoop(): Boolean {
    val q = queue ?: return true
    val cfg = config ?: return q.count() == 0
    val batchSize = (cfg.batchSize?.toInt() ?: 1).coerceAtLeast(1)

    while (true) {
      if (!enabled) return q.count() == 0
      val rows = q.peek(batchSize)
      if (rows.isEmpty()) { backoffMs = BACKOFF_START_MS; return true }

      val outcome = postBatch(cfg, rows)
      when (outcome.disposition) {
        Disposition.DELIVERED -> {
          q.delete(rows.map { it.id })
          backoffMs = BACKOFF_START_MS
          emitResult(LivePushResult(true, outcome.code.toDouble(), ""))
          // loop again immediately for the next batch
        }
        Disposition.DROP -> {
          // Non-retryable (e.g. 400/401): drop these rows so we don't wedge the
          // queue forever on a poison payload. 401 also clears config.
          q.delete(rows.map { it.id })
          emitResult(LivePushResult(false, outcome.code.toDouble(), outcome.error))
          if (outcome.code == 401) { Log.w(TAG, "401 — clearing stale config"); config = null; return q.count() == 0 }
        }
        Disposition.RETRY -> {
          // Transient (network / 5xx): keep rows, back off, stop this pass.
          emitResult(LivePushResult(false, outcome.code.toDouble(), outcome.error))
          scheduleBackoffRetry()
          return false
        }
      }
    }
  }

  private fun scheduleBackoffRetry() {
    val delay = backoffMs
    backoffMs = (backoffMs * 2).coerceAtMost(BACKOFF_MAX_MS)
    if (draining) return
    draining = true
    drainExecutor.execute {
      try { Thread.sleep(delay) } catch (_: InterruptedException) {}
      drainOnce()
    }
  }

  private enum class Disposition { DELIVERED, RETRY, DROP }
  private data class Outcome(val disposition: Disposition, val code: Int, val error: String)

  private fun postBatch(cfg: LivePushConfig, rows: List<LocationQueue.Row>): Outcome {
    val bodyStr = if (rows.size == 1) {
      rows[0].body
    } else {
      // batchSize > 1 → JSON array of the per-fix objects.
      val arr = JSONArray()
      rows.forEach { arr.put(JSONObject(it.body)) }
      arr.toString()
    }
    val req = buildRequest(cfg, bodyStr)
    return try {
      client.newCall(req).execute().use { resp ->
        val code = resp.code
        when {
          code in 200..299 -> Outcome(Disposition.DELIVERED, code, "")
          code == 401 || code == 400 || code == 413 -> Outcome(Disposition.DROP, code, code.toString())
          else -> Outcome(Disposition.RETRY, code, code.toString()) // 5xx, 429, etc.
        }
      }
    } catch (e: IOException) {
      Outcome(Disposition.RETRY, 0, e.message ?: "network error")
    }
  }

  private fun buildRequest(cfg: LivePushConfig, body: String): Request =
    Request.Builder()
      .url(cfg.url)
      .addHeader("Authorization", "Bearer ${cfg.authToken}")
      .post(body.toRequestBody(jsonMediaType))
      .build()
}
