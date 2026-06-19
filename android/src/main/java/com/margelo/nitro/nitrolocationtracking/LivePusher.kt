package com.margelo.nitro.nitrolocationtracking

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Per-fix HTTP POST that runs entirely on the native thread, so it keeps
 * delivering while the JS thread is suspended (screen off / backgrounded /
 * Doze — the location foreground service keeps this process and its network
 * access alive). Fed by LocationEngine.onLocationNative.
 *
 * Three gates decide whether a fix is sent (all controlled from JS, on rare
 * events while JS is awake): tracking must be running (no fix otherwise),
 * [enabled] must be true, and [config] must be set.
 *
 * Generic by design: the lib owns transport, the app owns schema. The body is
 * the parsed [LivePushConfig.extraFieldsJson] object with the location fields
 * merged on top.
 */
class LivePusher {
  companion object {
    private const val TAG = "LivePusher"
  }

  private val client = OkHttpClient.Builder()
    .callTimeout(10, TimeUnit.SECONDS)
    .build()
  private val jsonMediaType = "application/json".toMediaType()

  @Volatile private var config: LivePushConfig? = null
  @Volatile private var enabled = false

  /**
   * Optional observer of each POST outcome. Invoked from OkHttp's dispatcher
   * thread. Fires only while the JS thread is alive — results that land while
   * JS is suspended are not buffered (Nitro owns the JS-side delivery).
   */
  @Volatile private var onResult: ((LivePushResult) -> Unit)? = null

  fun configure(c: LivePushConfig) { config = c }
  fun setEnabled(value: Boolean) { enabled = value }
  fun setOnResult(cb: ((LivePushResult) -> Unit)?) { onResult = cb }
  fun clear() { config = null; enabled = false }

  fun push(loc: LocationData) {
    if (!enabled) return
    val cfg = config ?: return
    if (cfg.url.isBlank()) return

    val bodyString = try {
      val obj = if (cfg.extraFieldsJson.isNotBlank()) {
        JSONObject(cfg.extraFieldsJson)
      } else {
        JSONObject()
      }
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
      // Malformed extraFieldsJson — skip rather than crash the FGS thread.
      Log.w(TAG, "invalid extraFieldsJson: ${e.message}")
      return
    }

    val req = Request.Builder()
      .url(cfg.url)
      .addHeader("Authorization", "Bearer ${cfg.authToken}")
      .post(bodyString.toRequestBody(jsonMediaType))
      .build()

    // enqueue() runs on OkHttp's own dispatcher — never blocks the
    // foreground-service thread that delivered this fix.
    client.newCall(req).enqueue(object : Callback {
      override fun onResponse(call: Call, response: Response) {
        response.use {
          val code = it.code
          val ok = code in 200..299
          onResult?.invoke(
            LivePushResult(ok, code.toDouble(), if (ok) "" else code.toString())
          )
          if (code == 401) {
            // Token rotated while JS was asleep. Drop config so we stop
            // spamming 401s; JS re-configures on its next wake-up.
            Log.w(TAG, "401 — clearing stale config until JS re-configures")
            config = null
          }
        }
      }

      override fun onFailure(call: Call, e: IOException) {
        // Fire-and-forget: a dropped fix is corrected by the next one.
        Log.w(TAG, "push failed: ${e.message}")
        onResult?.invoke(LivePushResult(false, 0.0, e.message ?: "network error"))
      }
    })
  }
}
