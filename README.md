# react-native-nitro-location-tracking

A high-performance React Native location tracking library built with [Nitro Modules](https://nitro.margelo.com/). Designed for ride-hailing, delivery, and fleet tracking apps with background location, WebSocket connectivity, foreground service notifications, and smooth map marker animations.

## Features

- **Background location tracking** with foreground service (Android) and background modes (iOS)
- **WebSocket connection manager** with auto-reconnect and batch sync
- **Fake GPS detection** — detect mock locations and optionally reject them
- **Geofencing** — monitor enter/exit events for circular regions
- **Speed Monitoring** — configurable speed alerts with state-transition callbacks
- **Distance Calculator** — running trip stats with Haversine distance
- **Location Provider Status** — detect when GPS/location is turned on/off
- **Smooth map marker animations** via `LocationSmoother`
- **Bearing calculation** utilities for rotation/heading
- **Foreground notifications** (Android foreground service, iOS local notifications)
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
- `RECEIVE_BOOT_COMPLETED`
- `WAKE_LOCK`

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

### Location Tracking with `useDriverLocation`

The simplest way to track location using the built-in React hook:

```tsx
import { useDriverLocation } from 'react-native-nitro-location-tracking';
import type { LocationConfig } from 'react-native-nitro-location-tracking';

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
};

function DriverScreen() {
  const { location, isMoving, isTracking, startTracking, stopTracking } =
    useDriverLocation(config);

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
      <Button title="Start" onPress={startTracking} />
      <Button title="Stop" onPress={stopTracking} />
    </View>
  );
}
```

### WebSocket Connection with `useRideConnection`

Manage a WebSocket connection for real-time location sync:

```tsx
import { useRideConnection } from 'react-native-nitro-location-tracking';
import type { ConnectionConfig } from 'react-native-nitro-location-tracking';

const connectionConfig: ConnectionConfig = {
  wsUrl: 'wss://api.example.com/ws/driver',
  restUrl: 'https://api.example.com/api',
  authToken: 'your-auth-token',
  reconnectIntervalMs: 5000,
  maxReconnectAttempts: 10,
  batchSize: 5, // locations per batch upload
  syncIntervalMs: 10000, // flush queue every 10s
};

function RideScreen() {
  const { connectionState, lastMessage, connect, disconnect, send } =
    useRideConnection(connectionConfig);

  return (
    <View>
      <Text>Connection: {connectionState}</Text>
      <Text>Last message: {lastMessage}</Text>
      <Button title="Connect" onPress={connect} />
      <Button title="Disconnect" onPress={disconnect} />
      <Button title="Send Ping" onPress={() => send('ping')} />
    </View>
  );
}
```

### Direct Module Access

For advanced use cases, access the native module directly:

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

// Configure and start tracking
NitroLocationModule.configure(config);
NitroLocationModule.onLocation((location) => {
  console.log('New location:', location);
});
NitroLocationModule.onMotionChange((isMoving) => {
  console.log('Motion changed:', isMoving);
});
NitroLocationModule.startTracking();

// Get current location (one-shot)
const current = await NitroLocationModule.getCurrentLocation();

// Check tracking state
const tracking = NitroLocationModule.isTracking();

// Stop tracking
NitroLocationModule.stopTracking();

// Force sync queued locations
const synced = await NitroLocationModule.forceSync();

// Notifications
NitroLocationModule.showLocalNotification('Ride Started', 'Heading to pickup');
NitroLocationModule.updateForegroundNotification('En Route', '2.5 km away');

// Cleanup
NitroLocationModule.destroy();
```

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
```

**Platform behavior:**

| Platform | Per-location detection                                         | Device-level detection              |
| -------- | -------------------------------------------------------------- | ----------------------------------- |
| Android  | `Location.isMock` (API 31+) / `isFromMockProvider` (API 18+)   | `AppOpsManager` mock location check |
| iOS      | `CLLocation.sourceInformation.isSimulatedBySoftware` (iOS 15+) | Simulator detection                 |

### Smooth Map Marker Animation

Use `LocationSmoother` to animate a map marker smoothly between location updates:

```tsx
import { useRef, useCallback } from 'react';
import { Marker } from 'react-native-maps';
import {
  useDriverLocation,
  LocationSmoother,
} from 'react-native-nitro-location-tracking';

function MapScreen() {
  const markerRef = useRef(null);
  const smootherRef = useRef(new LocationSmoother(markerRef));

  const { location, startTracking } = useDriverLocation(config);

  // Feed each new location into the smoother
  const onLocation = useCallback((loc) => {
    smootherRef.current.feed(loc);
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

Monitor enter/exit events for circular regions:

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';
import type { GeofenceRegion } from 'react-native-nitro-location-tracking';

// Listen for geofence events
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
});

// Remove a specific geofence
NitroLocationModule.removeGeofence('pickup-zone');

// Remove all geofences
NitroLocationModule.removeAllGeofences();
```

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
}
```

#### `ConnectionConfig`

```ts
interface ConnectionConfig {
  wsUrl: string;
  restUrl: string;
  authToken: string;
  reconnectIntervalMs: number;
  maxReconnectAttempts: number;
  batchSize: number; // locations per batch upload
  syncIntervalMs: number; // how often to flush queue
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
}
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

### Hooks

| Hook                        | Returns                                                           | Description                            |
| --------------------------- | ----------------------------------------------------------------- | -------------------------------------- |
| `useDriverLocation(config)` | `{ location, isMoving, isTracking, startTracking, stopTracking }` | Manages location tracking lifecycle    |
| `useRideConnection(config)` | `{ connectionState, lastMessage, connect, disconnect, send }`     | Manages WebSocket connection lifecycle |

### Native Module Methods

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
| `forceSync()`                                | `Promise<boolean>`          | Flush queued locations to server                                                         |
| `isFakeGpsEnabled()`                         | `boolean`                   | Check if device-level mock location is enabled                                           |
| `setRejectMockLocations(reject)`             | `void`                      | Auto-reject mock locations when `true`                                                   |
| `addGeofence(region)`                        | `void`                      | Start monitoring a circular geofence region                                              |
| `removeGeofence(regionId)`                   | `void`                      | Stop monitoring a specific geofence                                                      |
| `removeAllGeofences()`                       | `void`                      | Remove all active geofences                                                              |
| `onGeofenceEvent(callback)`                  | `void`                      | Register geofence enter/exit callback                                                    |
| `getDistanceBetween(lat1, lon1, lat2, lon2)` | `number`                    | Calculate distance between two points in meters (native Haversine)                       |
| `getDistanceToGeofence(regionId)`            | `number`                    | Get distance in meters from last known location to a geofence center (`-1` if not found) |
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
| `getLocationPermissionStatus()`              | `PermissionStatus`          | Check current location permission without prompting                                      |
| `requestLocationPermission()`                | `Promise<PermissionStatus>` | Request location permission and return the resulting status                              |
| `onPermissionStatusChange(callback)`         | `void`                      | Register a callback that fires when location permission status changes                   |
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

## Publishing to npm

### Prerequisites

1. Create an npm account at [npmjs.com](https://www.npmjs.com/signup)
2. Log in from terminal:

```sh
npm login
```

### First-time Publish

1. Make sure the build is up to date:

```sh
yarn prepare
```

1. Do a dry run to verify what will be published:

```sh
npm pack --dry-run
```

1. Publish:

```sh
npm publish
```

> The package is configured with `"publishConfig": { "registry": "https://registry.npmjs.org/" }` so it will publish to the public npm registry.

### Release with Versioning (Recommended)

This project uses [release-it](https://github.com/release-it/release-it) with conventional changelog. To create a proper release:

```sh
yarn release
```

This will:

- Bump the version based on your commits
- Generate a changelog
- Create a git commit and tag
- Publish to npm
- Create a GitHub release

For a specific version bump:

```sh
# Patch release (0.1.5 -> 0.1.6)
npx release-it patch

# Minor release (0.1.5 -> 0.2.0)
npx release-it minor

# Major release (0.1.5 -> 1.0.0)
npx release-it major
```

### Manual Version Bump + Publish

```sh
# 1. Bump version
npm version patch  # or minor / major

# 2. Build
yarn prepare

# 3. Publish
npm publish

# 4. Push tags
git push --follow-tags
```

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

### Unpublishing / Deprecating

```sh
# Deprecate a version (users see a warning on install)
npm deprecate react-native-nitro-location-tracking@"< 0.2.0" "please upgrade to 0.2.0+"

# Unpublish a specific version (within 72 hours only)
npm unpublish react-native-nitro-location-tracking@0.1.0
```

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
