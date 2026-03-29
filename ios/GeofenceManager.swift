import CoreLocation

class GeofenceManager: NSObject, CLLocationManagerDelegate {

    private let locationManager = CLLocationManager()
    private var activeRegions = [String: GeofenceRegion]()
    private var callback: ((GeofenceEvent, String) -> Void)?

    override init() {
        super.init()
        locationManager.delegate = self
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
    }

    func removeGeofence(_ regionId: String) {
        for monitored in locationManager.monitoredRegions {
            if monitored.identifier == regionId {
                locationManager.stopMonitoring(for: monitored)
                break
            }
        }
        activeRegions.removeValue(forKey: regionId)
    }

    func removeAllGeofences() {
        for monitored in locationManager.monitoredRegions {
            locationManager.stopMonitoring(for: monitored)
        }
        activeRegions.removeAll()
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        callback?(.enter, region.identifier)
    }

    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        callback?(.exit, region.identifier)
    }

    func locationManager(_ manager: CLLocationManager, monitoringDidFailFor region: CLRegion?,
                         withError error: Error) {
        if let id = region?.identifier {
            print("[GeofenceManager] Monitoring failed for region \(id): \(error.localizedDescription)")
        }
    }

    func distanceTo(regionId: String, from location: CLLocation?) -> Double {
        guard let region = activeRegions[regionId], let location = location else { return -1 }
        let center = CLLocation(latitude: region.latitude, longitude: region.longitude)
        return location.distance(from: center)
    }

    func destroy() {
        removeAllGeofences()
        callback = nil
    }
}
