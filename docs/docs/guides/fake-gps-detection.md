---
sidebar_position: 4
---

# Fake GPS Detection

Detect mock/fake GPS locations and optionally reject them:

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

// Check if device-level mock location is enabled
const fakeGpsOn = NitroLocationModule.isFakeGpsEnabled();
if (fakeGpsOn) {
  console.warn('Mock location provider is active!');
}

// Auto-reject mock locations (they won't fire onLocation callbacks)
NitroLocationModule.setRejectMockLocations(true);

// Each location update includes isMockLocation flag
NitroLocationModule.onLocation((location) => {
  if (location.isMockLocation) {
    console.warn('This location is from a mock provider');
  }
});

// Or watch device-level mock location toggle directly — fires independently
// of tracking, so it works even before startTracking() is called.
NitroLocationModule.onMockLocationDetected((isMockEnabled) => {
  console.warn(isMockEnabled ? 'Fake GPS turned ON' : 'Fake GPS turned OFF');
});
```

**Platform behavior:**

| Platform | Per-location detection                                        | Device-level detection              |
| -------- | --------------------------------------------------------------- | ------------------------------------ |
| Android  | `Location.isMock` (API 31+) / `isFromMockProvider` (API 18+)    | `AppOpsManager` mock location check   |
| iOS      | `CLLocation.sourceInformation.isSimulatedBySoftware` (iOS 15+)  | Simulator detection                   |
