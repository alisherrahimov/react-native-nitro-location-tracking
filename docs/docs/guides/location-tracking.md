---
sidebar_position: 1
---

# Location Tracking

Configure the module, subscribe to updates, then start/stop tracking:

```tsx
import { useEffect, useState } from 'react';
import NitroLocationModule from 'react-native-nitro-location-tracking';
import type {
  LocationConfig,
  LocationData,
} from 'react-native-nitro-location-tracking';

const config: LocationConfig = {
  desiredAccuracy: 'high', // 'high' | 'balanced' | 'low'
  distanceFilter: 10, // meters
  intervalMs: 3000, // Android only
  fastestIntervalMs: 1000, // Android only
  stopTimeout: 5, // minutes before declaring stopped
  stopOnTerminate: false, // keep tracking after app close (Android)
  startOnBoot: true, // restart tracking after reboot (Android)
  foregroundNotificationTitle: 'Tracking Active',
  foregroundNotificationText: 'Your location is being tracked',
  adaptiveAccuracy: false, // opt-in: save battery by downgrading GPS when idle
};

function DriverScreen() {
  const [location, setLocation] = useState<LocationData | null>(null);
  const [isMoving, setIsMoving] = useState(false);
  const [isTracking, setIsTracking] = useState(false);

  useEffect(() => {
    NitroLocationModule.configure(config);
    NitroLocationModule.onLocation(setLocation);
    NitroLocationModule.onMotionChange(setIsMoving);
  }, []);

  const start = () => {
    NitroLocationModule.startTracking();
    setIsTracking(true);
  };
  const stop = () => {
    NitroLocationModule.stopTracking();
    setIsTracking(false);
  };

  return (
    <View>
      <Text>Tracking: {isTracking ? 'Yes' : 'No'}</Text>
      <Text>Moving: {isMoving ? 'Yes' : 'No'}</Text>
      {location && (
        <Text>
          {location.latitude.toFixed(6)}, {location.longitude.toFixed(6)}
          {'\n'}Speed: {location.speed} m/s | Bearing: {location.bearing}°
        </Text>
      )}
      <Button title="Start" onPress={start} />
      <Button title="Stop" onPress={stop} />
    </View>
  );
}
```

Other tracking calls on the module:

```tsx
// Get current location (one-shot)
const current = await NitroLocationModule.getCurrentLocation();

// Check tracking state
const tracking = NitroLocationModule.isTracking();

// Drain the durable Live Push queue now (when persistQueue is enabled).
// Resolves true if the queue is empty afterwards (all fixes delivered).
// When the queue is disabled this is a no-op that resolves true.
const synced = await NitroLocationModule.forceSync();

// How many fixes are still waiting in the durable queue (0 when disabled).
const pending = NitroLocationModule.getQueuedCount();
```

Notifications and cleanup:

```tsx
NitroLocationModule.showLocalNotification('Ride Started', 'Heading to pickup');
NitroLocationModule.updateForegroundNotification('En Route', '2.5 km away');

NitroLocationModule.destroy();
```

See also: [Adaptive Accuracy & Live Filtering](../api/motion-engine).
