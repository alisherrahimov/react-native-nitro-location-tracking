package com.margelo.nitro.nitrolocationtracking

import okhttp3.*
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

    val isConnected: Boolean get() = connected

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

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

    // Location delivery now runs through LivePusher (per-fix native POST that
    // survives JS suspension). There is no local queue to drain, so forceSync
    // has nothing to flush — kept for API compatibility, always succeeds.
    fun flushQueue(): Boolean = true
}
