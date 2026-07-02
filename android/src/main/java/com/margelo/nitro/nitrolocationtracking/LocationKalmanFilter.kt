package com.margelo.nitro.nitrolocationtracking

/**
 * Constant-position Kalman filter for a live GPS stream.
 *
 * A single scalar variance (in meters²) tracks the estimate's uncertainty. Each
 * update grows the variance by the process noise over the elapsed time (the
 * device could have moved), then blends in the new fix weighted by its reported
 * accuracy via the Kalman gain. Poor-accuracy fixes move the estimate less;
 * good ones move it more. The result smooths jitter and dampens outlier spikes
 * without the lag of a fixed moving average.
 *
 * This is the well-known 1-D GPS Kalman (position model, no explicit velocity
 * state), which is a good fit for pedestrian / vehicle tracking and cheap enough
 * to run on every fix.
 */
class LocationKalmanFilter(processNoiseMps: Double) {

  // Process noise in meters/second — how far the device may drift between fixes.
  private val q: Double = if (processNoiseMps > 0) processNoiseMps else 1.0

  private var lat = 0.0
  private var lng = 0.0
  private var timestampMs = 0L
  // Negative variance means "uninitialized" (no fix seen yet).
  private var variance = -1.0

  /** Discard all state so the next fix seeds the filter fresh. */
  fun reset() {
    variance = -1.0
  }

  /**
   * Feed a raw fix; returns the smoothed [lat, lng].
   *
   * @param accuracy horizontal accuracy in meters (clamped to a 1 m floor).
   */
  fun process(latitude: Double, longitude: Double, accuracyMeters: Double, timeMs: Long): DoubleArray {
    val accuracy = if (accuracyMeters < 1.0) 1.0 else accuracyMeters

    if (variance < 0) {
      // First fix — seed the estimate directly.
      lat = latitude
      lng = longitude
      timestampMs = timeMs
      variance = accuracy * accuracy
      return doubleArrayOf(lat, lng)
    }

    // Predict: uncertainty grows with the time gap since the last fix.
    val dtSec = (timeMs - timestampMs) / 1000.0
    if (dtSec > 0) {
      variance += dtSec * q * q
      timestampMs = timeMs
    }

    // Update: Kalman gain balances model vs. measurement.
    val gain = variance / (variance + accuracy * accuracy)
    lat += gain * (latitude - lat)
    lng += gain * (longitude - lng)
    variance *= (1.0 - gain)

    return doubleArrayOf(lat, lng)
  }
}
