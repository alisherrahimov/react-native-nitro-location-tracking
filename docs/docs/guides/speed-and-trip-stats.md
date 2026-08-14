---
sidebar_position: 10
---

# Speed Monitoring & Trip Stats

## Speed Monitoring

Get alerts when speed crosses configurable thresholds:

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

// Configure speed thresholds
NitroLocationModule.configureSpeedMonitor({
  maxSpeedKmh: 120, // alert when exceeding 120 km/h
  minSpeedKmh: 5, // alert when below 5 km/h (idle detection)
  checkIntervalMs: 0, // check on every location update
});

// Listen for speed state transitions
NitroLocationModule.onSpeedAlert((alert, speedKmh) => {
  if (alert === 'exceeded') {
    console.warn(`Speed limit exceeded: ${speedKmh.toFixed(1)} km/h`);
  } else if (alert === 'below_minimum') {
    console.log('Driver appears idle');
  } else {
    console.log('Speed normalized');
  }
});

// Get current speed anytime
const speed = NitroLocationModule.getCurrentSpeed(); // km/h
```

## Distance Calculator (trip stats)

Track running trip statistics:

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

// Start recording a trip
NitroLocationModule.startTripCalculation();

// Check stats during the trip
const stats = NitroLocationModule.getTripStats();
console.log(`Distance: ${(stats.distanceMeters / 1000).toFixed(2)} km`);
console.log(`Duration: ${(stats.durationMs / 60000).toFixed(1)} min`);
console.log(`Avg speed: ${stats.averageSpeedKmh.toFixed(1)} km/h`);
console.log(`Max speed: ${stats.maxSpeedKmh.toFixed(1)} km/h`);
console.log(`Points: ${stats.pointCount}`);

// Stop and get final stats
const finalStats = NitroLocationModule.stopTripCalculation();

// Reset for a new trip
NitroLocationModule.resetTripCalculation();
```

For heavy trip-math over thousands of points, see the
[Pure C++ Math Engine](../api/cpp-math-engine).
