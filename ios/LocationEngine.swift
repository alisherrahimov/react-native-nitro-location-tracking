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
    /// Native-side consumer of every fix, invoked alongside onLocation but
    /// WITHOUT crossing the JS bridge. Lets app-side native code (e.g. a REST
    /// pusher) keep delivering fixes while the JS thread is suspended
    /// (screen off / backgrounded). See NitroLocationTracking.nativeLocationListener.
    var onLocationNative: ((LocationData) -> Void)?
    var onMotionChange: ((Bool) -> Void)?
    var currentRideId: String?
    var rejectMockLocations: Bool = false
    let speedMonitor = SpeedMonitor()
    let tripCalculator = TripCalculator()

    // Odometer — total meters traveled while tracking, persisted across app
    // kill / reboot via UserDefaults.
    private static let odometerKey = "nitro_location_odometer_meters"
    private(set) var odometerMeters: Double =
        UserDefaults.standard.double(forKey: LocationEngine.odometerKey)
    // Last accepted (post-filter) position used for odometer deltas.
    private var odometerLast: (lat: Double, lng: Double)?

    func resetOdometer() {
        odometerMeters = 0
        odometerLast = nil
        UserDefaults.standard.set(0.0, forKey: LocationEngine.odometerKey)
    }

    // Live Kalman smoother — non-nil only while kalmanFilter is enabled.
    private var kalman: LocationKalmanFilter?

    // Motion state machine — drives onMotionChange and (when enabled) adaptive accuracy.
    private let motionManager = MotionActivityManager()
    private var currentConfig: LocationConfig?
    private var isMovingState = false
    private var stationaryTimer: Timer?
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
        currentConfig = config
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
        // (Re)build the Kalman smoother for this session so stale state from a
        // prior run never produces a spurious first-fix jump.
        if currentConfig?.kalmanFilter == true {
            kalman = LocationKalmanFilter(processNoiseMps: currentConfig?.kalmanProcessNoiseMps ?? 1.0)
        } else {
            kalman = nil
        }
        locationManager.startUpdatingLocation()
        tracking = true
        startMotionEngine()
    }

    private func startMotionEngine() {
        isMovingState = false
        cancelStationaryTimer()
        motionManager.onMotionChange = { [weak self] moving in
            self?.onMotionSignal(moving, fromMotionAPI: true)
        }
        // Returns false when Motion & Fitness is unavailable / denied; in that
        // case didUpdateLocations drives motion from speed (see onMotionSignal).
        motionManager.start()
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
        motionManager.stop()
        cancelStationaryTimer()
        isMovingState = false
        tracking = false
    }

    // MARK: - Motion state machine

    private func onMotionSignal(_ moving: Bool, fromMotionAPI: Bool) {
        // When Core Motion is active it is authoritative; ignore the speed-based
        // fallback so the two sources don't fight.
        if !fromMotionAPI && motionManager.isAvailable { return }

        if moving {
            cancelStationaryTimer()
            if !isMovingState { setMotionState(true) }
        } else {
            // Debounce: only declare stationary after stopTimeout of stillness.
            if isMovingState && stationaryTimer == nil { scheduleStationary() }
        }
    }

    private func scheduleStationary() {
        cancelStationaryTimer()
        let minutes = currentConfig?.stopTimeout ?? 5
        let timeout = max(minutes * 60, 0)
        stationaryTimer = Timer.scheduledTimer(withTimeInterval: timeout, repeats: false) { [weak self] _ in
            guard let self else { return }
            self.stationaryTimer = nil
            if self.isMovingState { self.setMotionState(false) }
        }
    }

    private func cancelStationaryTimer() {
        stationaryTimer?.invalidate()
        stationaryTimer = nil
    }

    private func setMotionState(_ moving: Bool) {
        isMovingState = moving
        onMotionChange?(moving)
        if currentConfig?.adaptiveAccuracy == true { applyAdaptiveAccuracy(moving) }
    }

    private func applyAdaptiveAccuracy(_ moving: Bool) {
        guard let config = currentConfig, tracking else { return }
        if moving {
            switch config.desiredAccuracy {
            case .high: locationManager.desiredAccuracy = kCLLocationAccuracyBest
            case .balanced: locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
            default: locationManager.desiredAccuracy = kCLLocationAccuracyKilometer
            }
            locationManager.distanceFilter = config.distanceFilter > 0 ? config.distanceFilter : kCLDistanceFilterNone
        } else {
            locationManager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
            locationManager.distanceFilter = max(config.distanceFilter, 100)
        }
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

        // Accuracy gate: drop low-confidence fixes before anything downstream sees them.
        let accuracyFilter = currentConfig?.accuracyFilter ?? 0
        if accuracyFilter > 0 && data.accuracy > accuracyFilter {
            return
        }

        // Kalman smoothing: replace lat/lng with the filtered estimate (speed/
        // bearing stay as reported). Everything downstream uses `emitted`.
        let emitted: LocationData
        if let k = kalman {
            let (lat, lng) = k.process(
                latitude: data.latitude, longitude: data.longitude,
                accuracyMeters: data.accuracy, timeMs: data.timestamp
            )
            emitted = LocationData(
                latitude: lat, longitude: lng, altitude: data.altitude,
                speed: data.speed, bearing: data.bearing, accuracy: data.accuracy,
                timestamp: data.timestamp, isMockLocation: isMock
            )
        } else {
            emitted = data
        }

        // Odometer: accumulate straight-line distance between consecutive accepted
        // (post-filter) fixes. Ignore GPS jitter (<0.5m) and implausible jumps (>10km).
        if let last = odometerLast {
            let delta = CLLocation(latitude: last.lat, longitude: last.lng)
                .distance(from: CLLocation(latitude: emitted.latitude, longitude: emitted.longitude))
            if delta >= 0.5 && delta <= 10000 {
                odometerMeters += delta
                UserDefaults.standard.set(odometerMeters, forKey: LocationEngine.odometerKey)
            }
        }
        odometerLast = (emitted.latitude, emitted.longitude)

        // Notify JS via Nitro callback
        onLocation?(emitted)
        // Native consumer — fires even when the JS thread is suspended.
        onLocationNative?(emitted)

        // Feed to speed monitor and trip calculator
        speedMonitor.feedLocation(emitted)
        tripCalculator.feedLocation(emitted)

        // Motion signal from speed — used only when Core Motion isn't the
        // authoritative source (see onMotionSignal).
        onMotionSignal(location.speed >= 0.5, fromMotionAPI: false)
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


