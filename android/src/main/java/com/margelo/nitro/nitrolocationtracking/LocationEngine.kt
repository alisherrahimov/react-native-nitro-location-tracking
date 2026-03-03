package com.margelo.nitro.nitrolocationtracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlin.coroutines.resume

class LocationEngine(private val context: Context) {
  private val fusedClient =
    LocationServices.getFusedLocationProviderClient(context)
  private var locationCallback: LocationCallback? = null

  var onLocation: ((LocationData) -> Unit)? = null
  var onMotionChange: ((Boolean) -> Unit)? = null
  var dbWriter: NativeDBWriter? = null
  var currentRideId: String? = null
  private var lastSpeed = 0f
  private var tracking = false

  val isTracking: Boolean get() = tracking

  @SuppressLint("MissingPermission")
  fun start(config: LocationConfig) {
    val priority = when (config.desiredAccuracy) {
      AccuracyLevel.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
      AccuracyLevel.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
      AccuracyLevel.LOW -> Priority.PRIORITY_LOW_POWER
    }
    val request = LocationRequest.Builder(priority, config.intervalMs.toLong())
      .setMinUpdateDistanceMeters(config.distanceFilter.toFloat())
      .setMinUpdateIntervalMillis(config.fastestIntervalMs.toLong())
      .build()

    locationCallback = object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        result.lastLocation?.let { processLocation(it) }
      }
    }
    fusedClient.requestLocationUpdates(
      request, locationCallback!!, Looper.getMainLooper())
    tracking = true
  }

  fun stop() {
    locationCallback?.let { fusedClient.removeLocationUpdates(it) }
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
//    dbWriter?.insert(data, currentRideId)
    onLocation?.invoke(data)

    val isMoving = location.speed > 0.5f
    if (isMoving != (lastSpeed > 0.5f)) onMotionChange?.invoke(isMoving)
    lastSpeed = location.speed
  }

  private fun locationToData(location: Location): LocationData {
    return LocationData(
      latitude = location.latitude,
      longitude = location.longitude,
      altitude = location.altitude,
      speed = location.speed.toDouble(),
      bearing = location.bearing.toDouble(),
      accuracy = location.accuracy.toDouble(),
      timestamp = location.time.toDouble()
    )
  }
}
