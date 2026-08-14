---
sidebar_position: 6
---

# Bearing Utilities

Calculate bearing between two coordinates and handle rotation smoothing:

```tsx
import {
  calculateBearing,
  shortestRotation,
} from 'react-native-nitro-location-tracking';

// Calculate bearing from point A to point B (in degrees, 0-360)
const bearing = calculateBearing(
  { latitude: 41.311, longitude: 69.279 },
  { latitude: 41.315, longitude: 69.285 }
);

// Smooth rotation to avoid spinning the long way around
const currentRotation = 350;
const targetRotation = 10;
const smoothed = shortestRotation(currentRotation, targetRotation); // 370 (not -350)
```
