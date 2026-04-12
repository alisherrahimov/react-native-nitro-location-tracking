import CoreLocation

class LocationEngine: NSObject, CLLocationManagerDelegate {
    /// Continuous tracking manager — only used for startUpdatingLocation / stopUpdatingLocation
    private let locationManager = CLLocationManager()
    /// Separate one-shot manager so requestLocation() never kills startUpdatingLocation()
    private lazy var oneShotManager: CLLocationManager = {
        let mgr = CLLocationManager()
        mgr.delegate = self
        mgr.desiredAccuracy = kCLLocationAccuracyBest
        return mgr
    }()
    private var tracking = false
    private var pendingPermissionCompletion: ((CLAuthorizationStatus) -> Void)?
    private var pendingStartAfterPermission = false

    var onLocation: ((LocationData) -> Void)?
    var onMotionChange: ((Bool) -> Void)?
    var dbWriter: NativeDBWriter?
    var currentRideId: String?
    var rejectMockLocations: Bool = false
    let speedMonitor = SpeedMonitor()
    let tripCalculator = TripCalculator()
    var providerStatusCallback: ((LocationProviderStatus, LocationProviderStatus) -> Void)? {
        didSet {
            // Emit current status immediately when the callback is first set
            if providerStatusCallback != nil {
                let enabled = CLLocationManager.locationServicesEnabled()
                let status: LocationProviderStatus = enabled ? .enabled : .disabled
                lastProviderEnabled = enabled
                providerStatusCallback?(status, status)
            }
        }
    }
    var permissionStatusCallback: ((PermissionStatus) -> Void)? {
        didSet {
            // Emit current status immediately when the callback is first set
            if permissionStatusCallback != nil {
                let current = Self.mapAuthStatus(CLLocationManager.authorizationStatus())
                lastPermissionStatus = current
                permissionStatusCallback?(current)
            }
        }
    }

    // Deduplication: track last-notified values
    private var lastPermissionStatus: PermissionStatus?
    private var lastProviderEnabled: Bool?

    /// The most recently received location from Core Location (for distance calculations)
    var lastCLLocation: CLLocation? {
        return locationManager.location
    }

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
            pendingStartAfterPermission = true
            locationManager.requestAlwaysAuthorization()
            return
        }
        locationManager.startUpdatingLocation()
        tracking = true
    }

    func requestPermission(completion: @escaping (CLAuthorizationStatus) -> Void) {
        let currentStatus = CLLocationManager.authorizationStatus()
        if currentStatus != .notDetermined {
            completion(currentStatus)
            return
        }
        pendingPermissionCompletion = completion
        locationManager.requestAlwaysAuthorization()
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
                    timestamp: lastLocation.timestamp.timeIntervalSince1970 * 1000,
                    isMockLocation: Self.isLocationMock(lastLocation)
                )
                completion(data)
                return
            }
        }
        // Use a SEPARATE manager for one-shot requests so we never
        // stop the continuous startUpdatingLocation() on locationManager.
        pendingLocationCompletions.append(completion)
        oneShotManager.requestLocation()
    }

    private var pendingLocationCompletions: [(LocationData?) -> Void] = []

    func locationManager(_ manager: CLLocationManager,
                         didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }

        let isMock = Self.isLocationMock(location)

        let data = LocationData(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            altitude: location.altitude,
            speed: max(location.speed, 0),
            bearing: max(location.course, 0),
            accuracy: location.horizontalAccuracy,
            timestamp: location.timestamp.timeIntervalSince1970 * 1000,
            isMockLocation: isMock
        )

        // Handle pending one-shot request (from oneShotManager)
        if manager === oneShotManager {
            let completions = pendingLocationCompletions
            pendingLocationCompletions.removeAll()
            for completion in completions {
                completion(data)
            }
            return
        }

        // Continuous updates from locationManager only
        guard manager === locationManager else { return }

        // Skip mock locations if rejection is enabled
        if rejectMockLocations && isMock {
            return
        }

        // Notify JS via Nitro callback
        onLocation?(data)

        // Feed to speed monitor and trip calculator
        speedMonitor.feedLocation(data)
        tripCalculator.feedLocation(data)

        // Motion detection
        onMotionChange?(location.speed >= 0.5)
    }

    func locationManager(_ manager: CLLocationManager,
                         didFailWithError error: Error) {
        if manager === oneShotManager {
            let completions = pendingLocationCompletions
            pendingLocationCompletions.removeAll()
            for completion in completions {
                completion(nil)
            }
        }
    }

    // MARK: - Mock Location Detection

    private static func isLocationMock(_ location: CLLocation) -> Bool {
        if #available(iOS 15.0, *) {
            if let sourceInfo = location.sourceInformation {
                return sourceInfo.isSimulatedBySoftware
            }
        }
        // Fallback: detect simulator at compile time
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }

    func isFakeGpsEnabled() -> Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }

    // MARK: - Location Provider Status

    func isLocationServicesEnabled() -> Bool {
        return CLLocationManager.locationServicesEnabled()
    }

    // MARK: - Authorization / Provider change delegate

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager === locationManager else { return }

        let authStatus = CLLocationManager.authorizationStatus()

        // Resolve pending permission request
        if let completion = pendingPermissionCompletion {
            pendingPermissionCompletion = nil
            completion(authStatus)
        }

        // Auto-start tracking if permission was granted after start() was called
        if pendingStartAfterPermission {
            pendingStartAfterPermission = false
            if authStatus == .authorizedAlways || authStatus == .authorizedWhenInUse {
                locationManager.startUpdatingLocation()
                tracking = true
            }
        }

        // Auto-stop if permission was revoked while tracking. Without this the
        // engine stays flagged as tracking but Core Location silently delivers
        // nothing, leaving the app in an inconsistent state.
        if authStatus == .denied || authStatus == .restricted {
            if tracking {
                locationManager.stopUpdatingLocation()
                tracking = false
                pendingStartAfterPermission = false
            }
        }

        // Notify JS about permission status change (deduplicated)
        let permStatus = Self.mapAuthStatus(authStatus)
        if permStatus != lastPermissionStatus {
            lastPermissionStatus = permStatus
            permissionStatusCallback?(permStatus)
        }

        // Notify JS about provider status change (deduplicated)
        let enabled = CLLocationManager.locationServicesEnabled()
        if enabled != lastProviderEnabled {
            lastProviderEnabled = enabled
            let status: LocationProviderStatus = enabled ? .enabled : .disabled
            providerStatusCallback?(status, status)
        }
    }

    // MARK: - Helpers

    private static func mapAuthStatus(_ status: CLAuthorizationStatus) -> PermissionStatus {
        switch status {
        case .notDetermined:
            return .notdetermined
        case .restricted:
            return .restricted
        case .denied:
            return .denied
        case .authorizedWhenInUse:
            return .wheninuse
        case .authorizedAlways:
            return .always
        @unknown default:
            return .notdetermined
        }
    }
}


