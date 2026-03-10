package com.margelo.nitro.nitrolocationtracking

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.google.android.gms.location.*
import kotlin.coroutines.resume

class LocationEngine(private val context: Context) {
  companion object {
    private const val TAG = "NitroLocationEngine"
  }

  private val fusedClient =
    LocationServices.getFusedLocationProviderClient(context)
  private var locationCallback: LocationCallback? = null

  var onLocation: ((LocationData) -> Unit)? = null
  var onMotionChange: ((Boolean) -> Unit)? = null
  var dbWriter: NativeDBWriter? = null
  var currentRideId: String? = null
  var rejectMockLocations: Boolean = false
  val speedMonitor = SpeedMonitor()
  val tripCalculator = TripCalculator()
  private var lastSpeed = 0f
  private var tracking = false

  val isTracking: Boolean get() = tracking

  @SuppressLint("MissingPermission")
  fun start(config: LocationConfig) {
    if (tracking) {
      locationCallback?.let { fusedClient.removeLocationUpdates(it) }
    }

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
    fusedClient.requestLocationUpdates(
      request, locationCallback!!, Looper.getMainLooper())
    tracking = true
  }

  fun stop() {
    locationCallback?.let { fusedClient.removeLocationUpdates(it) }
    locationCallback = null
    tracking = false
  }

  @SuppressLint("MissingPermission")
  fun getCurrentLocation(callback: (LocationData?) -> Unit) {
    fusedClient.lastLocation.addOnSuccessListener { location ->
      if (location != null) {
        callback(locationToData(location))
      } else {
        callback(null)
      }
    }.addOnFailureListener {
      callback(null)
    }
  }

  suspend fun getCurrentLocationSuspend(): LocationData? {
    return kotlin.coroutines.suspendCoroutine { cont ->
      getCurrentLocation { data ->
        cont.resume(data)
      }
    }
  }

  private fun processLocation(location: Location) {
    val data = locationToData(location)

    // Skip mock locations if rejection is enabled
    if (rejectMockLocations && data.isMockLocation == true) {
      Log.d(TAG, "Rejecting mock location")
      return
    }

    onLocation?.invoke(data)

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

    // API 23+: check if any app holds MOCK_LOCATION permission via AppOpsManager
    try {
      val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
      // Use reflection to access undocumented OP_MOCK_LOCATION (op code 58)
      val opMockLocation = 58
      val method = AppOpsManager::class.java.getMethod(
        "checkOp", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
      )
      val result = method.invoke(
        appOps, opMockLocation, android.os.Process.myUid(), context.packageName
      ) as Int
      return result == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
      Log.w(TAG, "Could not check mock location app ops: ${e.message}")
    }
    return false
  }
}
