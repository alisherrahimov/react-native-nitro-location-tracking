package com.margelo.nitro.nitrolocationtracking

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.proguard.annotations.DoNotStrip
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.ReactContext
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@DoNotStrip
class NitroLocationTracking : HybridNitroLocationTrackingSpec() {

    companion object {
        private const val TAG = "NitroLocationTracking"
        private const val PERMISSION_REQUEST_CODE = 9001
        private const val GPS_RESOLUTION_REQUEST_CODE = 9002

        /**
         * Process-wide listener invoked for every location fix, on the native
         * thread, without crossing the JS bridge. App-side native code (e.g. a
         * REST pusher) registers this once at startup (MainApplication.onCreate)
         * so fixes keep being delivered while the JS thread is suspended
         * (screen off / backgrounded / Doze). The active LocationEngine forwards
         * each fix here; it is wired automatically in ensureInitialized() and so
         * survives engine re-creation. Set to null to stop receiving fixes.
         */
        @Volatile
        @JvmStatic
        var nativeLocationListener: ((LocationData) -> Unit)? = null
    }

    private var locationEngine: LocationEngine? = null
    private val livePusher = LivePusher()
    private var connectionManager = ConnectionManager()
    private var notificationService: NotificationService? = null
    private var geofenceManager: GeofenceManager? = null
    private var providerStatusMonitor: ProviderStatusMonitor? = null
    private var permissionStatusMonitor: PermissionStatusMonitor? = null
    private var mockLocationMonitor: MockLocationMonitor? = null
    private var airplaneModeMonitor: AirplaneModeMonitor? = null

    private var locationCallback: ((LocationData) -> Unit)? = null
    private var motionCallback: ((Boolean) -> Unit)? = null
    private var connectionStateCallback: ((ConnectionState) -> Unit)? = null
    private var messageCallback: ((String) -> Unit)? = null
    private var geofenceCallback: ((GeofenceEvent, String) -> Unit)? = null
    private var speedAlertCallback: ((SpeedAlertType, Double) -> Unit)? = null
    private var providerStatusCallback: ((LocationProviderStatus, LocationProviderStatus) -> Unit)? = null
    private var permissionStatusCallback: ((PermissionStatus) -> Unit)? = null
    private var mockLocationCallback: ((Boolean) -> Unit)? = null
    private var airplaneModeCallback: ((Boolean) -> Unit)? = null

    private var locationConfig: LocationConfig? = null


    private fun ensureInitialized(): Boolean {
        if (locationEngine != null) return true
        val context = NitroModules.applicationContext
        if (context == null) {
            Log.e(TAG, "NitroModules.applicationContext is null — cannot initialize")
            return false
        }
        locationEngine = LocationEngine(context)
        notificationService = NotificationService(context)
        geofenceManager = GeofenceManager(context)
        providerStatusMonitor = ProviderStatusMonitor(context)
        permissionStatusMonitor = PermissionStatusMonitor(context)
        mockLocationMonitor = MockLocationMonitor(context)
        airplaneModeMonitor = AirplaneModeMonitor(context)

        // Forward every fix to (1) the built-in live pusher, and (2) the
        // process-wide native listener if the app registered one. Both run on
        // the native thread, so they keep firing while JS is suspended.
        // Wiring lives here so it survives engine re-creation.
        locationEngine?.onLocationNative = { data ->
            livePusher.push(data)
            nativeLocationListener?.invoke(data)
        }

        // Always watch for permission revocation so we can proactively tear
        // down tracking + the foreground service before the OS kills us for
        // holding a location-type FGS without the matching permission.
        permissionStatusMonitor?.setInternalCallback { status ->
            if (status == PermissionStatus.DENIED || status == PermissionStatus.RESTRICTED) {
                Log.w(TAG, "Location permission revoked — stopping tracking and foreground service")
                try {
                    locationEngine?.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping location engine: ${e.message}")
                }
                try {
                    notificationService?.stopForegroundService()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping foreground service: ${e.message}")
                }
            }
        }

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

        // Permission guard. Starting a foreground service of type `location`
        // without holding ACCESS_FINE_LOCATION throws SecurityException on
        // Android 14+. And even on older versions, requestLocationUpdates
        // will throw SecurityException. Fail closed and notify JS instead
        // of crashing the process.
        val permissionStatus = getLocationPermissionStatus()
        if (permissionStatus == PermissionStatus.DENIED ||
            permissionStatus == PermissionStatus.RESTRICTED) {
            Log.w(TAG, "startTracking aborted — location permission is $permissionStatus")
            permissionStatusCallback?.invoke(permissionStatus)
            return
        }

        val engine = locationEngine ?: return
        engine.onLocation = { data ->
            locationCallback?.invoke(data)
        }
        engine.onMotionChange = { isMoving ->
            motionCallback?.invoke(isMoving)
        }
        val started = engine.start(config)
        if (!started) {
            // engine.start() already logged the reason. Don't start the FGS
            // if tracking itself could not be started — the FGS would just
            // be killed immediately by the OS.
            Log.w(TAG, "startTracking aborted — location engine refused to start")
            return
        }

        try {
            notificationService?.startForegroundService(
                config.foregroundNotificationTitle,
                config.foregroundNotificationText
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not start foreground service — missing runtime permissions: ${e.message}")
            // Roll back the tracking session so we don't leak a location
            // request with no owning foreground service.
            try { engine.stop() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service: ${e.message}")
            try { engine.stop() } catch (_: Exception) {}
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

    // === Live Push ===

    override fun configureLivePush(config: LivePushConfig) {
        livePusher.configure(config)
    }

    override fun setLivePushEnabled(enabled: Boolean) {
        livePusher.setEnabled(enabled)
    }

    override fun clearLivePush() {
        livePusher.clear()
    }

    override fun onLivePushResult(callback: (result: LivePushResult) -> Unit) {
        livePusher.setOnResult(callback)
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

    override fun onMockLocationDetected(callback: (isMockEnabled: Boolean) -> Unit) {
        mockLocationCallback = callback
        ensureInitialized()
        mockLocationMonitor?.setCallback(callback)
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

    override fun openLocationSettings(): Promise<Boolean> {
        return Promise.async {
            suspendCoroutine { cont ->
                val context = NitroModules.applicationContext
                if (context == null) {
                    cont.resume(false)
                    return@suspendCoroutine
                }

                val reactContext = context as? ReactContext
                val activity = reactContext?.currentActivity
                if (reactContext == null || activity == null) {
                    Log.w(TAG, "openLocationSettings — no current Activity, using fallback")
                    openLocationSettingsFallback(context)
                    cont.resume(isLocationServicesEnabled())
                    return@suspendCoroutine
                }

                // Use a high-accuracy location request for the settings check.
                // These values are internal to the SettingsClient call — they do
                // not affect the tracking configuration.
                val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    10_000L
                ).build()

                val settingsRequest = com.google.android.gms.location.LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest)
                    .setAlwaysShow(true)
                    .build()

                val client = com.google.android.gms.location.LocationServices.getSettingsClient(context)
                val task = client.checkLocationSettings(settingsRequest)

                // Guard against double-resume — the continuation may otherwise
                // be resumed by both the success listener and the activity
                // result listener in rare race conditions.
                val resumed = AtomicBoolean(false)
                fun safeResume(value: Boolean) {
                    if (resumed.compareAndSet(false, true)) {
                        cont.resume(value)
                    }
                }

                task.addOnSuccessListener {
                    // Location settings are already satisfied — GPS is on.
                    safeResume(true)
                }

                task.addOnFailureListener { exception ->
                    if (exception is com.google.android.gms.common.api.ResolvableApiException) {
                        // Register the activity result listener BEFORE showing
                        // the dialog so we cannot miss the result.
                        val listener = object : BaseActivityEventListener() {
                            override fun onActivityResult(
                              activity: Activity,
                              requestCode: Int,
                              resultCode: Int,
                              data: Intent?
                            ) {
                                if (requestCode != GPS_RESOLUTION_REQUEST_CODE) return
                                reactContext.removeActivityEventListener(this)
                                // RESULT_OK = user tapped "OK" in the dialog and GPS is now on.
                                // RESULT_CANCELED = user tapped "No thanks" or dismissed.
                                val enabled = resultCode == Activity.RESULT_OK
                                safeResume(enabled)
                            }
                        }
                        reactContext.addActivityEventListener(listener)

                        try {
                            exception.startResolutionForResult(
                                activity, GPS_RESOLUTION_REQUEST_CODE
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to show resolution dialog: ${e.message}")
                            reactContext.removeActivityEventListener(listener)
                            openLocationSettingsFallback(context)
                            safeResume(isLocationServicesEnabled())
                        }
                    } else {
                        // Not resolvable (e.g. SETTINGS_CHANGE_UNAVAILABLE on an
                        // airplane-mode-locked device) — fall back to the system
                        // settings screen.
                        Log.w(TAG, "Location settings not resolvable: ${exception.message}")
                        openLocationSettingsFallback(context)
                        safeResume(isLocationServicesEnabled())
                    }
                }
            }
        }
    }

    private fun openLocationSettingsFallback(context: android.content.Context) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open location settings: ${e.message}")
        }
    }

    // === Device State Monitoring ===

    override fun isAirplaneModeEnabled(): Boolean {
        ensureInitialized()
        return airplaneModeMonitor?.isAirplaneModeEnabled() ?: false
    }

    override fun onAirplaneModeChange(callback: (isEnabled: Boolean) -> Unit) {
        airplaneModeCallback = callback
        ensureInitialized()
        airplaneModeMonitor?.setCallback(callback)
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

    // === Live Activity (Android no-op — Dynamic Island is iOS-only) ===

    override fun startLiveActivity(
        orderId: String,
        customerName: String,
        deliveryAddress: String,
        orderCount: Double,
        status: String,
        statusText: String,
        estimatedMinutes: Double,
        distanceMeters: Double
    ) { /* no-op */ }

    override fun updateLiveActivity(
        status: String,
        statusText: String,
        estimatedMinutes: Double,
        distanceMeters: Double
    ) { /* no-op */ }

    override fun endLiveActivity() { /* no-op */ }

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
        mockLocationMonitor?.destroy()
        airplaneModeMonitor?.destroy()
    }
}
