import Foundation

// Drives the fully dynamic Live Activity (NitroLiveActivityAttributes).
// Everything the widget shows lives in ContentState, so JS can change any
// text / icon / color / deep link on every update without restarting the
// activity.

#if canImport(ActivityKit)
import ActivityKit

@available(iOS 16.2, *)
final class NitroLiveActivityManager {
    static let shared = NitroLiveActivityManager()
    private init() {}

    private static let defaultStaleAfterSeconds: Double = 3600

    var areEnabled: Bool {
        ActivityAuthorizationInfo().areActivitiesEnabled
    }

    var isRunning: Bool {
        Activity<NitroLiveActivityAttributes>.activities.contains {
            $0.activityState == .active || $0.activityState == .stale
        }
    }

    func start(_ content: LiveActivityContent) throws {
        guard areEnabled else {
            let appName = Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String
                ?? Bundle.main.object(forInfoDictionaryKey: "CFBundleName") as? String
                ?? "the app"
            throw NSError(
                domain: "com.nitro.liveactivity",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Live Activities are disabled. Enable them in Settings → \(appName) → Live Activities."]
            )
        }

        // Single-activity semantics: end every previous activity (including
        // orphans left by a crash or a double start) before requesting.
        let existing = Activity<NitroLiveActivityAttributes>.activities
        for activity in existing {
            Task { await activity.end(nil, dismissalPolicy: .immediate) }
        }

        let attributes = NitroLiveActivityAttributes(activityId: UUID().uuidString)
        _ = try Activity.request(
            attributes: attributes,
            content: Self.activityContent(content),
            pushType: nil
        )
    }

    func update(_ content: LiveActivityContent) {
        let activityContent = Self.activityContent(content)
        for activity in Activity<NitroLiveActivityAttributes>.activities {
            Task { await activity.update(activityContent) }
        }
    }

    func end(finalContent: LiveActivityContent?, dismissAfterSeconds: Double?) {
        let final = finalContent.map { Self.activityContent($0) }
        let delay = max(0, dismissAfterSeconds ?? 0)
        let policy: ActivityUIDismissalPolicy = (final != nil && delay > 0)
            ? .after(Date().addingTimeInterval(delay))
            : .immediate
        for activity in Activity<NitroLiveActivityAttributes>.activities {
            Task { await activity.end(final, dismissalPolicy: policy) }
        }
    }

    // MARK: - Mapping (Nitro struct → Codable ContentState)

    private static func activityContent(_ c: LiveActivityContent) -> ActivityContent<NitroLiveActivityAttributes.ContentState> {
        let staleAfter = c.staleAfterSeconds ?? defaultStaleAfterSeconds
        return ActivityContent(
            state: state(c),
            staleDate: staleAfter > 0 ? Date().addingTimeInterval(staleAfter) : nil,
            relevanceScore: c.relevanceScore ?? 0
        )
    }

    private static func state(_ c: LiveActivityContent) -> NitroLiveActivityAttributes.ContentState {
        NitroLiveActivityAttributes.ContentState(
            title: c.title,
            subtitle: c.subtitle,
            icon: icon(c.icon),
            value: c.value,
            valueUnit: c.valueUnit,
            timerEndEpochMs: c.timerEndEpochMs,
            secondaryValue: c.secondaryValue,
            footerText: c.footerText,
            footerIcon: icon(c.footerIcon),
            footerTrailingText: c.footerTrailingText,
            progress: c.progress.map { min(max($0, 0), 1) },
            accentColor: c.accentColor,
            backgroundColor: c.backgroundColor,
            textColor: c.textColor,
            secondaryTextColor: c.secondaryTextColor,
            compactLeading: slot(c.compactLeading),
            compactTrailing: slot(c.compactTrailing),
            minimal: slot(c.minimal),
            staleText: c.staleText,
            deepLinkUrl: c.deepLinkUrl
        )
    }

    private static func icon(_ i: LiveActivityIcon?) -> NitroLiveActivityAttributes.Icon? {
        guard let i else { return nil }
        return NitroLiveActivityAttributes.Icon(type: i.type.stringValue, name: i.name, color: i.color)
    }

    private static func slot(_ s: LiveActivitySlot?) -> NitroLiveActivityAttributes.Slot? {
        guard let s else { return nil }
        return NitroLiveActivityAttributes.Slot(
            text: s.text,
            icon: icon(s.icon),
            timerEndEpochMs: s.timerEndEpochMs,
            color: s.color
        )
    }
}

#endif // canImport(ActivityKit)
