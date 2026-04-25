// CourierWidgetLiveActivity.swift
// react-native-nitro-location-tracking
//
// ─── HOW TO USE ──────────────────────────────────────────────────────────────
//
// 1. In Xcode, add a new Widget Extension target to your app:
//    File → New → Target → Widget Extension
//    Uncheck "Include Configuration App Intent"
//
// 2. Copy THIS file into the widget extension folder and add it to the
//    widget extension target's "Compile Sources".
//
// 3. Delete the template files Xcode generated
//    (WidgetName.swift, WidgetNameBundle.swift, Assets.xcassets is fine to keep).
//
// 4. Replace WidgetNameBundle.swift content with:
//
//    import WidgetKit
//    import SwiftUI
//
//    @main
//    struct YourWidgetBundle: WidgetBundle {
//        var body: some Widget {
//            CourierWidgetLiveActivity()
//        }
//    }
//
// 5. In your main app's Info.plist, add:
//    <key>NSSupportsLiveActivities</key>
//    <true/>
//
// 6. In Xcode, select your main app target → General →
//    Frameworks, Libraries, and Embedded Content → verify the
//    widget extension .appex is listed with "Embed Without Signing".
//
// Minimum deployment target: iOS 16.2
// ─────────────────────────────────────────────────────────────────────────────

import ActivityKit
import WidgetKit
import SwiftUI

// MARK: - Attributes
// Must mirror CourierActivityAttributes in LiveActivityManager.swift exactly.
// The activityType string is what links the library's Activity.request() call
// to this widget extension across Swift module boundaries.

struct CourierActivityAttributes: ActivityAttributes {
    static var activityType: String { "CourierDeliveryActivity" }

    let orderId: String
    let customerName: String
    let deliveryAddress: String
    let orderCount: Int

    struct ContentState: Codable, Hashable {
        let status: String       // "picking_up" | "on_the_way" | "arriving" | "delivered"
        let statusText: String   // Localised label for the status
        let estimatedMinutes: Int
        let distanceMeters: Double
    }
}

// MARK: - Helpers

private func statusEmoji(_ status: String) -> String {
    switch status {
    case "picking_up": return "📦"
    case "on_the_way":  return "🚗"
    case "arriving":    return "🏁"
    case "delivered":   return "✅"
    default:            return "📍"
    }
}

private func statusColor(_ status: String) -> Color {
    switch status {
    case "picking_up": return .orange
    case "on_the_way": return .blue
    case "arriving":   return Color(red: 0.1, green: 0.7, blue: 0.3)
    case "delivered":  return .green
    default:           return .gray
    }
}

private func distanceText(_ meters: Double) -> String {
    meters >= 1000
        ? String(format: "%.1f km", meters / 1000)
        : "\(Int(meters)) m"
}

private func safeDeliveryURL(orderId: String) -> URL? {
    var components = URLComponents()
    components.scheme = "nitrolocation"
    components.host = "delivery"
    components.path = "/\(orderId)"
    return components.url
}

// MARK: - Lock Screen / Banner

struct CourierLockScreenView: View {
    let context: ActivityViewContext<CourierActivityAttributes>

    var body: some View {
        VStack(spacing: 8) {
            HStack(alignment: .center) {
                Text(statusEmoji(context.state.status))
                    .font(.title2)
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.state.statusText)
                        .font(.headline)
                    Text(context.attributes.customerName)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    if context.isStale {
                        Text("Updating…")
                            .font(.caption2)
                            .foregroundStyle(.orange)
                    } else {
                        Text("\(context.state.estimatedMinutes) min")
                            .font(.title3)
                            .fontWeight(.bold)
                            .foregroundStyle(statusColor(context.state.status))
                    }
                    Text(distanceText(context.state.distanceMeters))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Divider()

            HStack {
                Image(systemName: "location.fill")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text(context.attributes.deliveryAddress)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Spacer()
                Text(context.attributes.orderId)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .activityBackgroundTint(Color(.systemBackground))
        .activitySystemActionForegroundColor(.primary)
    }
}

// MARK: - Widget

struct CourierWidgetLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CourierActivityAttributes.self) { context in
            CourierLockScreenView(context: context)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 6) {
                        Text(statusEmoji(context.state.status))
                            .font(.title2)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(context.state.statusText)
                                .font(.caption)
                                .fontWeight(.semibold)
                            Text(context.attributes.customerName)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 1) {
                        Text("\(context.state.estimatedMinutes)")
                            .font(.title)
                            .fontWeight(.bold)
                            .foregroundStyle(statusColor(context.state.status))
                        Text("min")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack {
                        Image(systemName: "location.fill")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Text(context.attributes.deliveryAddress)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        Spacer()
                        Text(distanceText(context.state.distanceMeters))
                            .font(.caption)
                            .fontWeight(.semibold)
                    }
                    .padding(.horizontal, 4)
                    .padding(.bottom, 4)
                }
            } compactLeading: {
                Text(statusEmoji(context.state.status))
            } compactTrailing: {
                Text("\(context.state.estimatedMinutes)m")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundStyle(statusColor(context.state.status))
            } minimal: {
                Text("\(context.state.estimatedMinutes)")
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundStyle(statusColor(context.state.status))
            }
            .widgetURL(safeDeliveryURL(orderId: context.attributes.orderId))
            .keylineTint(statusColor(context.state.status))
        }
    }
}
