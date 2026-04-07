import Foundation
import CoreLocation
import NitroModules

class NitroLocationTracking: HybridNitroLocationTrackingSpec {
    private let locationEngine = LocationEngine()
    private let connectionManager = ConnectionManager()
    private let dbWriter = NativeDBWriter()
    private let notificationService = NotificationService()
    private let geofenceManager = GeofenceManager()

    private var locationCallback: ((LocationData) -> Void)?
    private var motionCallback: ((Bool) -> Void)?
    private var connectionStateCallback: ((ConnectionState) -> Void)?
    private var messageCallback: ((String) -> Void)?
    private var geofenceCallback: ((GeofenceEvent, String) -> Void)?
    private var speedAlertCallback: ((SpeedAlertType, Double) -> Void)?
    private var providerStatusCallback: ((LocationProviderStatus, LocationProviderStatus) -> Void)?
    private var permissionStatusCallback: ((PermissionStatus) -> Void)?
    private var permissionPromise: Promise<PermissionStatus>?

    override init() {
        super.init()
        locationEngine.dbWriter = dbWriter
        connectionManager.dbWriter = dbWriter
    }

    // MARK: - Location Engine

    func configure(config: LocationConfig) throws {
        locationEngine.configure(config)
    }

    func startTracking() throws {
        locationEngine.onLocation = { [weak self] data in
            self?.locationCallback?(data)
        }
        locationEngine.onMotionChange = { [weak self] isMoving in
            self?.motionCallback?(isMoving)
        }
        locationEngine.start()
    }

    func stopTracking() throws {
        locationEngine.stop()
    }


    func getCurrentLocation() throws -> Promise<LocationData> {
        let promise = Promise<LocationData>()
        locationEngine.getCurrentLocation { data in
            if let data = data {
                promise.resolve(withResult: data)
            } else {
                promise.reject(withError: NSError(domain: "NitroLocation",
                                                  code: 1,
                                                  userInfo: [NSLocalizedDescriptionKey: "Failed to get location"]))
            }
        }
        return promise
    }

      func isTracking() throws -> Bool {
        return locationEngine.isTracking
    }

    func onLocation(callback: @escaping (LocationData) -> Void) throws {
        locationCallback = callback
    }

    func onMotionChange(callback: @escaping (Bool) -> Void) throws {
        motionCallback = callback
    }

    // MARK: - Connection Manager

    func configureConnection(config: ConnectionConfig) throws {
        connectionManager.configure(config)
    }

    func connectWebSocket() throws {
        connectionManager.onStateChange = { [weak self] (state: ConnectionState) in
            self?.connectionStateCallback?(state)
        }
        connectionManager.onMessage = { [weak self] message in
            self?.messageCallback?(message)
        }
        connectionManager.connect()
    }

    func disconnectWebSocket() throws {
        connectionManager.disconnect()
    }

    func sendMessage(message: String) throws {
        connectionManager.send(message)
    }

    func getConnectionState() throws -> ConnectionState {
        return connectionManager.getState()
    }

    func onConnectionStateChange(callback: @escaping (ConnectionState) -> Void) throws {
        connectionStateCallback = callback
    }

    func onMessage(callback: @escaping (String) -> Void) throws {
        messageCallback = callback
    }

    // MARK: - Sync Control

    func forceSync() throws -> Promise<Bool> {
        let result = connectionManager.flushQueue()
        return Promise.resolved(withResult: result)
    }

    // MARK: - Fake GPS Detection

    func isFakeGpsEnabled() throws -> Bool {
        return locationEngine.isFakeGpsEnabled()
    }

    func setRejectMockLocations(reject: Bool) throws {
        locationEngine.rejectMockLocations = reject
    }

    // MARK: - Geofencing

    func addGeofence(region: GeofenceRegion) throws {
        geofenceManager.addGeofence(region)
    }

    func removeGeofence(regionId: String) throws {
        geofenceManager.removeGeofence(regionId)
    }

    func removeAllGeofences() throws {
        geofenceManager.removeAllGeofences()
    }

    func onGeofenceEvent(callback: @escaping (GeofenceEvent, String) -> Void) throws {
        geofenceCallback = callback
        geofenceManager.setCallback(callback)
    }

    // MARK: - Speed Monitoring

    func configureSpeedMonitor(config: SpeedConfig) throws {
        locationEngine.speedMonitor.configure(config)
    }

    func onSpeedAlert(callback: @escaping (SpeedAlertType, Double) -> Void) throws {
        speedAlertCallback = callback
        locationEngine.speedMonitor.setCallback(callback)
    }

    func getCurrentSpeed() throws -> Double {
        return locationEngine.speedMonitor.getCurrentSpeed()
    }

    // MARK: - Distance Calculator

    func startTripCalculation() throws {
        locationEngine.tripCalculator.start()
    }

    func stopTripCalculation() throws -> TripStats {
        return locationEngine.tripCalculator.stop()
    }

    func getTripStats() throws -> TripStats {
        return locationEngine.tripCalculator.getStats()
    }

    func resetTripCalculation() throws {
        locationEngine.tripCalculator.reset()
    }

    // MARK: - Location Provider Status

    func isLocationServicesEnabled() throws -> Bool {
        return locationEngine.isLocationServicesEnabled()
    }

    func onProviderStatusChange(callback: @escaping (LocationProviderStatus, LocationProviderStatus) -> Void) throws {
        providerStatusCallback = callback
        locationEngine.providerStatusCallback = callback
    }

    // MARK: - Permission Status

    func getLocationPermissionStatus() throws -> PermissionStatus {
        let status: CLAuthorizationStatus
        if #available(iOS 14.0, *) {
            status = CLLocationManager().authorizationStatus
        } else {
            status = CLLocationManager.authorizationStatus()
        }

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

    func requestLocationPermission() throws -> Promise<PermissionStatus> {
        let promise = Promise<PermissionStatus>()
        locationEngine.requestPermission { authStatus in
            let result: PermissionStatus
            switch authStatus {
            case .notDetermined:
                result = .notdetermined
            case .restricted:
                result = .restricted
            case .denied:
                result = .denied
            case .authorizedWhenInUse:
                result = .wheninuse
            case .authorizedAlways:
                result = .always
            @unknown default:
                result = .notdetermined
            }
            promise.resolve(withResult: result)
        }
        return promise
    }

    func onPermissionStatusChange(callback: @escaping (PermissionStatus) -> Void) throws {
        permissionStatusCallback = callback
        locationEngine.permissionStatusCallback = callback
    }

    // MARK: - Distance Utilities

    func getDistanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double) throws -> Double {
        let loc1 = CLLocation(latitude: lat1, longitude: lon1)
        let loc2 = CLLocation(latitude: lat2, longitude: lon2)
        return loc1.distance(from: loc2)
    }

    func getDistanceToGeofence(regionId: String) throws -> Double {
        return geofenceManager.distanceTo(regionId: regionId, from: locationEngine.lastCLLocation)
    }

    // MARK: - Notifications

    func showLocalNotification(title: String, body: String) throws {
        notificationService.showLocalNotification(title: title, body: body)
    }

    func updateForegroundNotification(title: String, body: String) throws {
        notificationService.updateForegroundNotification(title: title, body: body)
    }

    // MARK: - Lifecycle

    func destroy() throws {
        locationEngine.stop()
        connectionManager.disconnect()
        geofenceManager.destroy()
    }
}
