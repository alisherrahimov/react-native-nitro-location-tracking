import Foundation
import CoreLocation

/// Periodically checks whether the device is using simulated/mock locations.
/// Works independently of location tracking — fires the callback when the state changes.
///
/// Detection methods:
/// - iOS 15+: Uses CLLocation.sourceInformation.isSimulatedBySoftware on a one-shot location request
/// - Simulator: Compile-time detection via #targetEnvironment(simulator)
/// - Jailbreak indicators: Checks for known mock location tool files
class MockLocationMonitor: NSObject, CLLocationManagerDelegate {

    private let locationManager = CLLocationManager()
    private var callback: ((Bool) -> Void)?
    private var lastState: Bool?
    private var pollTimer: Timer?
    private let pollInterval: TimeInterval = 3.0

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func setCallback(_ callback: @escaping (Bool) -> Void) {
        self.callback = callback

        // Emit current state immediately
        let current = checkMockLocationSync()
        lastState = current
        callback(current)

        // Start periodic polling
        startPolling()
    }

    private func startPolling() {
        stopPolling()
        pollTimer = Timer.scheduledTimer(withTimeInterval: pollInterval, repeats: true) { [weak self] _ in
            self?.performCheck()
        }
    }

    private func stopPolling() {
        pollTimer?.invalidate()
        pollTimer = nil
    }

    private func performCheck() {
        // First check compile-time and file-system indicators
        let syncResult = checkMockLocationSync()
        if syncResult != lastState {
            lastState = syncResult
            callback?(syncResult)
            return
        }

        // On iOS 15+, also do a one-shot location check for runtime detection
        if #available(iOS 15.0, *) {
            let status = CLLocationManager.authorizationStatus()
            if status == .authorizedAlways || status == .authorizedWhenInUse {
                locationManager.requestLocation()
            }
        }
    }

    // MARK: - Synchronous checks (no location needed)

    private func checkMockLocationSync() -> Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        // Check for common jailbreak/mock location indicators
        let suspiciousPaths = [
            "/Applications/Cydia.app",
            "/Library/MobileSubstrate/MobileSubstrate.dylib",
            "/usr/sbin/sshd",
            "/etc/apt",
            "/private/var/lib/apt/",
            "/Applications/LocationFaker.app",
            "/Applications/LocationHandle.app",
            "/Applications/LocationChanger.app"
        ]

        for path in suspiciousPaths {
            if FileManager.default.fileExists(atPath: path) {
                return true
            }
        }

        return false
        #endif
    }

    // MARK: - CLLocationManagerDelegate (for iOS 15+ runtime detection)

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }

        if #available(iOS 15.0, *) {
            if let sourceInfo = location.sourceInformation {
                let isMock = sourceInfo.isSimulatedBySoftware
                if isMock != lastState {
                    lastState = isMock
                    callback?(isMock)
                }
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Silently ignore — this is a best-effort check
    }

    func destroy() {
        stopPolling()
        callback = nil
        lastState = nil
    }
}
