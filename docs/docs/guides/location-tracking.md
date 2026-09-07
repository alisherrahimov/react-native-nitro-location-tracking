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
    const result = NitroLocationModule.startTracking();
    setIsTracking(result === 'started');
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

## Checking the start result

`startTracking()` never throws and never crashes the process. It reports every
refusal through its [`TrackingStartResult`](../api/types#trackingstartresult)
return value, and only `'started'` means tracking is running:

```tsx
const result = NitroLocationModule.startTracking();
if (result !== 'started') {
  // Nothing was started, so retrying is safe.
  console.warn(`tracking did not start: ${result}`);
}
```

## Starting only while the app is foreground

On Android, a `location` foreground service may only be started while the app
holds a visible activity. `startTracking()` enforces this itself and returns
`'appBackgrounded'` rather than attempting a start that would kill the process
with `ForegroundServiceDidNotStartInTimeException` — see
[`TrackingStartResult`](../api/types#trackingstartresult) for why that crash
cannot be caught.

This matters for starts that are not driven by a tap: a start fired right after
a permission dialog, or from a GPS-toggle listener, can easily land while the
app is on its way to the background. Gate those on `AppState` and retry on the
next `active`:

```tsx
import { AppState } from 'react-native';

function startTrackingWhenForeground() {
  if (AppState.currentState === 'active') {
    return NitroLocationModule.startTracking();
  }
  // Fire once, on the next foreground.
  const sub = AppState.addEventListener('change', (state) => {
    if (state === 'active') {
      sub.remove();
      NitroLocationModule.startTracking();
    }
  });
  return 'appBackgrounded' as const;
}
```

Tracking is also not restarted by the system after a process death: the service
returns `START_NOT_STICKY`, because a system-initiated restart lands while the
app is in the background, where the promotion is refused and the process would
crash-loop with no user action able to stop it. Re-arm tracking from JS instead,
once the app is foreground and the location permission is confirmed granted.

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
