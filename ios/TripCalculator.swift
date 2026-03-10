import Foundation

class TripCalculator {

    private var active = false
    private var startTime: Date?
    private var totalDistanceMeters: Double = 0.0
    private var maxSpeedKmh: Double = 0.0
    private var speedSumKmh: Double = 0.0
    private var pointCount: Int = 0
    private var lastLat: Double?
    private var lastLon: Double?

    var isActive: Bool { active }

    init() {
        startTime = nil
        lastLat = nil
        lastLon = nil
    }

    func start() {
        reset()
        active = true
        startTime = Date()
    }

    func stop() -> TripStats {
        active = false
        return getStats()
    }

    func getStats() -> TripStats {
        let durationMs: Double
        if let start = startTime {
            durationMs = Date().timeIntervalSince(start) * 1000
        } else {
            durationMs = 0
        }
        let avgSpeed = pointCount > 0 ? speedSumKmh / Double(pointCount) : 0
        return TripStats(
            distanceMeters: totalDistanceMeters,
            durationMs: durationMs,
            averageSpeedKmh: avgSpeed,
            maxSpeedKmh: maxSpeedKmh,
            pointCount: Double(pointCount)
        )
    }

    func reset() {
        active = false
        startTime = nil
        totalDistanceMeters = 0
        maxSpeedKmh = 0
        speedSumKmh = 0
        pointCount = 0
        lastLat = nil
        lastLon = nil
    }

    func feedLocation(_ data: LocationData) {
        guard active else { return }

        let speedKmh = data.speed * 3.6 // m/s → km/h
        if speedKmh > maxSpeedKmh { maxSpeedKmh = speedKmh }
        speedSumKmh += speedKmh
        pointCount += 1

        let prevLat = lastLat
        let prevLon = lastLon
        lastLat = data.latitude
        lastLon = data.longitude

        if let pLat = prevLat, let pLon = prevLon {
            totalDistanceMeters += Self.haversine(lat1: pLat, lon1: pLon,
                                                   lat2: data.latitude, lon2: data.longitude)
        }
    }

    // MARK: - Haversine

    private static let earthRadiusM: Double = 6371000.0

    static func haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) *
                sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }
}
