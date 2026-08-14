---
sidebar_position: 7
---

# Geofencing

Monitor **enter / exit / dwell** events for circular regions. Registered
regions are **durable** — they are persisted and re-armed after app kill and
device reboot, so monitoring keeps working without the app having to re-add
them.

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';
import type { GeofenceRegion } from 'react-native-nitro-location-tracking';

// Listen for geofence events ('enter' | 'exit' | 'dwell')
NitroLocationModule.onGeofenceEvent((event, regionId) => {
  console.log(`Geofence ${event} for region: ${regionId}`);
});

// Add a geofence around a pickup point
NitroLocationModule.addGeofence({
  id: 'pickup-zone',
  latitude: 41.311,
  longitude: 69.279,
  radius: 100, // meters
  notifyOnEntry: true,
  notifyOnExit: true,
  // Optional DWELL: fire once the device has stayed inside for loiteringDelayMs
  // — e.g. "courier arrived and is waiting at the pickup point".
  notifyOnDwell: true,
  loiteringDelayMs: 120000, // 2 minutes (default 5 min)
});

// Remove a specific geofence
NitroLocationModule.removeGeofence('pickup-zone');

// Remove all geofences
NitroLocationModule.removeAllGeofences();
```

**Key points:**

- **Durable across reboot.** On Android the regions are persisted and
  re-armed by a boot receiver (Google Play Services drops geofences on
  reboot); geofence transitions are delivered via a manifest receiver, so
  they survive process death. On iOS, `CLCircularRegion` monitoring is
  durable natively and the region metadata is restored on relaunch.
- **DWELL** is native on Android (`GEOFENCE_TRANSITION_DWELL`) and
  synthesised on iOS with a timer armed on entry and cancelled on exit.
- Events reach the `onGeofenceEvent` JS callback only while JS is alive. If
  the app is relaunched cold by a geofence transition, monitoring is
  restored but that specific in-flight event can't reach JS — surface it
  with a local notification from your own boot/launch handling if you need
  it.

:::note
iOS limits geofence regions to 20 per app. Android supports up to 100.
:::
