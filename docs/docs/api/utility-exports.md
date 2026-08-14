---
sidebar_position: 4
---

# Utility Exports

| Export | Description |
| --- | --- |
| `LocationSmoother` | Class for smooth map marker animation between updates |
| `calculateBearing(from, to)` | Calculate bearing between two coordinates (degrees, 0-360) |
| `shortestRotation(from, to)` | Calculate shortest rotation path to avoid spinning |
| `requestLocationPermission()` | Request location + notification permissions (Android) |
| `isLiveActivitySupported()` | Returns `true` on iOS, `false` on Android |
| `LiveActivityState` | Type for `{ status, statusText, estimatedMinutes, distanceMeters }` passed to start/update |
| `SnapToRoad` | Buffers/batches raw fixes and snaps them via a pluggable `SnapToRoadProvider` |
