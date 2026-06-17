package com.margelo.nitro.nitrolocationtracking

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.google.android.gms.location.*

class LocationEngine(private val context: Context) {
  companion object {
    private const val TAG = "NitroLocationEngine"
  }

  private val fusedClient =
    LocationServices.getFusedLocationProviderClient(context)
  private var locationCallback: LocationCallback? = null

  // Platform LocationManager fallback. Used when Google Play Services is
  // missing or unusable (e.g. Honor / Huawei devices without GMS), where the
  // FusedLocationProviderClient silently never delivers a single update.
  private val locationManager =
    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
  private var platformListener: LocationListener? = null

  var onLocation: ((LocationData) -> Unit)? = null
  // Native-side consumer of every fix, invoked synchronously on the same thread
  // as onLocation but WITHOUT crossing the JS bridge. Lets app-side native code
  // (e.g. a REST pusher) keep delivering fixes while the JS thread is suspended
  // (screen off / backgrounded / Doze). See NitroLocationTracking.nativeLocationListener.
  var onLocationNative: ((LocationData) -> Unit)? = null
  var onMotionChange: ((Boolean) -> Unit)? = null
  var onMockLocationChanged: ((Boolean) -> Unit)? = null
//  var currentRideId: String? = null
  var rejectMockLocations: Boolean = false
  val speedMonitor = SpeedMonitor()
  val tripCalculator = TripCalculator()
  private var lastSpeed = 0f
  private var lastMockState: Boolean? = null
  private var tracking = false
  var lastLocation: Location? = null
    private set

  val isTracking: Boolean get() = tracking

  fun start(config: LocationConfig): Boolean {
    if (tracking) {
      removeUpdatesInternal()
    }
    return if (isGooglePlayServicesAvailable()) {
      startFused(config)
    } else {
      Log.w(TAG, "Google Play Services unavailable — using platform LocationManager fallback")
      startPlatform(config)
    }
  }

  @SuppressLint("MissingPermission")
  private fun startFused(config: LocationConfig): Boolean {
    val priority = when (config.desiredAccuracy) {
      AccuracyLevel.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
      AccuracyLevel.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
      AccuracyLevel.LOW -> Priority.PRIORITY_LOW_POWER
    }
    val request = LocationRequest.Builder(priority, config.intervalMs.toLong())
      .setMinUpdateDistanceMeters(config.distanceFilter.toFloat())
      .setMinUpdateIntervalMillis(config.fastestIntervalMs.toLong())
      .setWaitForAccurateLocation(false)
      .build()

    locationCallback = object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        result.lastLocation?.let { processLocation(it) }
      }

      override fun onLocationAvailability(availability: LocationAvailability) {
        Log.d(TAG, "onLocationAvailability — isLocationAvailable=${availability.isLocationAvailable}")
      }
    }
    return try {
      fusedClient.requestLocationUpdates(
        request, locationCallback!!, Looper.getMainLooper())
      tracking = true
      true
    } catch (e: SecurityException) {
      // Permission was revoked between our caller's check and this call, or
      // the FusedLocationProvider is otherwise refusing access. Fail closed
      // instead of crashing the process.
      Log.w(TAG, "requestLocationUpdates refused — permission missing: ${e.message}")
      locationCallback = null
      tracking = false
      false
    } catch (e: Exception) {
      Log.e(TAG, "requestLocationUpdates failed: ${e.message}")
      locationCallback = null
      tracking = false
      false
    }
  }

  @SuppressLint("MissingPermission")
  private fun startPlatform(config: LocationConfig): Boolean {
    val lm = locationManager ?: run {
      Log.e(TAG, "LocationManager unavailable — cannot start platform location updates")
      return false
    }

    val listener = object : LocationListener {
      override fun onLocationChanged(location: Location) {
        processLocation(location)
      }
      // onStatusChanged/onProviderEnabled/onProviderDisabled gained default
      // implementations in API 30 but are abstract on lower compileSdk levels —
      // override as no-ops so this compiles regardless of compileSdk.
      @Deprecated("Deprecated in Android API 29")
      override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
      override fun onProviderEnabled(provider: String) {}
      override fun onProviderDisabled(provider: String) {}
    }

    return try {
      val providers = buildList {
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
      }
      if (providers.isEmpty()) {
        Log.w(TAG, "No platform location providers enabled (GPS and network both off)")
        return false
      }
      for (provider in providers) {
        lm.requestLocationUpdates(
          provider,
          config.intervalMs.toLong(),
          config.distanceFilter.toFloat(),
          listener,
          Looper.getMainLooper()
        )
      }
      platformListener = listener
      tracking = true
      Log.d(TAG, "Platform location updates started on: ${providers.joinToString()}")
      true
    } catch (e: SecurityException) {
      Log.w(TAG, "platform requestLocationUpdates refused — permission missing: ${e.message}")
      platformListener = null
      tracking = false
      false
    } catch (e: Exception) {
      Log.e(TAG, "platform requestLocationUpdates failed: ${e.message}")
      platformListener = null
      tracking = false
      false
    }
  }

  /**
   * Returns true only when a usable Google Play Services is present. On devices
   * without GMS (e.g. post-2020 Honor / Huawei) the FusedLocationProviderClient
   * never throws but also never delivers updates, so we must detect this and
   * fall back to the platform LocationManager.
   */
  private fun isGooglePlayServicesAvailable(): Boolean {
    return try {
      com.google.android.gms.common.GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) ==
        com.google.android.gms.common.ConnectionResult.SUCCESS
    } catch (e: Throwable) {
      Log.w(TAG, "Google Play Services availability check failed: ${e.message}")
      false
    }
  }

  private fun removeUpdatesInternal() {
    locationCallback?.let { fusedClient.removeLocationUpdates(it) }
    locationCallback = null
    platformListener?.let { locationManager?.removeUpdates(it) }
    platformListener = null
  }

  fun stop() {
    removeUpdatesInternal()
    tracking = false
  }

  @SuppressLint("MissingPermission")
  fun getCurrentLocation(callback: (LocationData?) -> Unit) {
    if (!isGooglePlayServicesAvailable()) {
      callback(getPlatformLastKnownLocation()?.let { locationToData(it) })
      return
    }
    try {
      fusedClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
          callback(locationToData(location))
        } else {
          callback(null)
        }
      }.addOnFailureListener {
        callback(null)
      }
    } catch (e: SecurityException) {
      Log.w(TAG, "getCurrentLocation refused — permission missing: ${e.message}")
      callback(null)
    }
  }

  @SuppressLint("MissingPermission")
  private fun getPlatformLastKnownLocation(): Location? {
    val lm = locationManager ?: return null
    return try {
      listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider ->
          if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
        }
        .maxByOrNull { it.time }
    } catch (e: SecurityException) {
      Log.w(TAG, "getPlatformLastKnownLocation refused — permission missing: ${e.message}")
      null
    }
  }

//  suspend fun getCurrentLocationSuspend(): LocationData? {
//    return kotlin.coroutines.suspendCoroutine { cont ->
//      getCurrentLocation { data ->
//        cont.resume(data)
//      }
//    }
//  }

  private fun processLocation(location: Location) {
    lastLocation = location
    val data = locationToData(location)

    // Check mock location state change and notify
    val isMock = data.isMockLocation == true
    if (isMock != lastMockState) {
      lastMockState = isMock
      Log.d(TAG, "Mock location state changed: $isMock")
      onMockLocationChanged?.invoke(isMock)
    }

    // Skip mock locations if rejection is enabled
    if (rejectMockLocations && isMock) {
      Log.d(TAG, "Rejecting mock location")
      return
    }

    onLocation?.invoke(data)
    // Native consumer — fires even when the JS thread is suspended.
    onLocationNative?.invoke(data)

    // Feed to speed monitor and trip calculator
    speedMonitor.feedLocation(data)
    tripCalculator.feedLocation(data)

    val isMoving = location.speed > 0.5f
    if (isMoving != (lastSpeed > 0.5f)) onMotionChange?.invoke(isMoving)
    lastSpeed = location.speed
  }

  private fun locationToData(location: Location): LocationData {
    val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      location.isMock
    } else {
      @Suppress("DEPRECATION")
      location.isFromMockProvider
    }
    return LocationData(
      latitude = location.latitude,
      longitude = location.longitude,
      altitude = location.altitude,
      speed = location.speed.toDouble(),
      bearing = location.bearing.toDouble(),
      accuracy = location.accuracy.toDouble(),
      timestamp = location.time.toDouble(),
      isMockLocation = isMock
    )
  }

  @SuppressLint("DiscouragedPrivateApi")
  fun isFakeGpsEnabled(): Boolean {
    // Pre-API 23: check the global mock location setting
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      @Suppress("DEPRECATION")
      val mockSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ALLOW_MOCK_LOCATION
      )
      return mockSetting == "1"
    }

    // API 23+: scan all installed packages for OP_MOCK_LOCATION permission
    try {
      val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
      val opMockLocation = 58 // OP_MOCK_LOCATION
      val checkOp = AppOpsManager::class.java.getMethod(
        "checkOp", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
      )

      val pm = context.packageManager
      @Suppress("DEPRECATION")
      val packages = pm.getInstalledApplications(0)
      for (appInfo in packages) {
        if (appInfo.packageName == context.packageName) continue
        try {
          val result = checkOp.invoke(
            appOps, opMockLocation, appInfo.uid, appInfo.packageName
          ) as Int
          if (result == AppOpsManager.MODE_ALLOWED) {
            Log.d(TAG, "Mock location provider detected: ${appInfo.packageName}")
            return true
          }
        } catch (_: Exception) {
          // Some packages may not be queryable
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Could not check mock location app ops: ${e.message}")
    }
    return false
  }
}
