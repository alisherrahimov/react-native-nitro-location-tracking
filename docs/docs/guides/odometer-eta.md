---
sidebar_position: 9
---

# Odometer & ETA

## Odometer

A persisted running total of distance traveled **while tracking**. It
accumulates the straight-line distance between consecutive accepted fixes
natively, and it **survives app kill / reboot** (stored in
`SharedPreferences` on Android, `UserDefaults` on iOS).

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

const meters = NitroLocationModule.getOdometer(); // total meters since last reset
console.log(`Odometer: ${(meters / 1000).toFixed(2)} km`);

// e.g. reset at the start of a new shift / trip
NitroLocationModule.resetOdometer();
```

**Key points:**

- Only accumulates while tracking is active (fixes are flowing).
- GPS jitter (< 0.5 m) and implausible jumps (> 10 km between two fixes) are
  ignored.
- Rejected mock locations (when `setRejectMockLocations(true)`) are not
  counted.

## ETA

Straight-line distance and a rough time-to-arrival from the last known
location to a target coordinate, using the current native speed.

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

const { distanceMeters, etaSeconds } = NitroLocationModule.getEtaTo(
  41.311081, // target latitude
  69.240562 // target longitude
);

if (etaSeconds >= 0) {
  console.log(`${(distanceMeters / 1000).toFixed(1)} km, ~${Math.round(etaSeconds / 60)} min`);
} else {
  // -1: no known location, or the device is effectively stationary so speed is
  // unreliable — show distance only, or a placeholder.
  console.log(`${(distanceMeters / 1000).toFixed(1)} km`);
}
```

**Key points:**

- `distanceMeters` is straight-line (great-circle), not road distance — pair
  with a snap-to-road/routing service if you need drive-time accuracy.
- `etaSeconds` is `-1` when it can't be estimated (no known location, or
  speed below a 0.5 m/s floor). `distanceMeters` is `-1` only when there is
  no known location.
