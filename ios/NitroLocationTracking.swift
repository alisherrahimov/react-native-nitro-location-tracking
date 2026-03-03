import Foundation
import NitroModules

class NitroLocationTracking: HybridNitroLocationTrackingSpec {

    private let locationEngine = LocationEngine()
    private let connectionManager = ConnectionManager()
    private let dbWriter = NativeDBWriter()
    private let notificationService = NotificationService()

    private var locationCallback: ((LocationData) -> Void)?
    private var motionCallback: ((Bool) -> Void)?
    private var connectionStateCallback: ((ConnectionState) -> Void)?
    private var messageCallback: ((String) -> Void)?

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
    }
}
