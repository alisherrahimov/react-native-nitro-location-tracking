import CoreLocation

/**
 * Circular geofencing with ENTER / EXIT / DWELL transitions.
 *
 * iOS region monitoring (`CLCircularRegion`) is itself durable — the system
 * keeps monitoring across app termination and relaunches the app to deliver
 * transitions — but the app-side metadata (our `GeofenceRegion`, including the
 * DWELL config) is lost on relaunch, so it is persisted to `UserDefaults` and
 * rebuilt on init.
 *
 * DWELL is not a native iOS transition, so it is synthesised: on ENTER we arm a
 * timer for `loiteringDelayMs`; if the device is still inside when it fires we
 * emit `.dwell`. EXIT cancels the pending timer.
 */
class GeofenceManager: NSObject, CLLocationManagerDelegate {

    private static let defaultLoiteringMs: Double = 300_000 // 5 minutes
    private static let storeKey = "nitro_geofences"

    private let locationManager = CLLocationManager()
    private var activeRegions = [String: GeofenceRegion]()
    private var dwellTimers = [String: Timer]()
    private var callback: ((GeofenceEvent, String) -> Void)?

    override init() {
        super.init()
        locationManager.delegate = self
        restorePersisted()
    }

    func setCallback(_ callback: @escaping (GeofenceEvent, String) -> Void) {
        self.callback = callback
    }

    func addGeofence(_ region: GeofenceRegion) {
        let clRegion = CLCircularRegion(
            center: CLLocationCoordinate2D(latitude: region.latitude, longitude: region.longitude),
            radius: region.radius,
            identifier: region.id
        )
        clRegion.notifyOnEntry = region.notifyOnEntry
        clRegion.notifyOnExit = region.notifyOnExit

        locationManager.startMonitoring(for: clRegion)
        activeRegions[region.id] = region
        persist()
    }

    func removeGeofence(_ regionId: String) {
        for monitored in locationManager.monitoredRegions where monitored.identifier == regionId {
            locationManager.stopMonitoring(for: monitored)
        }
        cancelDwell(regionId)
        activeRegions.removeValue(forKey: regionId)
        persist()
    }

    func removeAllGeofences() {
        for monitored in locationManager.monitoredRegions {
            locationManager.stopMonitoring(for: monitored)
        }
        dwellTimers.values.forEach { $0.invalidate() }
        dwellTimers.removeAll()
        activeRegions.removeAll()
        persist()
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        callback?(.enter, region.identifier)
        // Arm DWELL if configured for this region.
        if let r = activeRegions[region.identifier], r.notifyOnDwell == true {
            armDwell(r)
        }
    }

    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        cancelDwell(region.identifier)
        callback?(.exit, region.identifier)
    }

    func locationManager(_ manager: CLLocationManager, monitoringDidFailFor region: CLRegion?,
                         withError error: Error) {
        if let id = region?.identifier {
            NSLog("[GeofenceManager] monitoring failed for region \(id): \(error.localizedDescription)")
        }
    }

    // MARK: - DWELL (synthesised)

    private func armDwell(_ region: GeofenceRegion) {
        cancelDwell(region.id)
        let delaySec = (region.loiteringDelayMs ?? GeofenceManager.defaultLoiteringMs) / 1000.0
        let timer = Timer.scheduledTimer(withTimeInterval: max(delaySec, 0), repeats: false) { [weak self] _ in
            guard let self else { return }
            self.dwellTimers.removeValue(forKey: region.id)
            // Still monitoring this region → the device is presumed still inside.
            if self.activeRegions[region.id] != nil {
                self.callback?(.dwell, region.id)
            }
        }
        dwellTimers[region.id] = timer
    }

    private func cancelDwell(_ id: String) {
        dwellTimers[id]?.invalidate()
        dwellTimers.removeValue(forKey: id)
    }

    // MARK: - Distance

    func distanceTo(regionId: String, from location: CLLocation?) -> Double {
        guard let region = activeRegions[regionId], let location else { return -1 }
        let center = CLLocation(latitude: region.latitude, longitude: region.longitude)
        return location.distance(from: center)
    }

    func destroy() {
        // Detach the JS callback but leave OS monitoring intact — geofences are
        // durable by design and survive until explicitly removed.
        dwellTimers.values.forEach { $0.invalidate() }
        dwellTimers.removeAll()
        callback = nil
    }

    // MARK: - Persistence

    private func persist() {
        let payload: [[String: Any]] = activeRegions.values.map { r in
            var d: [String: Any] = [
                "id": r.id,
                "latitude": r.latitude,
                "longitude": r.longitude,
                "radius": r.radius,
                "notifyOnEntry": r.notifyOnEntry,
                "notifyOnExit": r.notifyOnExit,
                "notifyOnDwell": r.notifyOnDwell ?? false,
            ]
            if let loiter = r.loiteringDelayMs { d["loiteringDelayMs"] = loiter }
            return d
        }
        UserDefaults.standard.set(payload, forKey: GeofenceManager.storeKey)
    }

    private func restorePersisted() {
        guard let payload = UserDefaults.standard.array(forKey: GeofenceManager.storeKey) as? [[String: Any]] else { return }
        for d in payload {
            guard let id = d["id"] as? String,
                  let lat = d["latitude"] as? Double,
                  let lon = d["longitude"] as? Double,
                  let radius = d["radius"] as? Double else { continue }
            activeRegions[id] = GeofenceRegion(
                id: id,
                latitude: lat,
                longitude: lon,
                radius: radius,
                notifyOnEntry: d["notifyOnEntry"] as? Bool ?? true,
                notifyOnExit: d["notifyOnExit"] as? Bool ?? true,
                notifyOnDwell: d["notifyOnDwell"] as? Bool ?? false,
                loiteringDelayMs: d["loiteringDelayMs"] as? Double
            )
        }
    }
}
