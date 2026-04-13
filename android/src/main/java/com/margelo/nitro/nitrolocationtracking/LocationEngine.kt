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
//  var currentRideId: String? = null
  var rejectMockLocations: Boolean = false
  val speedMonitor = SpeedMonitor()
  val tripCalculator = TripCalculator()
  private var lastSpeed = 0f
  private var tracking = false
  var lastLocation: Location? = null
    private set

  val isTracking: Boolean get() = tracking

  @SuppressLint("MissingPermission")
  fun start(config: LocationConfig): Boolean {
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

  fun stop() {
    locationCallback?.let { fusedClient.removeLocationUpdates(it) }
    locationCallback = null
    tracking = false
  }

  @SuppressLint("MissingPermission")
  fun getCurrentLocation(callback: (LocationData?) -> Unit) {
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
