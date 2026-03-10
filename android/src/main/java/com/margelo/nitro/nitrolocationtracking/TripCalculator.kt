package com.margelo.nitro.nitrolocationtracking

import kotlin.math.*

class TripCalculator {

    private var active = false
    private var startTimeMs: Long = 0L
    private var totalDistanceMeters: Double = 0.0
    private var maxSpeedKmh: Double = 0.0
    private var speedSumKmh: Double = 0.0
    private var pointCount: Int = 0
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    val isActive: Boolean get() = active

    fun start() {
        reset()
        active = true
        startTimeMs = System.currentTimeMillis()
    }

    fun stop(): TripStats {
        active = false
        return getStats()
    }

    fun getStats(): TripStats {
        val durationMs = if (startTimeMs > 0) {
            (System.currentTimeMillis() - startTimeMs).toDouble()
        } else 0.0
        val avgSpeed = if (pointCount > 0) speedSumKmh / pointCount else 0.0
        return TripStats(
            distanceMeters = totalDistanceMeters,
            durationMs = durationMs,
            averageSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeedKmh,
            pointCount = pointCount.toDouble()
        )
    }

    fun reset() {
        active = false
        startTimeMs = 0L
        totalDistanceMeters = 0.0
        maxSpeedKmh = 0.0
        speedSumKmh = 0.0
        pointCount = 0
        lastLat = null
        lastLon = null
    }

    fun feedLocation(data: LocationData) {
        if (!active) return

        val speedKmh = data.speed * 3.6 // m/s → km/h
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        speedSumKmh += speedKmh
        pointCount++

        val prevLat = lastLat
        val prevLon = lastLon
        lastLat = data.latitude
        lastLon = data.longitude

        if (prevLat != null && prevLon != null) {
            totalDistanceMeters += haversine(prevLat, prevLon, data.latitude, data.longitude)
        }
    }

    companion object {
        private const val EARTH_RADIUS_M = 6371000.0

        fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_M * c
        }
    }
}
