package com.margelo.nitro.nitrolocationtracking

class SpeedMonitor {

    private var config: SpeedConfig? = null
    private var callback: ((SpeedAlertType, Double) -> Unit)? = null
    private var currentState: SpeedAlertType = SpeedAlertType.NORMALIZED
    private var lastSpeedKmh: Double = 0.0

    fun configure(config: SpeedConfig) {
        this.config = config
    }

    fun setCallback(callback: (SpeedAlertType, Double) -> Unit) {
        this.callback = callback
    }

    fun getCurrentSpeed(): Double = lastSpeedKmh

    fun feedLocation(data: LocationData) {
        val cfg = config ?: return

        val speedKmh = data.speed * 3.6 // m/s → km/h
        lastSpeedKmh = speedKmh

        val newState = when {
            speedKmh > cfg.maxSpeedKmh -> SpeedAlertType.EXCEEDED
            speedKmh < cfg.minSpeedKmh -> SpeedAlertType.BELOW_MINIMUM
            else -> SpeedAlertType.NORMALIZED
        }

        // Only fire callback on state transitions
        if (newState != currentState) {
            currentState = newState
            callback?.invoke(newState, speedKmh)
        }
    }
}
