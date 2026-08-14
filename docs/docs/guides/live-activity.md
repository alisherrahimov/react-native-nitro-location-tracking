---
sidebar_position: 15
---

# Live Activity (iOS Dynamic Island & Lock Screen · Android ongoing card)

Show a live delivery card that updates in real time as the order progresses.
One JS integration drives both platforms:

- **iOS** — a Live Activity on the Lock Screen and Dynamic Island (16.2+).
- **Android** — a rich **ongoing notification** ("delivery activity" card)
  showing the same status, ETA, distance, and address. Requires the
  `POST_NOTIFICATIONS` permission (already declared by the library) to be
  granted.

The status string maps to an emoji on both platforms (`picking_up` 📦,
`on_the_way` 🚗, `arriving` 🏁, `delivered` ✅).

## iOS Prerequisites (one-time setup)

1. **Create a Widget Extension** in Xcode:
   `File → New → Target → Widget Extension` — uncheck "Include Configuration
   App Intent"

2. **Copy the widget file** from the published package into your widget
   extension folder:

   ```sh
   # from your project root
   cp node_modules/react-native-nitro-location-tracking/ios/widget/CourierWidgetLiveActivity.swift \
      ios/YourWidgetExtension/CourierWidgetLiveActivity.swift
   ```

   Then in Xcode, add the copied file to the widget extension target's
   **Compile Sources**.

3. **Replace** the generated bundle file (`YourWidgetBundle.swift`) with:

   ```swift
   import WidgetKit
   import SwiftUI

   @main
   struct YourWidgetBundle: WidgetBundle {
       var body: some Widget {
           CourierWidgetLiveActivity()
       }
   }
   ```

4. **Delete** the other files Xcode generated (`YourWidget.swift`,
   `YourWidgetControl.swift`, `AppIntent.swift`).

5. **Add to your main app's `Info.plist`**:

   ```xml
   <key>NSSupportsLiveActivities</key>
   <true/>
   ```

6. **Verify embedding**: Main app target → **General** → **Frameworks,
   Libraries, and Embedded Content** → your `.appex` should be listed as
   **Embed Without Signing**.

7. On the device, go to **Settings → [Your App] → Live Activities** and make
   sure it is **ON**.

:::warning
Live Activities do not work in the iOS Simulator. Test on a real device
running iOS 16.2+. Dynamic Island UI requires iPhone 14 Pro or later.
:::

## Usage

```tsx
import NitroLocation, {
  isLiveActivitySupported,
} from 'react-native-nitro-location-tracking';
import type { LiveActivityState } from 'react-native-nitro-location-tracking';

// Guard — only call on iOS
if (!isLiveActivitySupported()) return;

// Start a Live Activity
try {
  NitroLocation.startLiveActivity(
    'ORD-9981',           // orderId
    'John Doe',           // customerName
    '42 Elm Street',      // deliveryAddress
    2,                    // orderCount
    'picking_up',         // status
    'Picking up order',   // statusText (localised label shown on widget)
    18,                   // estimatedMinutes
    4500                  // distanceMeters
  );
} catch (e) {
  // Thrown when Live Activities are disabled in Settings
  Alert.alert('Live Activity', String(e));
}

// Update as the delivery progresses
const nextState: LiveActivityState = {
  status: 'on_the_way',
  statusText: 'On the way',
  estimatedMinutes: 12,
  distanceMeters: 2800,
};
NitroLocation.updateLiveActivity(
  nextState.status,
  nextState.statusText,
  nextState.estimatedMinutes,
  nextState.distanceMeters
);

// End when delivered
NitroLocation.endLiveActivity();
```

**Status values and their meaning:**

| `status`     | Widget emoji | Typical use                   |
| ------------ | ------------ | ------------------------------ |
| `picking_up` | 📦           | Courier heading to restaurant |
| `on_the_way` | 🚗           | En route to customer          |
| `arriving`   | 🏁           | Less than ~1 min away         |
| `delivered`  | ✅           | Order handed over             |

**Platform behavior:**

| Platform  | Behavior                                                |
| --------- | -------------------------------------------------------- |
| iOS 16.2+ | Shows on Lock Screen; Dynamic Island on iPhone 14 Pro+  |
| Android   | Ongoing notification card — safe to call cross-platform |
