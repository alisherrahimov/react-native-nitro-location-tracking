---
sidebar_position: 11
---

# Location Provider Status

Detect when GPS/location services are turned on or off:

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

// Check current status
const enabled = NitroLocationModule.isLocationServicesEnabled();

// Listen for changes
NitroLocationModule.onProviderStatusChange((gps, network) => {
  console.log(`GPS: ${gps}, Network: ${network}`);
  if (gps === 'disabled') {
    console.warn('Please enable location services!');
  }
});
```

## Prompt user to enable GPS

Ask the user to turn on device location. On Android this shows the native
Google Play Services in-app dialog ("For better experience, turn on device
location…") without leaving the app. On iOS there is no equivalent system
dialog, so this opens your app's Settings page and resolves after the user
returns to the app.

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

async function ensureGpsOn() {
  if (NitroLocationModule.isLocationServicesEnabled()) {
    return true;
  }
  const enabled = await NitroLocationModule.openLocationSettings();
  if (enabled) {
    NitroLocationModule.startTracking();
  } else {
    // User declined the dialog (Android) or did not enable GPS (iOS)
  }
  return enabled;
}
```

**Platform behavior:**

- **Android** — Uses `SettingsClient.checkLocationSettings()` +
  `startResolutionForResult`. Resolves `true` if GPS is already on or if the
  user accepts the dialog, `false` if the user declines or the dialog cannot
  be shown.
- **iOS** — Opens the app's Settings page via
  `UIApplication.openSettingsURLString` and listens for
  `UIApplication.didBecomeActiveNotification` to detect the return to
  foreground. Resolves `true` if `CLLocationManager.locationServicesEnabled()`
  is on after the user returns, `false` otherwise.
