---
sidebar_position: 8
---

# Distance Utilities

Calculate distance between two points or from the current location to a
registered geofence:

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

// Calculate distance between any two coordinates (returns meters)
const meters = NitroLocationModule.getDistanceBetween(
  41.311158,
  69.279737, // point A
  41.3152,
  69.2851 // point B
);
console.log(`Distance: ${meters.toFixed(0)}m`);

// Get distance from current location to a registered geofence center
// First, register a geofence
NitroLocationModule.addGeofence({
  id: 'branch-123',
  latitude: 41.311158,
  longitude: 69.279737,
  radius: 150,
  notifyOnEntry: true,
  notifyOnExit: true,
});

// Then query distance using the same region id
const distToBranch = NitroLocationModule.getDistanceToGeofence('branch-123');
if (distToBranch >= 0) {
  console.log(`Distance to branch: ${distToBranch.toFixed(0)}m`);
} else {
  console.warn('Geofence not found or no location available');
}
```

**Key points:**

- Both methods use **native distance APIs** (`CLLocation.distance(from:)` on
  iOS, `Location.distanceBetween()` on Android) — no JS-thread computation.
- `getDistanceBetween()` is a pure utility — pass any two lat/lng pairs.
- `getDistanceToGeofence()` uses the device's **last known native location**
  and the registered geofence center. Returns `-1` if the region ID is not
  found or no location is available.
- The `regionId` is the `id` string you set when calling `addGeofence()`.
