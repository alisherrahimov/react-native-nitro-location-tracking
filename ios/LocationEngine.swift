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
        // If we already have a recent location, return it immediately
        if let lastLocation = locationManager.location {
            let age: TimeInterval = Swift.abs(lastLocation.timestamp.timeIntervalSinceNow)
            if age < 10.0 {
                let data = LocationData(
                    latitude: lastLocation.coordinate.latitude,
                    longitude: lastLocation.coordinate.longitude,
                    altitude: lastLocation.altitude,
                    speed: max(lastLocation.speed, 0),
                    bearing: max(lastLocation.course, 0),
                    accuracy: lastLocation.horizontalAccuracy,
                    timestamp: lastLocation.timestamp.timeIntervalSince1970 * 1000
                )
                completion(data)
                return
            }
        }
        // Otherwise request a fresh location
        pendingLocationCompletion = completion
        locationManager.requestLocation()
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
      

        // Handle pending one-shot request
        if let completion = pendingLocationCompletion {
            completion(data)
            pendingLocationCompletion = nil
        }

        // Save to SQLite immediately (offline-first)
//        dbWriter?.insert(data, rideId: currentRideId)

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

