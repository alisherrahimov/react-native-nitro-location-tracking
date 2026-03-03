import CoreLocation

class LocationEngine: NSObject, CLLocationManagerDelegate {
    private let locationManager = CLLocationManager()
    private var tracking = false

    var onLocation: ((LocationData) -> Void)?
    var onMotionChange: ((Bool) -> Void)?
    var dbWriter: NativeDBWriter?
    var currentRideId: String?

    override init() {
        super.init()
        locationManager.delegate = self
    }

    func configure(_ config: LocationConfig) {
        switch config.desiredAccuracy {
        case .high:
            locationManager.desiredAccuracy = kCLLocationAccuracyBest
        case .balanced:
            locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        default:
            locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
        }

        locationManager.distanceFilter = config.distanceFilter
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.showsBackgroundLocationIndicator = true
        locationManager.pausesLocationUpdatesAutomatically = false
    }

    func start() {
        guard CLLocationManager.authorizationStatus() == .authorizedAlways ||
              CLLocationManager.authorizationStatus() == .authorizedWhenInUse else {
            locationManager.requestAlwaysAuthorization()
            return
        }
        locationManager.startUpdatingLocation()
        tracking = true
    }

    func stop() {
        locationManager.stopUpdatingLocation()
        tracking = false
    }

    var isTracking: Bool { tracking }

    func getCurrentLocation(completion: @escaping (LocationData?) -> Void) {
        locationManager.requestLocation()
        // Store completion for delegate callback
        pendingLocationCompletion = completion
    }

    private var pendingLocationCompletion: ((LocationData?) -> Void)?

    func locationManager(_ manager: CLLocationManager,
                         didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }

        let data = LocationData(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            altitude: location.altitude,
            speed: max(location.speed, 0),
            bearing: max(location.course, 0),
            accuracy: location.horizontalAccuracy,
            timestamp: location.timestamp.timeIntervalSince1970 * 1000
        )
      
      
        print(data,"dataaas")

        // Handle pending one-shot request
        if let completion = pendingLocationCompletion {
            completion(data)
            pendingLocationCompletion = nil
        }

        // Save to SQLite immediately (offline-first)
        dbWriter?.insert(data, rideId: currentRideId)

        // Notify JS via Nitro callback
        onLocation?(data)

        // Motion detection
        onMotionChange?(location.speed >= 0.5)
    }

    func locationManager(_ manager: CLLocationManager,
                         didFailWithError error: Error) {
        if let completion = pendingLocationCompletion {
            completion(nil)
            pendingLocationCompletion = nil
        }
    }
}

//// Simple struct to pass location data between components
//struct LocationData {
//    let latitude: Double
//    let longitude: Double
//    let altitude: Double
//    let speed: Double
//    let bearing: Double
//    let accuracy: Double
//    let timestamp: Double
//
//    var id: String = ""
//
//    func toJSON() -> [String: Any] {
//        return [
//            "latitude": latitude,
//            "longitude": longitude,
//            "altitude": altitude,
//            "speed": speed,
//            "bearing": bearing,
//            "accuracy": accuracy,
//            "timestamp": timestamp
//        ]
//    }
//}
//
//// Config structs
//struct LocationConfig {
//    let desiredAccuracy: String
//    let distanceFilter: Double
//    let intervalMs: Double
//    let fastestIntervalMs: Double
//    let stopTimeout: Double
//    let stopOnTerminate: Bool
//    let startOnBoot: Bool
//    let foregroundNotificationTitle: String
//    let foregroundNotificationText: String
//}
//
//struct ConnectionConfig {
//    let wsUrl: String
//    let restUrl: String
//    let authToken: String
//    let reconnectIntervalMs: Double
//    let maxReconnectAttempts: Double
//    let batchSize: Double
//    let syncIntervalMs: Double
//}
