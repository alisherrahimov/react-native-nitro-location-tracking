// NitroLiveActivityAttributes.swift
// react-native-nitro-location-tracking
//
// Shared between the library pod AND your Widget Extension target.
//
// Add this file to the widget target BY REFERENCE (Xcode → Add Files → do NOT
// tick "Copy items if needed") from:
//   node_modules/react-native-nitro-location-tracking/ios/shared/
// so the library and the widget always compile the exact same struct and can
// never drift apart after a library upgrade.
//
// Every ContentState field except `title` is optional, so an older widget can
// still decode content produced by a newer library (unknown keys are ignored,
// missing keys decode as nil).

#if canImport(ActivityKit)
import ActivityKit
import Foundation

struct NitroLiveActivityAttributes: ActivityAttributes {
    /// Random id generated per `startLiveActivityWithContent` call.
    let activityId: String

    struct Icon: Codable, Hashable {
        /// "symbol" | "emoji" | "asset" (kept as String so new kinds never break decoding).
        var type: String
        var name: String
        var color: String?
    }

    struct Slot: Codable, Hashable {
        var text: String?
        var icon: Icon?
        var timerEndEpochMs: Double?
        var color: String?
    }

    struct ContentState: Codable, Hashable {
        var title: String
        var subtitle: String?
        var icon: Icon?

        var value: String?
        var valueUnit: String?
        var timerEndEpochMs: Double?
        var secondaryValue: String?

        var footerText: String?
        var footerIcon: Icon?
        var footerTrailingText: String?

        var progress: Double?

        var accentColor: String?
        var backgroundColor: String?
        var textColor: String?
        var secondaryTextColor: String?

        var compactLeading: Slot?
        var compactTrailing: Slot?
        var minimal: Slot?

        var staleText: String?
        var deepLinkUrl: String?
    }
}

#endif // canImport(ActivityKit)
