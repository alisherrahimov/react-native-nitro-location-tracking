---
sidebar_position: 5
---

# Pure C++ Math Engine

For computationally heavy tasks like array slicing and trip mapping, use the
pure C++ Math Engine to bypass the JS thread entirely.

```typescript
import { NitroLocationCalculations } from 'react-native-nitro-location-tracking';

// Instantly compute heavy math directly in C++
const stats = NitroLocationCalculations.calculateTotalTripStats(points);
```

| Method | Returns | Description |
| --- | --- | --- |
| `calculateTotalTripStats(points)` | `TripMathStats` | Instantly computes exact Haversine distance, time, and max/average speed over an array of thousands of points. |
| `filterAnomalousPoints(points, maxSpeedMps)` | `LocationPoint[]` | Cleans an array of points by mathematically stripping out teleportation jumps that exceed the given speed limit. |
| `smoothPath(points, toleranceMeters)` | `LocationPoint[]` | Simplifies a highly dense GPS path into perfect drawing lines using the Ramer-Douglas-Peucker algorithm. |
| `calculateBearing(lat1, lon1, lat2, lon2)` | `number` | Lightning fast C++ trigonometric bearing computation for raw coordinates. |
| `encodeGeohash(lat, lon, precision)` | `string` | Converts coordinates into a Geohash string instantly representing geological boundaries. |
