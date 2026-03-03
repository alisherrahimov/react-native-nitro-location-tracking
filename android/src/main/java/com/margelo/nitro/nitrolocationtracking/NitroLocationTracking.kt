package com.margelo.nitro.nitrolocationtracking

import android.util.Log
import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@DoNotStrip
class NitroLocationTracking : HybridNitroLocationTrackingSpec() {

    companion object {
        private const val TAG = "NitroLocationTracking"
    }

    private var locationEngine: LocationEngine? = null
    private var connectionManager = ConnectionManager()
    private var dbWriter: NativeDBWriter? = null
    private var notificationService: NotificationService? = null

    private var locationCallback: ((LocationData) -> Unit)? = null
    private var motionCallback: ((Boolean) -> Unit)? = null
    private var connectionStateCallback: ((ConnectionState) -> Unit)? = null
    private var messageCallback: ((String) -> Unit)? = null

    private var locationConfig: LocationConfig? = null

    private fun ensureInitialized(): Boolean {
        if (locationEngine != null) return true
        val context = NitroModules.applicationContext
        if (context == null) {
            Log.e(TAG, "NitroModules.applicationContext is null — cannot initialize")
            return false
        }
        locationEngine = LocationEngine(context)
        dbWriter = NativeDBWriter(context)
        notificationService = NotificationService(context)
        locationEngine?.dbWriter = dbWriter
        connectionManager.dbWriter = dbWriter
        Log.d(TAG, "Components initialized successfully")
        return true
    }

    // === Location Engine ===

    override fun configure(config: LocationConfig) {
        locationConfig = config
        ensureInitialized()
    }

    override fun startTracking() {
        val config = locationConfig ?: run {
            Log.w(TAG, "startTracking called but no config set — call configure() first")
            return
        }
        if (!ensureInitialized()) {
            Log.e(TAG, "startTracking failed — could not initialize components")
            return
        }
        val engine = locationEngine ?: return
        engine.onLocation = { data ->
            locationCallback?.invoke(data)
        }
        engine.onMotionChange = { isMoving ->
            motionCallback?.invoke(isMoving)
        }
        engine.start(config)

        try {
            notificationService?.startForegroundService(
                config.foregroundNotificationTitle,
                config.foregroundNotificationText
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not start foreground service — missing runtime permissions: ${e.message}")
        }
    }

    override fun stopTracking() {
        locationEngine?.stop()
        notificationService?.stopForegroundService()
    }

    override fun getCurrentLocation(): Promise<LocationData> {
        return Promise.async {
            suspendCoroutine { cont ->
                if (!ensureInitialized()) {
                    cont.resumeWithException(Exception("Location engine not initialized — context unavailable"))
                    return@suspendCoroutine
                }
                val engine = locationEngine!!
                engine.getCurrentLocation { data ->
                    if (data != null) {
                        cont.resume(data)
                    } else {
                        cont.resumeWithException(Exception("Failed to get location"))
                    }
                }
            }
        }
    }

    override fun isTracking(): Boolean {
        return locationEngine?.isTracking ?: false
    }

    override fun onLocation(callback: (location: LocationData) -> Unit) {
        locationCallback = callback
    }

    override fun onMotionChange(callback: (isMoving: Boolean) -> Unit) {
        motionCallback = callback
    }

    // === Connection Manager ===

    override fun configureConnection(config: ConnectionConfig) {
        connectionManager.configure(config)
    }

    override fun connectWebSocket() {
        connectionManager.onStateChange = { state ->
            connectionStateCallback?.invoke(state)
        }
        connectionManager.onMessage = { message ->
            messageCallback?.invoke(message)
        }
        connectionManager.connect()
    }

    override fun disconnectWebSocket() {
        connectionManager.disconnect()
    }

    override fun sendMessage(message: String) {
        connectionManager.send(message)
    }

    override fun getConnectionState(): ConnectionState {
        return connectionManager.getState()
    }

    override fun onConnectionStateChange(callback: (state: ConnectionState) -> Unit) {
        connectionStateCallback = callback
    }

    override fun onMessage(callback: (message: String) -> Unit) {
        messageCallback = callback
    }

    // === Sync Control ===

    override fun forceSync(): Promise<Boolean> {
        return Promise.async {
            connectionManager.flushQueue()
        }
    }

    // === Notifications ===

    override fun showLocalNotification(title: String, body: String) {
        ensureInitialized()
        notificationService?.showLocalNotification(title, body)
    }

    override fun updateForegroundNotification(title: String, body: String) {
        ensureInitialized()
        notificationService?.updateForegroundNotification(title, body)
    }

    // === Lifecycle ===

    override fun destroy() {
        locationEngine?.stop()
        connectionManager.disconnect()
        notificationService?.stopForegroundService()
    }
}
