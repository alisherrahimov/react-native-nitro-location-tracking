//
//  CourierWidgetLiveActivity.swift
//  CourierWidget
//
//  Created by Alisher Raximov on 4/25/26.
//

import ActivityKit
import WidgetKit
import SwiftUI

struct CourierWidgetAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        // Dynamic stateful properties about your activity go here!
        var emoji: String
    }

    // Fixed non-changing properties about your activity go here!
    var name: String
}

struct CourierWidgetLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CourierWidgetAttributes.self) { context in
            // Lock screen/banner UI goes here
            VStack {
                Text("Hello \(context.state.emoji)")
            }
            .activityBackgroundTint(Color.cyan)
            .activitySystemActionForegroundColor(Color.black)

        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded UI goes here.  Compose the expanded UI through
                // various regions, like leading/trailing/center/bottom
                DynamicIslandExpandedRegion(.leading) {
                    Text("Leading")
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("Trailing")
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("Bottom \(context.state.emoji)")
                    // more content
                }
            } compactLeading: {
                Text("L")
            } compactTrailing: {
                Text("T \(context.state.emoji)")
            } minimal: {
                Text(context.state.emoji)
            }
            .widgetURL(URL(string: "http://www.apple.com"))
            .keylineTint(Color.red)
        }
    }
}

extension CourierWidgetAttributes {
    fileprivate static var preview: CourierWidgetAttributes {
        CourierWidgetAttributes(name: "World")
    }
}

extension CourierWidgetAttributes.ContentState {
    fileprivate static var smiley: CourierWidgetAttributes.ContentState {
        CourierWidgetAttributes.ContentState(emoji: "😀")
     }
     
     fileprivate static var starEyes: CourierWidgetAttributes.ContentState {
         CourierWidgetAttributes.ContentState(emoji: "🤩")
     }
}

#Preview("Notification", as: .content, using: CourierWidgetAttributes.preview) {
   CourierWidgetLiveActivity()
} contentStates: {
    CourierWidgetAttributes.ContentState.smiley
    CourierWidgetAttributes.ContentState.starEyes
}
