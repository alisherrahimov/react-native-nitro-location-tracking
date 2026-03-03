package com.margelo.nitro.nitrolocationtracking

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ConnectionManager {
    private var client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var webSocket: WebSocket? = null
    private var config: ConnectionConfig? = null
    private var reconnectAttempts = 0
    private var connected = false

    var onStateChange: ((ConnectionState) -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null
    var dbWriter: NativeDBWriter? = null

    val isConnected: Boolean get() = connected

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var syncRunnable: Runnable? = null

    fun configure(config: ConnectionConfig) {
        this.config = config
    }

    fun connect() {
        val cfg = config ?: return
        val request = Request.Builder().url(cfg.wsUrl)
            .addHeader("Authorization", "Bearer ${cfg.authToken}").build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, r: Response) {
                connected = true
                reconnectAttempts = 0
                onStateChange?.invoke(ConnectionState.CONNECTED)
                startSyncTimer()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                onMessage?.invoke(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
                handleDisconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                connected = false
                onStateChange?.invoke(ConnectionState.DISCONNECTED)
            }
        })
    }

    fun disconnect() {
        syncRunnable?.let { handler.removeCallbacks(it) }
        webSocket?.close(1000, "bye")
        connected = false
        onStateChange?.invoke(ConnectionState.DISCONNECTED)
    }

    fun send(msg: String) {
        webSocket?.send(msg)
    }

    fun getState(): ConnectionState {
        return if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
    }

    private fun handleDisconnect() {
        val cfg = config ?: return
        connected = false
        onStateChange?.invoke(ConnectionState.RECONNECTING)
        if (reconnectAttempts >= cfg.maxReconnectAttempts.toInt()) {
            onStateChange?.invoke(ConnectionState.DISCONNECTED)
            return
        }
        reconnectAttempts++
        handler.postDelayed({ connect() }, cfg.reconnectIntervalMs.toLong())
    }

    private fun startSyncTimer() {
        val cfg = config ?: return
        syncRunnable = object : Runnable {
            override fun run() {
                flushQueue()
                handler.postDelayed(this, cfg.syncIntervalMs.toLong())
            }
        }
        handler.postDelayed(syncRunnable!!, cfg.syncIntervalMs.toLong())
    }

    fun flushQueue(): Boolean {
        val cfg = config ?: return false
        val db = dbWriter ?: return false
        val queued = db.getUnsyncedBatch(cfg.batchSize.toInt())
        if (queued.isEmpty()) return true

        val ids = queued.map { it.first }
        val arr = JSONArray()
        for (pair in queued) {
            val loc = pair.second
            val obj = JSONObject()
            obj.put("latitude", loc.latitude)
            obj.put("longitude", loc.longitude)
            obj.put("altitude", loc.altitude)
            obj.put("speed", loc.speed)
            obj.put("bearing", loc.bearing)
            obj.put("accuracy", loc.accuracy)
            obj.put("timestamp", loc.timestamp)
            arr.put(obj)
        }

        if (connected) {
            send(arr.toString())
            db.markSynced(ids)
        } else {
            val body = arr.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("${cfg.restUrl}/locations/batch")
                .addHeader("Authorization", "Bearer ${cfg.authToken}")
                .post(body).build()
            client.newCall(req).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) db.markSynced(ids)
                }

                override fun onFailure(call: Call, e: java.io.IOException) {}
            })
        }
        return true
    }
}
