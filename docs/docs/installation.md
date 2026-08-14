---
sidebar_position: 2
---

# Installation

```sh
npm install react-native-nitro-location-tracking react-native-nitro-modules
# or
yarn add react-native-nitro-location-tracking react-native-nitro-modules
```

:::info
`react-native-nitro-modules` is a required peer dependency.
:::

## iOS Setup

1. Install pods:

```sh
cd ios && pod install
```

2. Add the following keys to your `Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>We need your location to track your ride.</string>
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>We need background location access to continue tracking while the app is minimized.</string>
```

3. Enable **Background Modes** in Xcode:
   - Go to your target → **Signing & Capabilities** → **+ Capability** →
     **Background Modes**
   - Check **Location updates**

## Android Setup

The library's `AndroidManifest.xml` automatically merges the required
permissions and the foreground service declaration. No manual changes needed.

Permissions included automatically:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS`
- `INTERNET`
- `RECEIVE_BOOT_COMPLETED` (re-arm geofences after reboot)
- `WAKE_LOCK`
- `ACTIVITY_RECOGNITION` (motion engine for adaptive accuracy; runtime
  requestable on Android 10+ — request it yourself, or the library falls back
  to speed-based motion detection)

Next, set up [Permissions](./permissions) before starting location tracking.
