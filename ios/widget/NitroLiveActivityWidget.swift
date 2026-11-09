// NitroLiveActivityWidget.swift
// react-native-nitro-location-tracking
//
// Fully dynamic Live Activity UI. Every text, icon, color, countdown and the
// tap deep link come from JS via `startLiveActivityWithContent` /
// `updateLiveActivityWithContent` — nothing here is hard-coded per app.
//
// ─── HOW TO USE ──────────────────────────────────────────────────────────────
//
// 1. Xcode → File → New → Target → Widget Extension
//    (uncheck "Include Configuration App Intent"), deployment target 16.2.
//
// 2. Add BOTH files to the widget extension target:
//    - ios/shared/NitroLiveActivityAttributes.swift  → add BY REFERENCE
//      (do NOT tick "Copy items if needed") so it stays in sync with the lib.
//    - ios/widget/NitroLiveActivityWidget.swift      → reference or copy
//      (copy it if you want to customise the layout).
//
// 3. Replace the generated bundle file with:
//
//    import WidgetKit
//    import SwiftUI
//
//    @main
//    struct YourWidgetBundle: WidgetBundle {
//        var body: some Widget {
//            NitroLiveActivityWidget()
//        }
//    }
//
// 4. Main app Info.plist: <key>NSSupportsLiveActivities</key><true/>
//
// 5. Icons with type "asset" are looked up in the WIDGET extension's
//    Assets.xcassets (the widget cannot read the app's asset catalog).
//
// Minimum deployment target: iOS 16.2
// ─────────────────────────────────────────────────────────────────────────────

import ActivityKit
import WidgetKit
import SwiftUI

private typealias State = NitroLiveActivityAttributes.ContentState

// MARK: - Helpers

private extension Color {
    /// "#RRGGBB" / "#RRGGBBAA" / "RRGGBB". Returns nil for malformed input.
    init?(nitroHex: String?) {
        guard var hex = nitroHex?.trimmingCharacters(in: .whitespacesAndNewlines), !hex.isEmpty else { return nil }
        if hex.hasPrefix("#") { hex.removeFirst() }
        guard hex.count == 6 || hex.count == 8, let v = UInt64(hex, radix: 16) else { return nil }
        let r, g, b, a: Double
        if hex.count == 6 {
            r = Double((v >> 16) & 0xFF) / 255
            g = Double((v >> 8) & 0xFF) / 255
            b = Double(v & 0xFF) / 255
            a = 1
        } else {
            r = Double((v >> 24) & 0xFF) / 255
            g = Double((v >> 16) & 0xFF) / 255
            b = Double((v >> 8) & 0xFF) / 255
            a = Double(v & 0xFF) / 255
        }
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

private extension State {
    var accent: Color { Color(nitroHex: accentColor) ?? .accentColor }
    var primaryText: Color { Color(nitroHex: textColor) ?? .primary }
    var secondaryText: Color { Color(nitroHex: secondaryTextColor) ?? .secondary }
    var timerEnd: Date? { timerEndEpochMs.map { Date(timeIntervalSince1970: $0 / 1000) } }
    var url: URL? { deepLinkUrl.flatMap { URL(string: $0) } }
    var hasFooter: Bool { footerText != nil || footerTrailingText != nil }
}

private func timerRange(_ end: Date) -> ClosedRange<Date> {
    let now = Date()
    return min(now, end)...end
}

// MARK: - Building blocks

private struct IconView: View {
    let icon: NitroLiveActivityAttributes.Icon
    var fallbackColor: Color = .primary

    var body: some View {
        let tint = Color(nitroHex: icon.color) ?? fallbackColor
        switch icon.type {
        case "symbol":
            Image(systemName: icon.name).foregroundStyle(tint)
        case "asset":
            Image(icon.name).resizable().scaledToFit()
        default: // "emoji" and unknown kinds
            Text(icon.name)
        }
    }
}

/// Compact / minimal island region. Priority: timer → text → icon.
private struct SlotView: View {
    let slot: NitroLiveActivityAttributes.Slot
    let fallbackColor: Color

    var body: some View {
        let color = Color(nitroHex: slot.color) ?? fallbackColor
        if let ms = slot.timerEndEpochMs {
            Text(timerInterval: timerRange(Date(timeIntervalSince1970: ms / 1000)), countsDown: true)
                .monospacedDigit()
                .font(.caption2.weight(.bold))
                .foregroundStyle(color)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 48)
        } else if let text = slot.text {
            Text(text)
                .font(.caption2.weight(.bold))
                .foregroundStyle(color)
                .lineLimit(1)
        } else if let icon = slot.icon {
            IconView(icon: icon, fallbackColor: color)
                .frame(maxWidth: 20, maxHeight: 20)
        }
    }
}

private struct ValueBlock: View {
    let state: State
    let isStale: Bool
    var valueFont: Font = .title3

    var body: some View {
        VStack(alignment: .trailing, spacing: 1) {
            if isStale, let stale = state.staleText {
                Text(stale)
                    .font(.caption2)
                    .foregroundStyle(state.accent)
            } else if let end = state.timerEnd {
                Text(timerInterval: timerRange(end), countsDown: true)
                    .monospacedDigit()
                    .font(valueFont.weight(.bold))
                    .foregroundStyle(state.accent)
                    .multilineTextAlignment(.trailing)
            } else if let value = state.value {
                HStack(alignment: .firstTextBaseline, spacing: 3) {
                    Text(value)
                        .font(valueFont.weight(.bold))
                        .foregroundStyle(state.accent)
                    if let unit = state.valueUnit {
                        Text(unit)
                            .font(.caption)
                            .foregroundStyle(state.secondaryText)
                    }
                }
            }
            if let secondary = state.secondaryValue {
                Text(secondary)
                    .font(.caption)
                    .foregroundStyle(state.secondaryText)
            }
        }
    }
}

private struct TitleBlock: View {
    let state: State
    var titleFont: Font = .headline
    var subtitleFont: Font = .subheadline

    var body: some View {
        HStack(spacing: 8) {
            if let icon = state.icon {
                IconView(icon: icon, fallbackColor: state.accent)
                    .font(.title2)
                    .frame(maxWidth: 32, maxHeight: 32)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(state.title)
                    .font(titleFont)
                    .foregroundStyle(state.primaryText)
                    .lineLimit(1)
                if let subtitle = state.subtitle {
                    Text(subtitle)
                        .font(subtitleFont)
                        .foregroundStyle(state.secondaryText)
                        .lineLimit(1)
                }
            }
        }
    }
}

private struct FooterRow: View {
    let state: State

    var body: some View {
        HStack(spacing: 4) {
            if let icon = state.footerIcon {
                IconView(icon: icon, fallbackColor: state.secondaryText)
                    .font(.caption2)
                    .frame(maxWidth: 14, maxHeight: 14)
            }
            if let footer = state.footerText {
                Text(footer)
                    .font(.caption)
                    .foregroundStyle(state.secondaryText)
                    .lineLimit(1)
            }
            Spacer(minLength: 4)
            if let trailing = state.footerTrailingText {
                Text(trailing)
                    .font(.caption2)
                    .foregroundStyle(state.secondaryText)
            }
        }
    }
}

// MARK: - Lock Screen / Banner

struct NitroLiveActivityLockScreenView: View {
    let context: ActivityViewContext<NitroLiveActivityAttributes>

    var body: some View {
        let state = context.state
        VStack(spacing: 8) {
            HStack(alignment: .center) {
                TitleBlock(state: state)
                Spacer(minLength: 8)
                ValueBlock(state: state, isStale: context.isStale)
            }
            if let progress = state.progress {
                ProgressView(value: progress).tint(state.accent)
            }
            if state.hasFooter {
                Divider()
                FooterRow(state: state)
            }
        }
        .padding()
        .activityBackgroundTint(Color(nitroHex: state.backgroundColor))
        .activitySystemActionForegroundColor(state.primaryText)
    }
}

// MARK: - Widget

struct NitroLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: NitroLiveActivityAttributes.self) { context in
            NitroLiveActivityLockScreenView(context: context)
                .widgetURL(context.state.url)
        } dynamicIsland: { context in
            let state = context.state
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    TitleBlock(state: state, titleFont: .caption.weight(.semibold), subtitleFont: .caption2)
                        .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ValueBlock(state: state, isStale: context.isStale, valueFont: .title)
                        .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 6) {
                        if let progress = state.progress {
                            ProgressView(value: progress).tint(state.accent)
                        }
                        if state.hasFooter {
                            FooterRow(state: state)
                        }
                    }
                    .padding(.horizontal, 4)
                    .padding(.bottom, 4)
                }
            } compactLeading: {
                SlotView(
                    slot: state.compactLeading
                        ?? .init(text: nil, icon: state.icon, timerEndEpochMs: nil, color: nil),
                    fallbackColor: state.accent
                )
            } compactTrailing: {
                SlotView(
                    slot: state.compactTrailing
                        ?? .init(
                            text: state.value.map { v in state.valueUnit.map { "\(v) \($0)" } ?? v },
                            icon: nil,
                            timerEndEpochMs: state.timerEndEpochMs,
                            color: nil
                        ),
                    fallbackColor: state.accent
                )
            } minimal: {
                SlotView(
                    slot: state.minimal
                        ?? .init(text: state.value, icon: state.icon, timerEndEpochMs: state.timerEndEpochMs, color: nil),
                    fallbackColor: state.accent
                )
            }
            .widgetURL(state.url)
            .keylineTint(state.accent)
        }
    }
}
