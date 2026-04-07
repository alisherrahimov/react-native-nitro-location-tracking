package com.margelo.nitro.nitrolocationtracking

import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.proguard.annotations.DoNotStrip
import com.facebook.react.bridge.ReactContext
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@DoNotStrip
class NitroLocationTracking : HybridNitroLocationTrackingSpec() {

    companion object {
        private const val TAG = "NitroLocationTracking"
        private const val PERMISSION_REQUEST_CODE = 9001
    }

    private var locationEngine: LocationEngine? = null
    private var connectionManager = ConnectionManager()
    private var dbWriter: NativeDBWriter? = null
    private var notificationService: NotificationService? = null
    private var geofenceManager: GeofenceManager? = null
    private var providerStatusMonitor: ProviderStatusMonitor? = null
    private var permissionStatusMonitor: PermissionStatusMonitor? = null

    private var locationCallback: ((LocationData) -> Unit)? = null
    private var motionCallback: ((Boolean) -> Unit)? = null
    private var connectionStateCallback: ((ConnectionState) -> Unit)? = null
    private var messageCallback: ((String) -> Unit)? = null
    private var geofenceCallback: ((GeofenceEvent, String) -> Unit)? = null
    private var speedAlertCallback: ((SpeedAlertType, Double) -> Unit)? = null
    private var providerStatusCallback: ((LocationProviderStatus, LocationProviderStatus) -> Unit)? = null
    private var permissionStatusCallback: ((PermissionStatus) -> Unit)? = null

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
        geofenceManager = GeofenceManager(context)
        providerStatusMonitor = ProviderStatusMonitor(context)
        permissionStatusMonitor = PermissionStatusMonitor(context)
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

    // === Fake GPS Detection ===

    override fun isFakeGpsEnabled(): Boolean {
        ensureInitialized()
        return locationEngine?.isFakeGpsEnabled() ?: false
    }

    override fun setRejectMockLocations(reject: Boolean) {
        ensureInitialized()
        locationEngine?.rejectMockLocations = reject
    }

    // === Geofencing ===

    override fun addGeofence(region: GeofenceRegion) {
        ensureInitialized()
        geofenceManager?.addGeofence(region)
    }

    override fun removeGeofence(regionId: String) {
        ensureInitialized()
        geofenceManager?.removeGeofence(regionId)
    }

    override fun removeAllGeofences() {
        ensureInitialized()
        geofenceManager?.removeAllGeofences()
    }

    override fun onGeofenceEvent(callback: (event: GeofenceEvent, regionId: String) -> Unit) {
        geofenceCallback = callback
        ensureInitialized()
        geofenceManager?.setCallback(callback)
    }

    // === Speed Monitoring ===

    override fun configureSpeedMonitor(config: SpeedConfig) {
        ensureInitialized()
        locationEngine?.speedMonitor?.configure(config)
    }

    override fun onSpeedAlert(callback: (alert: SpeedAlertType, currentSpeedKmh: Double) -> Unit) {
        speedAlertCallback = callback
        ensureInitialized()
        locationEngine?.speedMonitor?.setCallback(callback)
    }

    override fun getCurrentSpeed(): Double {
        return locationEngine?.speedMonitor?.getCurrentSpeed() ?: 0.0
    }

    // === Distance Calculator ===

    override fun startTripCalculation() {
        ensureInitialized()
        locationEngine?.tripCalculator?.start()
    }

    override fun stopTripCalculation(): TripStats {
        return locationEngine?.tripCalculator?.stop() ?: TripStats(
            distanceMeters = 0.0, durationMs = 0.0, averageSpeedKmh = 0.0,
            maxSpeedKmh = 0.0, pointCount = 0.0
        )
    }

    override fun getTripStats(): TripStats {
        return locationEngine?.tripCalculator?.getStats() ?: TripStats(
            distanceMeters = 0.0, durationMs = 0.0, averageSpeedKmh = 0.0,
            maxSpeedKmh = 0.0, pointCount = 0.0
        )
    }

    override fun resetTripCalculation() {
        locationEngine?.tripCalculator?.reset()
    }

    // === Location Provider Status ===

    override fun isLocationServicesEnabled(): Boolean {
        ensureInitialized()
        return providerStatusMonitor?.isLocationServicesEnabled() ?: false
    }

    override fun onProviderStatusChange(callback: (gps: LocationProviderStatus, network: LocationProviderStatus) -> Unit) {
        providerStatusCallback = callback
        ensureInitialized()
        providerStatusMonitor?.setCallback(callback)
    }

    // === Permission Status ===

    override fun getLocationPermissionStatus(): PermissionStatus {
        val context = NitroModules.applicationContext ?: return PermissionStatus.NOTDETERMINED

        val fineGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            return PermissionStatus.DENIED
        }

        // Fine location granted — check background
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val bgGranted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return if (bgGranted) PermissionStatus.ALWAYS else PermissionStatus.WHENINUSE
        }

        // Pre-Android 10: fine location = always (no separate background permission)
        return PermissionStatus.ALWAYS
    }

    override fun onPermissionStatusChange(callback: (status: PermissionStatus) -> Unit) {
        permissionStatusCallback = callback
        ensureInitialized()
        permissionStatusMonitor?.setCallback(callback)
    }

    override fun requestLocationPermission(): Promise<PermissionStatus> {
        return Promise.async {
            suspendCoroutine { cont ->
                val context = NitroModules.applicationContext
                if (context == null) {
                    cont.resume(PermissionStatus.DENIED)
                    return@suspendCoroutine
                }

                // Already granted — return current status immediately
                val fineGranted = ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (fineGranted) {
                    cont.resume(getLocationPermissionStatus())
                    return@suspendCoroutine
                }

                // Need to request — get the current Activity
                val activity = (context as? ReactContext)?.currentActivity
                if (activity == null) {
                    Log.e(TAG, "requestLocationPermission — no current Activity available")
                    cont.resume(PermissionStatus.DENIED)
                    return@suspendCoroutine
                }

                // Use PermissionAwareActivity if available (React Native's Activity)
                if (activity is com.facebook.react.modules.core.PermissionAwareActivity) {
                    activity.requestPermissions(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        PERMISSION_REQUEST_CODE
                    ) { requestCode, _, _ ->
                        if (requestCode == PERMISSION_REQUEST_CODE) {
                            cont.resume(getLocationPermissionStatus())
                        }
                        true // PermissionListener requires Boolean return
                    }
                } else {
                    // Fallback: use ActivityCompat (result won't be captured directly)
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        PERMISSION_REQUEST_CODE
                    )
                    // Poll for result after user interacts with the dialog
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        cont.resume(getLocationPermissionStatus())
                    }, 5000)
                }
            }
        }
    }

    // === Distance Utilities ===

    override fun getDistanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    override fun getDistanceToGeofence(regionId: String): Double {
        ensureInitialized()
        return geofenceManager?.distanceTo(regionId, locationEngine?.lastLocation) ?: -1.0
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
        geofenceManager?.destroy()
        providerStatusMonitor?.destroy()
        permissionStatusMonitor?.destroy()
    }
}
