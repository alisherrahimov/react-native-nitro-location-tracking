# react-native-nitro-location-tracking

A high-performance React Native location tracking library built with [Nitro Modules](https://nitro.margelo.com/). Designed for ride-hailing, delivery, and fleet tracking apps with background location, WebSocket connectivity, foreground service notifications, and smooth map marker animations.

## Features

- **Background location tracking** with foreground service (Android) and background modes (iOS)
- **WebSocket connection manager** with auto-reconnect and batch sync
- **Native live push** — per-fix HTTP POST sent from the native thread, so the server keeps receiving the courier's position even while the screen is off, the app is backgrounded, or the device is in Doze (no dependency on the JS thread)
- **Fake GPS detection** — detect mock locations and optionally reject them
- **Geofencing** — monitor enter/exit events for circular regions
- **Speed Monitoring** — configurable speed alerts with state-transition callbacks
- **Distance Calculator** — running trip stats with Haversine distance
- **Location Provider Status** — detect when GPS/location is turned on/off
- **Smooth map marker animations** via `LocationSmoother`
- **Bearing calculation** utilities for rotation/heading
- **Foreground notifications** (Android foreground service, iOS local notifications)
- **Live Activity / Dynamic Island** (iOS 16.2+) — show a delivery card on the Lock Screen and Dynamic Island that updates in real time
- **Permission helpers** for fine, background, and notification permissions
- **Built on Nitro Modules** for near-native performance via JSI

## Installation

```sh
npm install react-native-nitro-location-tracking react-native-nitro-modules
# or
yarn add react-native-nitro-location-tracking react-native-nitro-modules
```

> `react-native-nitro-modules` is a required peer dependency.

### iOS Setup

1. Install pods:

```sh
cd ios && pod install
```

1. Add the following keys to your `Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>We need your location to track your ride.</string>
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>We need background location access to continue tracking while the app is minimized.</string>
```

1. Enable **Background Modes** in Xcode:
   - Go to your target > **Signing & Capabilities** > **+ Capability** > **Background Modes**
   - Check **Location updates**

### Android Setup

The library's `AndroidManifest.xml` automatically merges the required permissions and the foreground service declaration. No manual changes needed.

Permissions included automatically:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS`
- `INTERNET`
- `RECEIVE_BOOT_COMPLETED` (re-arm geofences after reboot)
- `WAKE_LOCK`
- `ACTIVITY_RECOGNITION` (motion engine for adaptive accuracy; runtime-requestable
  on Android 10+ — request it yourself, or the library falls back to speed-based
  motion detection)

## Usage

### Request Permissions

Call this before starting location tracking, especially on Android:

```tsx
import { requestLocationPermission } from 'react-native-nitro-location-tracking';

const granted = await requestLocationPermission();
if (!granted) {
  console.warn('Location permission denied');
}
```

You can customize the permission dialog messages:

```tsx
const granted = await requestLocationPermission(
  {
    title: 'Location Access',
    message: 'We need your location to show your position on the map.',
    buttonPositive: 'Allow',
    buttonNegative: 'Deny',
  },
  {
    title: 'Background Location',
    message:
      'Allow background location to keep tracking while the app is minimized.',
    buttonPositive: 'Allow',
    buttonNegative: 'Deny',
  }
);
```

> On iOS, permissions are handled via `Info.plist` and the system prompt on first access.

**Single entry point.** Everything runs through the default export,
`NitroLocationModule` — the native HybridObject. There are no built-in React
hooks; wire callbacks into your own component state with `useState` /
`useEffect`. This keeps one source of truth and avoids hidden lifecycle magic.

### Location Tracking

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

### WebSocket Connection

Manage a WebSocket connection for real-time location sync directly on the module:

```tsx
import { useEffect, useState } from 'react';
import NitroLocationModule from 'react-native-nitro-location-tracking';
import type {
  ConnectionConfig,
  ConnectionState,
} from 'react-native-nitro-location-tracking';

const connectionConfig: ConnectionConfig = {
  wsUrl: 'wss://api.example.com/ws/driver',
  authToken: 'your-auth-token',
  reconnectIntervalMs: 5000,
  maxReconnectAttempts: 10,
};

function RideScreen() {
  const [connectionState, setConnectionState] =
    useState<ConnectionState>('disconnected');
  const [lastMessage, setLastMessage] = useState<string | null>(null);

  useEffect(() => {
    NitroLocationModule.configureConnection(connectionConfig);
    NitroLocationModule.onConnectionStateChange(setConnectionState);
    NitroLocationModule.onMessage(setLastMessage);
    return () => NitroLocationModule.disconnectWebSocket();
  }, []);

  return (
    <View>
      <Text>Connection: {connectionState}</Text>
      <Text>Last message: {lastMessage}</Text>
      <Button title="Connect" onPress={() => NitroLocationModule.connectWebSocket()} />
      <Button title="Disconnect" onPress={() => NitroLocationModule.disconnectWebSocket()} />
      <Button title="Send Ping" onPress={() => NitroLocationModule.sendMessage('ping')} />
    </View>
  );
}
```

Notifications and cleanup on the module:

```tsx
// Notifications
NitroLocationModule.showLocalNotification('Ride Started', 'Heading to pickup');
NitroLocationModule.updateForegroundNotification('En Route', '2.5 km away');

// Cleanup
NitroLocationModule.destroy();
```

### Live Push (background-safe per-fix POST)

The JS thread is suspended when the screen is off or the app is backgrounded, so
a `fetch()` driven by `onLocation` stops firing. **Live Push** moves the per-fix
HTTP POST onto the native thread — which the location foreground service keeps
alive — so the server keeps receiving positions in the background and in Doze.

It's generic by design: the lib owns the transport, your app owns the schema.
Each POST body is the parsed `extraFieldsJson` object with the location fields
merged on top. Config is pushed down from JS on rare events (login, token
refresh, new delivery); the native side then handles every fix on its own.

A fix is sent only when all three gates are open: **tracking is running** (no fix
otherwise) **and** push is **enabled** **and** a **config** is set. A `401`
response auto-clears the config until JS re-configures with a fresh token.

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';
import type { LivePushConfig } from 'react-native-nitro-location-tracking';

// 1. Configure — on login / token refresh / new delivery (while JS is awake):
const config: LivePushConfig = {
  url: 'https://api.example.com/tracking', // full endpoint
  authToken: token, // sent as `Authorization: Bearer <token>`
  // Static fields merged into every POST body. A JSON string, so values may be
  // string / number / bool / null. The location fields are added on top.
  extraFieldsJson: JSON.stringify({ courier_id: 'c1', delivery_id: null, active: true }),
  // false → body = { latitude, longitude, timestamp, ...extraFields }
  // true  → also include speed, bearing, accuracy, altitude
  includeFullPoint: false,
  // Opt-in durable offline queue (default false). See "Durable queue" below.
  persistQueue: true,
  batchSize: 5, // POST up to 5 fixes per request (body becomes a JSON array)
  batchMaxDelayMs: 3000, // flush a partial batch after 3s
  maxQueueSize: 10000, // cap; oldest fixes dropped beyond this
};
NitroLocationModule.configureLivePush(config);

// 2. Toggle at runtime — cheap on/off that keeps config (duty / online state):
NitroLocationModule.setLivePushEnabled(true);
NitroLocationModule.setLivePushEnabled(false);

// 3. Clear — on logout (wipes config and disables):
NitroLocationModule.clearLivePush();

// Optional: observe each POST outcome (foreground only — see caveat below):
NitroLocationModule.onLivePushResult((r) => {
  if (!r.ok) {
    console.warn('live push failed', r.statusCode, r.error);
    // e.g. on 401, re-authenticate and call configureLivePush again
  }
});
```

> **`onLivePushResult` outcomes are buffered across suspension.** The POST keeps
> firing natively while the JS thread is suspended (screen off / backgrounded /
> Doze). Outcomes that land during suspension are held in a small native ring
> buffer (most-recent outcomes win) and replayed in order the next time a callback
> is registered / JS resumes, so a "last sync OK / 401" indicator stays reliable.
> The buffer is bounded, so this is foreground observability — for guaranteed
> per-fix delivery, enable `persistQueue`. `statusCode` is the HTTP code (or `0`
> for a network error / timeout); `error` is `''` on success.

#### Durable queue (`persistQueue`)

By default Live Push is **fire-and-forget**: one POST per fix, dropped on failure,
and the next successful push corrects the server-side position. That is fine for
"where is the courier right now" but loses fixes captured with no connectivity.

Set `persistQueue: true` to turn on the **durable offline queue**:

- Every fix is written to a native **SQLite** queue before sending.
- A background drainer POSTs the oldest fixes first, **deletes a row only on a 2xx**
  response, and **retries with exponential backoff** (2s → 60s) on network errors
  or `5xx` / `429`. Non-retryable codes (`400` / `401` / `413`) drop the offending
  rows so a poison payload can't wedge the queue.
- The queue **survives app kill / reboot** — fixes recorded offline are delivered
  when connectivity returns.
- **Batching:** `batchSize > 1` sends up to N fixes in one request. The body then
  becomes a **JSON array** of the per-fix objects (your endpoint must accept an
  array); `batchMaxDelayMs` bounds how long a partial batch waits before flushing.
- **Back-pressure:** `maxQueueSize` (default 10000) caps the DB; the oldest fixes
  are dropped beyond it during long outages.
- Call `forceSync()` to drain on demand (resolves `true` when the queue is empty),
  and `getQueuedCount()` to read how many fixes are still pending.

> The WebSocket connection (`connectWebSocket` / `sendMessage`) remains available
> for real-time messaging, independent of Live Push.

### Fake GPS Detection

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

| Platform | Per-location detection                                         | Device-level detection              |
| -------- | -------------------------------------------------------------- | ----------------------------------- |
| Android  | `Location.isMock` (API 31+) / `isFromMockProvider` (API 18+)   | `AppOpsManager` mock location check |
| iOS      | `CLLocation.sourceInformation.isSimulatedBySoftware` (iOS 15+) | Simulator detection                 |

### Smooth Map Marker Animation

Use `LocationSmoother` to animate a map marker smoothly between location updates:

```tsx
import { useEffect, useRef, useState } from 'react';
import { Marker } from 'react-native-maps';
import NitroLocationModule, {
  LocationSmoother,
} from 'react-native-nitro-location-tracking';
import type { LocationData } from 'react-native-nitro-location-tracking';

function MapScreen() {
  const markerRef = useRef(null);
  const smootherRef = useRef(new LocationSmoother(markerRef));
  const [location, setLocation] = useState<LocationData | null>(null);

  useEffect(() => {
    NitroLocationModule.configure(config);
    NitroLocationModule.onLocation((loc) => {
      // Feed each new location into the smoother, then keep the marker mounted
      smootherRef.current.feed(loc);
      setLocation(loc);
    });
    NitroLocationModule.startTracking();
    return () => NitroLocationModule.stopTracking();
  }, []);

  return (
    <MapView>
      {location && (
        <Marker
          ref={markerRef}
          coordinate={{
            latitude: location.latitude,
            longitude: location.longitude,
          }}
        />
      )}
    </MapView>
  );
}
```

### Bearing Utilities

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

### Geofencing

Monitor **enter / exit / dwell** events for circular regions. Registered regions
are **durable** — they are persisted and re-armed after app kill and device
reboot, so monitoring keeps working without the app having to re-add them.

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

- **Durable across reboot.** On Android the regions are persisted and re-armed by
  a boot receiver (Google Play Services drops geofences on reboot); geofence
  transitions are delivered via a manifest receiver, so they survive process
  death. On iOS, `CLCircularRegion` monitoring is durable natively and the region
  metadata is restored on relaunch.
- **DWELL** is native on Android (`GEOFENCE_TRANSITION_DWELL`) and synthesised on
  iOS with a timer armed on entry and cancelled on exit.
- Events reach the `onGeofenceEvent` JS callback only while JS is alive. If the
  app is relaunched cold by a geofence transition, monitoring is restored but that
  specific in-flight event can't reach JS — surface it with a local notification
  from your own boot/launch handling if you need it.

> **Note:** iOS limits geofence regions to 20 per app. Android supports up to 100.

### Distance Utilities

Calculate distance between two points or from the current location to a registered geofence:

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

- Both methods use **native distance APIs** (`CLLocation.distance(from:)` on iOS, `Location.distanceBetween()` on Android) — no JS-thread computation.
- `getDistanceBetween()` is a pure utility — pass any two lat/lng pairs.
- `getDistanceToGeofence()` uses the device's **last known native location** and the registered geofence center. Returns `-1` if the region ID is not found or no location is available.
- The `regionId` is the `id` string you set when calling `addGeofence()`.

### Odometer

A persisted running total of distance traveled **while tracking**. It accumulates
the straight-line distance between consecutive accepted fixes natively, and it
**survives app kill / reboot** (stored in `SharedPreferences` on Android,
`UserDefaults` on iOS).

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

const meters = NitroLocationModule.getOdometer(); // total meters since last reset
console.log(`Odometer: ${(meters / 1000).toFixed(2)} km`);

// e.g. reset at the start of a new shift / trip
NitroLocationModule.resetOdometer();
```

**Key points:**

- Only accumulates while tracking is active (fixes are flowing).
- GPS jitter (< 0.5 m) and implausible jumps (> 10 km between two fixes) are ignored.
- Rejected mock locations (when `setRejectMockLocations(true)`) are not counted.

### ETA

Straight-line distance and a rough time-to-arrival from the last known location to
a target coordinate, using the current native speed.

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

- `distanceMeters` is straight-line (great-circle), not road distance — pair with a
  snap-to-road/routing service if you need drive-time accuracy.
- `etaSeconds` is `-1` when it can't be estimated (no known location, or speed below
  a 0.5 m/s floor). `distanceMeters` is `-1` only when there is no known location.

### Speed Monitoring

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

### Distance Calculator

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

### Location Provider Status

Detect when GPS/location services are turned on or off:

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

// Check current status
const enabled = NitroLocationModule.isLocationServicesEnabled();

// Listen for changes
NitroLocationModule.onProviderStatusChange((gps, network) => {
  console.log(`GPS: ${gps}, Network: ${network}`);
  if (gps === 'disabled') {
    console.warn('Please enable location services!');
  }
});
```

### Prompt user to enable GPS

Ask the user to turn on device location. On Android this shows the native
Google Play Services in-app dialog ("For better experience, turn on device
location…") without leaving the app. On iOS there is no equivalent system
dialog, so this opens your app's Settings page and resolves after the user
returns to the app.

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

async function ensureGpsOn() {
  if (NitroLocationModule.isLocationServicesEnabled()) {
    return true;
  }
  const enabled = await NitroLocationModule.openLocationSettings();
  if (enabled) {
    NitroLocationModule.startTracking();
  } else {
    // User declined the dialog (Android) or did not enable GPS (iOS)
  }
  return enabled;
}
```

**Platform behavior:**

- **Android** — Uses `SettingsClient.checkLocationSettings()` + `startResolutionForResult`. Resolves `true` if GPS is already on or if the user accepts the dialog, `false` if the user declines or the dialog cannot be shown.
- **iOS** — Opens the app's Settings page via `UIApplication.openSettingsURLString` and listens for `UIApplication.didBecomeActiveNotification` to detect the return to foreground. Resolves `true` if `CLLocationManager.locationServicesEnabled()` is on after the user returns, `false` otherwise.

### Background reliability on Android OEMs

On many Android skins — Xiaomi/MIUI, Huawei/EMUI, Honor, Oppo·Realme/ColorOS,
Vivo, Samsung, OnePlus — the system aggressively kills background services unless
the app is **exempt from battery optimization** and **allowed to auto-start**.
Without this, background location tracking silently stops after a while, even
with a foreground service. These helpers let you detect the state and prompt the
user to fix it. **All are Android-only; iOS returns the safe defaults noted below.**

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

async function ensureBackgroundReliable() {
  // 1. Battery optimization (stock Android / Doze).
  if (!NitroLocationModule.isIgnoringBatteryOptimizations()) {
    await NitroLocationModule.requestIgnoreBatteryOptimizations();
  }

  // 2. OEM auto-start / "protected apps" — only worth prompting on known OEMs.
  const oem = NitroLocationModule.getDeviceManufacturer();
  if (['xiaomi', 'redmi', 'poco', 'huawei', 'honor', 'oppo', 'realme', 'vivo']
        .some((m) => oem.includes(m))) {
    await NitroLocationModule.openOemAutoStartSettings();
  }
}
```

**Behavior & platform notes:**

- `getDeviceManufacturer()` — lowercased `Build.MANUFACTURER` (e.g. `"xiaomi"`); `"apple"` on iOS.
- `isIgnoringBatteryOptimizations()` — reflects `PowerManager.isIgnoringBatteryOptimizations`; always `true` on iOS.
- `requestIgnoreBatteryOptimizations()` — resolves with the resulting state **after the user returns**. If your app declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, it shows the one-tap system dialog; otherwise it opens the battery-optimization settings list. iOS resolves `true`.
- `openOemAutoStartSettings()` — opens the vendor auto-start screen (best-effort; OEM Activities are undocumented and vary by version), falling back to the app's details page. Resolves `true` if a screen opened; iOS resolves `false`.

> **One-tap battery dialog (optional).** Google Play restricts
> `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to apps with a qualifying background use
> case (background location qualifies) and requires a Play Console declaration.
> The library does **not** bundle this permission — add it to your app's manifest
> if you want the one-tap dialog. Without it, `requestIgnoreBatteryOptimizations()`
> still works by opening the settings list.

```xml
<!-- Optional: enables the one-tap battery-exemption dialog. Requires a Play
     Console declaration. Omit to use the settings-list fallback. -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

### Permission Status

Check the current location permission status without prompting the user:

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

const status = NitroLocationModule.getLocationPermissionStatus();

switch (status) {
  case 'always':
    console.log('Background location granted');
    break;
  case 'whenInUse':
    console.log('Foreground only — background tracking may not work');
    break;
  case 'denied':
    console.warn('Location permission denied');
    break;
  case 'restricted':
    console.warn('Location restricted by parental controls or MDM');
    break;
  case 'notDetermined':
    console.log('Permission not yet requested');
    break;
}
```

| Status          | iOS                      | Android                      |
| --------------- | ------------------------ | ---------------------------- |
| `notDetermined` | Not yet asked            | N/A (returns `denied`)       |
| `denied`        | User denied              | Fine location not granted    |
| `restricted`    | Parental/MDM restriction | N/A (returns `denied`)       |
| `whenInUse`     | Authorized when in use   | Fine granted, background not |
| `always`        | Authorized always        | Fine + background granted    |

### Request Permission (Native)

Request location permission directly via the native module and get the resulting status:

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

async function setup() {
  // Check current status first (no dialog)
  const current = NitroLocationModule.getLocationPermissionStatus();

  if (current === 'always' || current === 'whenInUse') {
    // Already granted — start tracking
    NitroLocationModule.startTracking();
    return;
  }

  // Request permission — shows the system dialog
  const status = await NitroLocationModule.requestLocationPermission();

  switch (status) {
    case 'always':
    case 'whenInUse':
      NitroLocationModule.startTracking();
      break;
    case 'denied':
      Alert.alert('Location Required', 'Please enable location in Settings');
      break;
    case 'restricted':
      // Parental controls / MDM — cannot request
      break;
  }
}
```

**Platform behavior:**

| Platform | Behavior                                                                                                                                                                                                               |
| -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| iOS      | Calls `requestAlwaysAuthorization()`. If permission is already determined, resolves immediately with current status. If `start()` was called before permission was granted, tracking auto-starts once the user allows. |
| Android  | Uses React Native's `PermissionAwareActivity` to show the system permission dialog for `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`. Resolves with the resulting status after the user responds.                  |

### Permission Change Listener

Listen for permission changes in real time. The callback fires whenever the user changes the location permission (via the system dialog, the Settings app, or MDM policy changes):

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

// Register a listener — fires whenever the permission status changes
NitroLocationModule.onPermissionStatusChange((status) => {
  console.log('Permission changed to:', status);
  // status: 'notDetermined' | 'denied' | 'restricted' | 'whenInUse' | 'always'

  switch (status) {
    case 'always':
      console.log('Background location granted — full tracking available');
      break;
    case 'whenInUse':
      console.log('Foreground only — background tracking may not work');
      break;
    case 'denied':
      Alert.alert('Location Required', 'Please re-enable location in Settings');
      break;
    case 'restricted':
      console.warn('Location restricted by parental controls or MDM');
      break;
  }
});
```

**Platform behavior:**

| Platform | Mechanism | When the callback fires |
| -------- | --------- | ----------------------- |
| iOS      | `locationManagerDidChangeAuthorization` delegate | Immediately when the user changes permission (system dialog, Settings, MDM) |
| Android  | `ProcessLifecycleOwner` lifecycle observer | When the app returns to foreground after the user changes permission in Settings |

### Snap to road (pluggable)

For cleaner trip polylines, snap raw GPS fixes to the road network. The library
**bundles no map vendor** — you implement `SnapToRoadProvider` around whatever
service you use (Google Roads API, Mapbox Map Matching, Valhalla, OSRM, …) and
feed fixes through `SnapToRoad`, which buffers them, batches provider calls, and
falls back to raw points if the provider errors.

```tsx
import NitroLocationModule, {
  SnapToRoad,
  type SnapToRoadProvider,
  type SnapPoint,
} from 'react-native-nitro-location-tracking';

// 1. Wrap your roads service.
const googleRoads: SnapToRoadProvider = {
  async snap(points: SnapPoint[]) {
    const path = points.map((p) => `${p.latitude},${p.longitude}`).join('|');
    const res = await fetch(
      `https://roads.googleapis.com/v1/snapToRoads?interpolate=true&path=${path}&key=YOUR_KEY`
    );
    const json = await res.json();
    return (json.snappedPoints ?? []).map((sp: any) => ({
      latitude: sp.location.latitude,
      longitude: sp.location.longitude,
    }));
  },
};

// 2. Buffer fixes as they arrive.
const snapper = new SnapToRoad(googleRoads, { minDistanceMeters: 5 });
NitroLocationModule.onLocation((loc) => snapper.add(loc));

// 3. Snap when you need the polyline (clears the buffer).
const polyline = await snapper.flush();
```

- `batchSize` (default 100) splits large buffers into sequential provider calls.
- `minDistanceMeters` thins dense/duplicate fixes before snapping.
- A failed or empty provider response falls back to that batch's raw points, so
  the returned line is always continuous.

### Live Activity (iOS Dynamic Island & Lock Screen · Android ongoing card)

Show a live delivery card that updates in real time as the order progresses. One
JS integration drives both platforms:

- **iOS** — a Live Activity on the Lock Screen and Dynamic Island (16.2+).
- **Android** — a rich **ongoing notification** ("delivery activity" card) showing
  the same status, ETA, distance, and address. No longer a no-op; requires the
  `POST_NOTIFICATIONS` permission (already declared by the library) to be granted.

The status string maps to an emoji on both platforms (`picking_up` 📦, `on_the_way`
🚗, `arriving` 🏁, `delivered` ✅).

#### iOS Prerequisites (one-time setup)

1. **Create a Widget Extension** in Xcode:
   `File → New → Target → Widget Extension` — uncheck "Include Configuration App Intent"

2. **Copy the widget file** from the published package into your widget extension folder:

   ```sh
   # from your project root
   cp node_modules/react-native-nitro-location-tracking/ios/widget/CourierWidgetLiveActivity.swift \
      ios/YourWidgetExtension/CourierWidgetLiveActivity.swift
   ```

   Then in Xcode, add the copied file to the widget extension target's **Compile Sources**.

3. **Replace** the generated bundle file (`YourWidgetBundle.swift`) with:

   ```swift
   import WidgetKit
   import SwiftUI

   @main
   struct YourWidgetBundle: WidgetBundle {
       var body: some Widget {
           CourierWidgetLiveActivity()
       }
   }
   ```

4. **Delete** the other files Xcode generated (`YourWidget.swift`, `YourWidgetControl.swift`, `AppIntent.swift`).

5. **Add to your main app's `Info.plist`**:

   ```xml
   <key>NSSupportsLiveActivities</key>
   <true/>
   ```

6. **Verify embedding**: Main app target → **General** → **Frameworks, Libraries, and Embedded Content** → your `.appex` should be listed as **Embed Without Signing**.

7. On the device, go to **Settings → [Your App] → Live Activities** and make sure it is **ON**.

> Live Activities do not work in the iOS Simulator. Test on a real device running iOS 16.2+.
> Dynamic Island UI requires iPhone 14 Pro or later.

#### Usage

```tsx
import NitroLocation, {
  isLiveActivitySupported,
} from 'react-native-nitro-location-tracking';
import type { LiveActivityState } from 'react-native-nitro-location-tracking';

// Guard — only call on iOS
if (!isLiveActivitySupported()) return;

// Start a Live Activity
try {
  NitroLocation.startLiveActivity(
    'ORD-9981',           // orderId
    'John Doe',           // customerName
    '42 Elm Street',      // deliveryAddress
    2,                    // orderCount
    'picking_up',         // status
    'Picking up order',   // statusText (localised label shown on widget)
    18,                   // estimatedMinutes
    4500                  // distanceMeters
  );
} catch (e) {
  // Thrown when Live Activities are disabled in Settings
  Alert.alert('Live Activity', String(e));
}

// Update as the delivery progresses
const nextState: LiveActivityState = {
  status: 'on_the_way',
  statusText: 'On the way',
  estimatedMinutes: 12,
  distanceMeters: 2800,
};
NitroLocation.updateLiveActivity(
  nextState.status,
  nextState.statusText,
  nextState.estimatedMinutes,
  nextState.distanceMeters
);

// End when delivered
NitroLocation.endLiveActivity();
```

**Status values and their meaning:**

| `status`      | Widget emoji | Typical use                    |
| ------------- | ------------ | ------------------------------ |
| `picking_up`  | 📦           | Courier heading to restaurant  |
| `on_the_way`  | 🚗           | En route to customer           |
| `arriving`    | 🏁           | Less than ~1 min away          |
| `delivered`   | ✅           | Order handed over              |

**Platform behavior:**

| Platform | Behavior |
| -------- | -------- |
| iOS 16.2+ | Shows on Lock Screen; Dynamic Island on iPhone 14 Pro+ |
| Android   | No-op — calls are safe to make cross-platform |

## API Reference

### Types

#### `LocationData`

```ts
interface LocationData {
  latitude: number;
  longitude: number;
  altitude: number;
  speed: number; // m/s
  bearing: number; // degrees
  accuracy: number; // meters
  timestamp: number; // unix ms
  isMockLocation?: boolean; // true when from a mock provider
}
```

#### `LocationConfig`

```ts
interface LocationConfig {
  desiredAccuracy: 'high' | 'balanced' | 'low';
  distanceFilter: number; // meters
  intervalMs: number; // Android only
  fastestIntervalMs: number; // Android only
  stopTimeout: number; // minutes before declaring stopped
  stopOnTerminate: boolean; // keep tracking after app close (Android)
  startOnBoot: boolean; // restart tracking after reboot (Android)
  foregroundNotificationTitle: string;
  foregroundNotificationText: string;
  adaptiveAccuracy?: boolean; // opt-in: downgrade GPS when stationary (default false)
  kalmanFilter?: boolean; // opt-in: smooth the live stream (default false)
  kalmanProcessNoiseMps?: number; // Kalman process noise in m/s (default 1.0)
  accuracyFilter?: number; // drop fixes worse than N meters (default 0 = off)
}
```

### Adaptive accuracy & the motion engine

`onMotionChange(callback)` reports whether the device is **moving** or
**stationary**. It is backed by a motion state machine, not a raw speed check.

**How motion is detected (with graceful fallback):**

1. **OS activity recognition** (preferred) — Activity Recognition on Android,
   Core Motion "Motion & Fitness" on iOS. This is what lets the library know the
   device is parked even when GPS still reports small jitter.
2. **Speed-based fallback** — if the activity permission is missing or
   unavailable, motion is inferred from the location stream's speed. No extra
   permission required; slightly less precise.

Transitions are **debounced** by `stopTimeout` (minutes): the device must be
continuously still for that long before it is declared stationary, which avoids
flapping at traffic lights.

**Adaptive accuracy** (`adaptiveAccuracy: true`, default `false`): when the motion
engine reports stationary, the library drops GPS to low power (longer interval,
larger distance filter / coarser accuracy) and restores your configured
`desiredAccuracy` the moment it starts moving again — saving battery on long idle
stretches. It's opt-in so existing integrations keep their current behavior.

**Permissions required for OS activity recognition:**

| Platform | Requirement |
| -------- | ----------- |
| Android  | `ACTIVITY_RECOGNITION` (added by the library's manifest; **runtime-requestable on API 29+** — request it in your app, or the engine silently uses the speed fallback) |
| iOS      | `NSMotionUsageDescription` in your app's `Info.plist` (or the engine uses the speed fallback) |

> Without these, tracking and `onMotionChange` still work — they just fall back to
> speed-based motion detection. Adaptive accuracy still functions on top of
> whichever motion source is active.

### Live location filtering (Kalman + accuracy gate)

Raw GPS is noisy: the position "dances" a few meters while you stand still, and
occasionally spikes far away near buildings/tunnels. Two opt-in, native filters
clean the **live stream** before it reaches JS, Live Push, or trip stats — no
post-processing needed.

```tsx
NitroLocationModule.configure({
  // ...other config
  accuracyFilter: 50, // drop fixes reporting worse than 50 m accuracy
  kalmanFilter: true, // smooth the survivors
  kalmanProcessNoiseMps: 1.0, // ~walking/driving; higher = less smoothing
});
```

- **`accuracyFilter`** (meters, default `0` = off) — a cheap gate that discards
  low-confidence fixes outright before anything downstream sees them.
- **`kalmanFilter`** (default `false`) — runs each surviving fix through a
  position **Kalman filter** that weights it by its reported accuracy, so jitter
  is smoothed and outlier spikes are dampened. **This changes the emitted
  `latitude`/`longitude`** — they become the filtered estimate, not the raw chip
  output. Speed and bearing are left as reported.
- **`kalmanProcessNoiseMps`** (m/s, default `1.0`) — tuning knob: higher trusts
  new fixes more (more responsive, less smoothing), lower trusts the motion model
  more (smoother, laggier).

The filter is rebuilt each time tracking starts, so it never carries stale state
into a new session. The odometer, speed monitor, and trip calculator all consume
the filtered stream when these are enabled.

#### `ConnectionConfig`

```ts
interface ConnectionConfig {
  wsUrl: string;
  authToken: string;
  reconnectIntervalMs: number;
  maxReconnectAttempts: number;
}
```

#### `LivePushConfig`

```ts
interface LivePushConfig {
  url: string; // full endpoint, e.g. https://api.example.com/tracking
  authToken: string; // sent as `Authorization: Bearer <authToken>`
  // JSON object string merged into every POST body alongside the location
  // fields, e.g. '{"courier_id":"c1","delivery_id":null,"active":true}'.
  // A string (not a typed map) so values may be string / number / bool / null.
  extraFieldsJson: string;
  // false → body carries { latitude, longitude, timestamp } only.
  // true  → also include speed, bearing, accuracy, altitude.
  includeFullPoint: boolean;
  // Opt-in durable offline queue (default false). See "Durable queue" above.
  persistQueue?: boolean;
  // Fixes per POST when draining the queue (default 1). >1 → body is a JSON array.
  batchSize?: number;
  // Flush a partial batch after this many ms (default 0 = flush immediately).
  batchMaxDelayMs?: number;
  // Cap on queued fixes (default 10000); oldest dropped beyond this.
  maxQueueSize?: number;
}
```

#### `LivePushResult`

```ts
interface LivePushResult {
  ok: boolean; // true on a 2xx response
  statusCode: number; // HTTP status code, or 0 for a network error / timeout
  error: string; // '' on success, else a short message ("timeout", "401", …)
}
```

#### `GeofenceRegion`

```ts
interface GeofenceRegion {
  id: string;
  latitude: number;
  longitude: number;
  radius: number; // meters
  notifyOnEntry: boolean;
  notifyOnExit: boolean;
  notifyOnDwell?: boolean; // fire 'dwell' after loiteringDelayMs inside (default false)
  loiteringDelayMs?: number; // dwell delay in ms (default 300000 = 5 min)
}

// type GeofenceEvent = 'enter' | 'exit' | 'dwell'
```

#### `SpeedConfig`

```ts
interface SpeedConfig {
  maxSpeedKmh: number; // speed limit in km/h
  minSpeedKmh: number; // minimum speed threshold
  checkIntervalMs: number; // how often to evaluate
}
```

#### `TripStats`

```ts
interface TripStats {
  distanceMeters: number;
  durationMs: number;
  averageSpeedKmh: number;
  maxSpeedKmh: number;
  pointCount: number;
}
```

#### `PermissionStatus`

```ts
type PermissionStatus =
  | 'notDetermined'
  | 'denied'
  | 'restricted'
  | 'whenInUse'
  | 'always';
```

### Native Module Methods

All functionality is exposed on the default export, `NitroLocationModule`.

| Method                                       | Returns                     | Description                                                                              |
| -------------------------------------------- | --------------------------- | ---------------------------------------------------------------------------------------- |
| `configure(config)`                          | `void`                      | Set location tracking configuration                                                      |
| `startTracking()`                            | `void`                      | Start location tracking                                                                  |
| `stopTracking()`                             | `void`                      | Stop location tracking                                                                   |
| `getCurrentLocation()`                       | `Promise<LocationData>`     | Get a one-shot location                                                                  |
| `isTracking()`                               | `boolean`                   | Check if tracking is active                                                              |
| `onLocation(callback)`                       | `void`                      | Register location update callback                                                        |
| `onMotionChange(callback)`                   | `void`                      | Register motion state callback                                                           |
| `configureConnection(config)`                | `void`                      | Set WebSocket/REST configuration                                                         |
| `connectWebSocket()`                         | `void`                      | Open WebSocket connection                                                                |
| `disconnectWebSocket()`                      | `void`                      | Close WebSocket connection                                                               |
| `sendMessage(message)`                       | `void`                      | Send a message via WebSocket                                                             |
| `getConnectionState()`                       | `ConnectionState`           | Get current connection state                                                             |
| `onConnectionStateChange(callback)`          | `void`                      | Register connection state callback                                                       |
| `onMessage(callback)`                        | `void`                      | Register incoming message callback                                                       |
| `forceSync()`                                | `Promise<boolean>`          | Drain the durable Live Push queue now; resolves `true` when empty (no-op resolving `true` when the queue is disabled) |
| `getQueuedCount()`                           | `number`                    | Fixes currently waiting in the durable Live Push queue (`0` when disabled)                |
| `configureLivePush(config)`                  | `void`                      | Set the native live-push endpoint, token, and body fields (call on login / token refresh / new delivery) |
| `setLivePushEnabled(enabled)`                | `void`                      | Cheap runtime on/off for live push without losing config (call on duty/online state changes) |
| `clearLivePush()`                            | `void`                      | Wipe live-push config and disable it (call on logout)                                    |
| `onLivePushResult(callback)`                 | `void`                      | Observe each live-push POST outcome (`LivePushResult`). Buffered across JS suspension — see callout above |
| `isFakeGpsEnabled()`                         | `boolean`                   | Check if device-level mock location is enabled                                           |
| `setRejectMockLocations(reject)`             | `void`                      | Auto-reject mock locations when `true`                                                   |
| `onMockLocationDetected(callback)`           | `void`                      | Register a callback that fires when device-level mock location is toggled on/off         |
| `addGeofence(region)`                        | `void`                      | Start monitoring a circular geofence region                                              |
| `removeGeofence(regionId)`                   | `void`                      | Stop monitoring a specific geofence                                                      |
| `removeAllGeofences()`                       | `void`                      | Remove all active geofences                                                              |
| `onGeofenceEvent(callback)`                  | `void`                      | Register geofence enter/exit callback                                                    |
| `getDistanceBetween(lat1, lon1, lat2, lon2)` | `number`                    | Calculate distance between two points in meters (native Haversine)                       |
| `getDistanceToGeofence(regionId)`            | `number`                    | Get distance in meters from last known location to a geofence center (`-1` if not found) |
| `getOdometer()`                              | `number`                    | Total meters traveled while tracking; persisted across app kill / reboot                 |
| `resetOdometer()`                            | `void`                      | Reset the persisted odometer to 0                                                         |
| `getEtaTo(latitude, longitude)`              | `EtaResult`                 | Straight-line distance + ETA (seconds) from last known location to a target (`-1` fields when not estimable) |
| `configureSpeedMonitor(config)`              | `void`                      | Set speed monitoring thresholds                                                          |
| `onSpeedAlert(callback)`                     | `void`                      | Register speed state-transition callback                                                 |
| `getCurrentSpeed()`                          | `number`                    | Get current speed in km/h                                                                |
| `startTripCalculation()`                     | `void`                      | Start recording trip distance/stats                                                      |
| `stopTripCalculation()`                      | `TripStats`                 | Stop recording and get final stats                                                       |
| `getTripStats()`                             | `TripStats`                 | Get current trip stats without stopping                                                  |
| `resetTripCalculation()`                     | `void`                      | Reset trip calculator                                                                    |
| `isLocationServicesEnabled()`                | `boolean`                   | Check if GPS/location is enabled on device                                               |
| `openLocationSettings()`                     | `Promise<boolean>`          | Prompt user to enable GPS. Resolves `true` if enabled, `false` if not                    |
| `onProviderStatusChange(callback)`           | `void`                      | Register GPS/network provider status callback                                            |
| `isAirplaneModeEnabled()`                    | `boolean`                   | Check if Airplane mode is active on Android                                              |
| `onAirplaneModeChange(callback)`             | `void`                      | Register Airplane mode state-transition callback                                         |
| `getDeviceManufacturer()`                    | `string`                    | Lowercased device manufacturer (e.g. `"xiaomi"`); `"apple"` on iOS                       |
| `isIgnoringBatteryOptimizations()`           | `boolean`                   | Android: is the app exempt from Doze battery optimization? iOS: always `true`            |
| `requestIgnoreBatteryOptimizations()`        | `Promise<boolean>`          | Prompt for battery-optimization exemption; resolves resulting state (iOS resolves `true`) |
| `openOemAutoStartSettings()`                 | `Promise<boolean>`          | Open the OEM auto-start / protected-apps screen; resolves `true` if opened (iOS `false`) |
| `getLocationPermissionStatus()`              | `PermissionStatus`          | Check current location permission without prompting                                      |
| `requestLocationPermission()`                | `Promise<PermissionStatus>` | Request location permission and return the resulting status                              |
| `onPermissionStatusChange(callback)`         | `void`                      | Register a callback that fires when location permission status changes                   |
| `startLiveActivity(orderId, customerName, deliveryAddress, orderCount, status, statusText, estimatedMinutes, distanceMeters)` | `void` | Start a live delivery card — Lock Screen / Dynamic Island (iOS 16.2+) or an ongoing notification (Android) |
| `updateLiveActivity(status, statusText, estimatedMinutes, distanceMeters)` | `void` | Push a state update to the running Live Activity |
| `endLiveActivity()`                          | `void`                      | End and dismiss the Live Activity immediately                                            |
| `showLocalNotification(title, body)`         | `void`                      | Show a local notification                                                                |
| `updateForegroundNotification(title, body)`  | `void`                      | Update the foreground service notification                                               |
| `destroy()`                                  | `void`                      | Stop tracking and disconnect                                                             |

### Utility Exports

| Export                        | Description                                                |
| ----------------------------- | ---------------------------------------------------------- |
| `LocationSmoother`            | Class for smooth map marker animation between updates      |
| `calculateBearing(from, to)`  | Calculate bearing between two coordinates (degrees, 0-360) |
| `shortestRotation(from, to)`  | Calculate shortest rotation path to avoid spinning         |
| `requestLocationPermission()` | Request location + notification permissions (Android)      |
| `isLiveActivitySupported()`   | Returns `true` on iOS, `false` on Android                  |
| `LiveActivityState`           | Type for `{ status, statusText, estimatedMinutes, distanceMeters }` passed to start/update |

### Pure C++ Math Engine

For computationally heavy tasks like array slicing and trip mapping, use the pure C++ Math Engine to bypass the JS thread entirely.

```typescript
import { NitroLocationCalculations } from 'react-native-nitro-location-tracking';

// Instantly compute heavy math directly in C++
const stats = NitroLocationCalculations.calculateTotalTripStats(points);
```

| Method | Returns | Description |
| :--- | :--- | :--- |
| `calculateTotalTripStats(points)` | `TripMathStats` | Instantly computes exact Haversine distance, time, and max/average speed over an array of thousands of points. |
| `filterAnomalousPoints(points, maxSpeedMps)` | `LocationPoint[]` | Cleans an array of points by mathematically stripping out teleportation jumps that exceed the given speed limit. |
| `smoothPath(points, toleranceMeters)` | `LocationPoint[]` | Simplifies a highly dense GPS path into perfect drawing lines using the Ramer-Douglas-Peucker algorithm. |
| `calculateBearing(lat1, lon1, lat2, lon2)` | `number` | Lightning fast C++ trigonometric bearing computation for raw coordinates. |
| `encodeGeohash(lat, lon, precision)` | `string` | Converts coordinates into a Geohash string instantly representing geological boundaries. |

### What Gets Published

The `files` field in `package.json` controls what is included in the npm package:

- `src/` - TypeScript source
- `lib/` - Compiled JS + type declarations
- `android/` - Android native code (excluding build artifacts)
- `ios/` - iOS native code (excluding build folder)
- `cpp/` - C++ shared code
- `nitrogen/` - Nitro generated code
- `nitro.json` - Nitro module configuration
- `*.podspec` - CocoaPods spec
- `react-native.config.js` - React Native CLI config

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)

---

<p align="center">
  <a href="https://www.buymeacoffee.com/alilion" target="_blank">
    <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="60" width="217">
  </a>
</p>