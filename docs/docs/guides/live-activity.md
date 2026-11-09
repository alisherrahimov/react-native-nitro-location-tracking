---
sidebar_position: 15
---

# Live Activity (iOS Dynamic Island & Lock Screen · Android ongoing card)

Show a live card that updates in real time. **Everything on it is controlled
from JS**: title, subtitle, icons (SF Symbols, emoji, or widget assets), the big
value, a native countdown, a progress bar, the footer, colors, the compact
Dynamic Island regions, and the tap deep link. Each field can change on every
update. The widget has no hard-coded text, so all localization happens in JS.

- **iOS 16.2+**: Lock Screen and Dynamic Island.
- **Android**: an ongoing notification built from the same content (needs
  `POST_NOTIFICATIONS`).

## iOS setup (one-time)

1. **Create a Widget Extension**: Xcode → `File → New → Target → Widget
   Extension`. Uncheck "Include Configuration App Intent" and set the deployment
   target to **16.2**.

2. **Add two files to the widget target:**

   | File | How to add |
   | --- | --- |
   | `node_modules/react-native-nitro-location-tracking/ios/shared/NitroLiveActivityAttributes.swift` | **By reference.** Leave "Copy items if needed" **unticked**, so the widget and the library always compile the same struct and can't drift apart after an upgrade. |
   | `node_modules/react-native-nitro-location-tracking/ios/widget/NitroLiveActivityWidget.swift` | By reference, or copy it if you want to change the layout. |

3. **Replace the generated bundle file:**

   ```swift
   import WidgetKit
   import SwiftUI

   @main
   struct YourWidgetBundle: WidgetBundle {
       var body: some Widget {
           NitroLiveActivityWidget()
       }
   }
   ```

   Delete the other generated templates.

4. **In the main app's `Info.plist`:**

   ```xml
   <key>NSSupportsLiveActivities</key>
   <true/>
   ```

5. **Deep links:** register your URL scheme on the app and pass the full URL in
   `deepLinkUrl`.

6. **Asset icons** (`type: 'asset'`) are loaded from the **widget extension's**
   `Assets.xcassets`, because the widget can't read the app's asset catalog.

:::warning
Live Activities run in the iOS Simulator (pick an iPhone 14 Pro or newer for
the Dynamic Island), but check the final look and update behaviour on a real
device with iOS 16.2+.
:::

## Usage

```tsx
import { LiveActivity } from 'react-native-nitro-location-tracking';
import type { LiveActivityContent } from 'react-native-nitro-location-tracking';

function contentFor(order: Order, t: TFunction): LiveActivityContent {
  return {
    title: t('liveActivity.onTheWay'),
    subtitle: `#${order.number}`,
    icon: { type: 'symbol', name: 'car.fill', color: '#1E88E5' },

    // Native countdown: keeps ticking while JS is suspended or the app is killed.
    timerEndEpochMs: order.deadline.getTime(),
    secondaryValue: formatDistance(order.distanceMeters), // "2.4 km" / "2,4 км"

    footerText: order.street,
    footerIcon: { type: 'symbol', name: 'mappin.and.ellipse' },
    footerTrailingText: t('liveActivity.orders', { count: order.tripCount }),

    progress: order.progress, // 0…1, optional
    accentColor: '#1E88E5',

    // Dynamic Island compact regions (optional, see defaults below)
    compactLeading: { icon: { type: 'symbol', name: 'car.fill' } },
    compactTrailing: { timerEndEpochMs: order.deadline.getTime() },

    staleText: t('liveActivity.updating'),
    deepLinkUrl: `myapp://delivery/${order.id}`,
  };
}

if (LiveActivity.isSupported() && LiveActivity.areEnabled()) {
  try {
    LiveActivity.start(contentFor(order, t));
  } catch (e) {
    console.warn('[LiveActivity] start failed', e); // disabled in Settings
  }
}

// Any field can change, including the order the card shows.
LiveActivity.update(contentFor(nextOrder, t)); // identical payloads are skipped

// Show a final state for 5 s, then dismiss.
LiveActivity.end({
  finalContent: {
    title: t('liveActivity.delivered'),
    icon: { type: 'emoji', name: '✅' },
  },
  dismissAfterSeconds: 5,
});

// Cold start: kill orphans when nothing is active.
if (LiveActivity.isRunning() && !hasActiveOrders) LiveActivity.end();
```

## `LiveActivityContent`

Only `title` is required. Omitted fields are hidden.

| Field | Type | Where it shows |
| --- | --- | --- |
| `title` | `string` | Main line |
| `subtitle` | `string` | Under the title |
| `icon` | `LiveActivityIcon` | Leading icon. Also the default for compact leading and minimal |
| `value` / `valueUnit` | `string` | Big trailing value, e.g. `"12"` `"min"` |
| `timerEndEpochMs` | `number` | Native countdown in place of `value` |
| `secondaryValue` | `string` | Small line under the value, e.g. `"2.4 km"` |
| `footerText` / `footerIcon` / `footerTrailingText` | | Bottom row |
| `progress` | `number` 0…1 | Progress bar |
| `accentColor` / `backgroundColor` / `textColor` / `secondaryTextColor` | hex | Colors (`#RRGGBB` or `#RRGGBBAA`) |
| `compactLeading` / `compactTrailing` / `minimal` | `LiveActivitySlot` | Dynamic Island compact regions |
| `staleText` | `string` | Replaces the value once the content goes stale |
| `deepLinkUrl` | `string` | Opened on tap |
| `staleAfterSeconds` | `number` | Default 3600. After this, iOS marks the content stale |
| `relevanceScore` | `number` | iOS ordering when several activities are visible |

`LiveActivityIcon = { type: 'symbol' | 'emoji' | 'asset'; name: string; color?: string }`

`LiveActivitySlot = { text?; icon?; timerEndEpochMs?; color? }`. When several
fields are set, the widget uses the first one present in this order: timer,
then text, then icon.

Default compact regions when you don't set them:

| Region | Default |
| --- | --- |
| compact leading | `icon` |
| compact trailing | countdown, otherwise `value valueUnit` |
| minimal | countdown, otherwise `value`, otherwise `icon` |

## `LiveActivity` API

| Method | Returns | Notes |
| --- | --- | --- |
| `isSupported()` | `boolean` | iOS 16.2+, and always `true` on Android |
| `areEnabled()` | `boolean` | `false` when the user turned off Live Activities (iOS) or notifications (Android) |
| `isRunning()` | `boolean` | Survives app restarts on iOS. Use it to reconcile on cold start |
| `start(content)` | `void` | Ends all previous activities first. Throws when disabled |
| `update(content)` | `boolean` | Replaces all content. Returns `false` when the payload is identical to the last one |
| `end({ finalContent?, dismissAfterSeconds? })` | `void` | Ends every activity, including orphans left by a crash |

The raw native methods are also available on the default export:
`startLiveActivityWithContent`, `updateLiveActivityWithContent`,
`endLiveActivityWithContent`, `isLiveActivitySupported`,
`areLiveActivitiesEnabled`, and `isLiveActivityRunning`.

## Tips

- **Throttle updates.** ActivityKit rate-limits them. Push an update when the
  status changes, when the shown minute changes, or when the distance moves
  ≥ 100 m. Use `timerEndEpochMs` rather than pushing minutes every tick.
- **Privacy.** The Lock Screen is visible without unlocking the phone. Don't put
  customer names or phone numbers in the content.
- The content is limited to **4 KB** by ActivityKit. Keep strings short.

## Legacy API (deprecated)

`startLiveActivity(orderId, customerName, …)`, `updateLiveActivity(…)` and
`endLiveActivity()` still work with the old
`ios/widget/CourierWidgetLiveActivity.swift` template. That template has fixed
English labels, a hard-coded `nitrolocation://` deep link, and emoji chosen by
`status`. New integrations should use `LiveActivity`. The two APIs use
different activity types, so they don't interfere with each other.
