---
sidebar_position: 1
---

# Types

## `LocationData`

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

## `LocationConfig`

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

See [Adaptive accuracy & the motion engine](./motion-engine) for how
`adaptiveAccuracy`, `kalmanFilter`, and `accuracyFilter` behave.

## `ConnectionConfig`

```ts
interface ConnectionConfig {
  wsUrl: string;
  authToken: string;
  reconnectIntervalMs: number;
  maxReconnectAttempts: number;
}
```

## `LivePushConfig`

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
  // Opt-in durable offline queue (default false). See the Live Push guide.
  persistQueue?: boolean;
  // Fixes per POST when draining the queue (default 1). >1 → body is a JSON array.
  batchSize?: number;
  // Flush a partial batch after this many ms (default 0 = flush immediately).
  batchMaxDelayMs?: number;
  // Cap on queued fixes (default 10000); oldest dropped beyond this.
  maxQueueSize?: number;
}
```

## `LivePushResult`

```ts
interface LivePushResult {
  ok: boolean; // true on a 2xx response
  statusCode: number; // HTTP status code, or 0 for a network error / timeout
  error: string; // '' on success, else a short message ("timeout", "401", …)
}
```

## `GeofenceRegion`

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

## `SpeedConfig`

```ts
interface SpeedConfig {
  maxSpeedKmh: number; // speed limit in km/h
  minSpeedKmh: number; // minimum speed threshold
  checkIntervalMs: number; // how often to evaluate
}
```

## `TripStats`

```ts
interface TripStats {
  distanceMeters: number;
  durationMs: number;
  averageSpeedKmh: number;
  maxSpeedKmh: number;
  pointCount: number;
}
```

## `PermissionStatus`

```ts
type PermissionStatus =
  | 'notDetermined'
  | 'denied'
  | 'restricted'
  | 'whenInUse'
  | 'always';
```

## `TrackingStartResult`

```ts
type TrackingStartResult =
  | 'started'
  | 'appBackgrounded'
  | 'permissionDenied'
  | 'engineRefused'
  | 'notConfigured';
```

Returned by `startTracking()`. Every value other than `'started'` means no
tracking session and no foreground service were started, so the call is safe to
retry.

| Value | Meaning |
| --- | --- |
| `'started'` | Tracking is running. |
| `'appBackgrounded'` | Android only. The app holds no visible activity, so a `location` foreground service cannot be started. Retry once the app is foreground. |
| `'permissionDenied'` | Location permission is denied or restricted. `onPermissionStatusChange` also fires. |
| `'engineRefused'` | The platform location engine would not start (provider disabled, or the foreground service was rejected). |
| `'notConfigured'` | `configure()` was never called, or native components could not initialise. |

`'appBackgrounded'` is a deliberate refusal, not a failure. `startForegroundService()`
arms a ~10s OS watchdog that only a successful `startForeground()` disarms, and a
process that is cached and then frozen (Android 14+ Cached Apps Freezer) cannot run
`Service.onCreate` inside that window — the resulting
`ForegroundServiceDidNotStartInTimeException` is fatal and cannot be caught. Android
12+ forbids the background start outright as well. iOS has no foreground service and
always returns `'started'`.

See the [Location Tracking guide](../guides/location-tracking) for the retry pattern.

## `NetworkStatus`

```ts
interface NetworkStatus {
  transport: 'cellular' | 'wifi' | 'ethernet' | 'other' | 'none';
  generation: 'gen2' | 'gen3' | 'gen4' | 'gen5' | 'unknown';
  radioTechnology: string;
  isExpensive: boolean;
  isConstrained: boolean;
}
```

See the [Network Monitoring guide](../guides/network-monitoring) for usage.
