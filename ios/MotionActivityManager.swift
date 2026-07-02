import CoreMotion

/**
 * Motion-state source backed by Core Motion's activity classifier.
 *
 * Emits `onMotionChange(true)` when the device reports a locomotion activity
 * (walking / running / cycling / automotive) and `onMotionChange(false)` when it
 * reports `stationary`. Requires the "Motion & Fitness" permission
 * (NSMotionUsageDescription in the host app's Info.plist). When motion activity
 * is unavailable or the permission is denied, `start()` returns false and callers
 * should fall back to speed-based motion detection — `isAvailable` reflects this.
 */
final class MotionActivityManager {

    var onMotionChange: ((Bool) -> Void)?

    /// True only while Core Motion is actively feeding this manager.
    private(set) var isAvailable = false

    private let manager = CMMotionActivityManager()

    @discardableResult
    func start() -> Bool {
        guard CMMotionActivityManager.isActivityAvailable() else {
            isAvailable = false
            return false
        }
        let status = CMMotionActivityManager.authorizationStatus()
        if status == .denied || status == .restricted {
            isAvailable = false
            return false
        }

        manager.startActivityUpdates(to: .main) { [weak self] activity in
            guard let self, let activity else { return }
            // Only act on confidently-classified samples to avoid flapping.
            guard activity.confidence != .low else { return }

            if activity.stationary {
                self.onMotionChange?(false)
            } else if activity.walking || activity.running || activity.automotive || activity.cycling {
                self.onMotionChange?(true)
            }
            // `unknown` with no locomotion flag → leave state unchanged.
        }
        isAvailable = true
        return true
    }

    func stop() {
        manager.stopActivityUpdates()
        isAvailable = false
    }
}
