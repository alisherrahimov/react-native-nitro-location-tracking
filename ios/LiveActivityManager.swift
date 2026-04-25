import Foundation

// Wraps ActivityKit so the rest of the module stays buildable on iOS < 16.2
// and on macOS/tvOS where ActivityKit is unavailable.

#if canImport(ActivityKit)
import ActivityKit

// ─── Activity Attributes ──────────────────────────────────────────────────────

public struct CourierActivityAttributes: ActivityAttributes {
    // Custom activityType so the widget extension (different module) can match this activity.
    public static var activityType: String { "CourierDeliveryActivity" }

    public let orderId: String
    public let customerName: String
    public let deliveryAddress: String
    public let orderCount: Int

    public struct ContentState: Codable, Hashable {
        public let status: String
        public let statusText: String
        public let estimatedMinutes: Int
        public let distanceMeters: Double
    }
}

// ─── Manager ─────────────────────────────────────────────────────────────────

@available(iOS 16.2, *)
final class LiveActivityManager {
    static let shared = LiveActivityManager()

    private init() {
        // Recover any activity that was alive before the app restarted.
        currentActivity = Activity<CourierActivityAttributes>.activities.first
    }

    private var currentActivity: Activity<CourierActivityAttributes>?

    func start(
        orderId: String,
        customerName: String,
        deliveryAddress: String,
        orderCount: Int,
        status: String,
        statusText: String,
        estimatedMinutes: Int,
        distanceMeters: Double
    ) throws {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            let appName = Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String
                ?? Bundle.main.object(forInfoDictionaryKey: "CFBundleName") as? String
                ?? "the app"
            throw NSError(
                domain: "com.nitro.liveactivity",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Live Activities are disabled. Enable them in Settings → \(appName) → Live Activities."]
            )
        }

        if let existing = currentActivity {
            Task { await existing.end(nil, dismissalPolicy: .immediate) }
            currentActivity = nil
        }

        let attributes = CourierActivityAttributes(
            orderId: orderId,
            customerName: customerName,
            deliveryAddress: deliveryAddress,
            orderCount: orderCount
        )
        let content = ActivityContent(
            state: CourierActivityAttributes.ContentState(
                status: status,
                statusText: statusText,
                estimatedMinutes: estimatedMinutes,
                distanceMeters: distanceMeters
            ),
            staleDate: Date().addingTimeInterval(3600)
        )

        currentActivity = try Activity.request(attributes: attributes, content: content, pushType: nil)
    }

    func update(status: String, statusText: String, estimatedMinutes: Int, distanceMeters: Double) {
        // Recover orphaned reference if the app was restarted between start and update.
        if currentActivity == nil {
            currentActivity = Activity<CourierActivityAttributes>.activities.first
        }
        guard let activity = currentActivity else { return }

        let content = ActivityContent(
            state: CourierActivityAttributes.ContentState(
                status: status,
                statusText: statusText,
                estimatedMinutes: estimatedMinutes,
                distanceMeters: distanceMeters
            ),
            staleDate: Date().addingTimeInterval(3600)
        )
        Task { await activity.update(content) }
    }

    func end() {
        // Recover orphaned reference if the app was restarted between start and end.
        if currentActivity == nil {
            currentActivity = Activity<CourierActivityAttributes>.activities.first
        }
        guard let activity = currentActivity else { return }
        currentActivity = nil
        Task { await activity.end(nil, dismissalPolicy: .immediate) }
    }
}

#endif // canImport(ActivityKit)
