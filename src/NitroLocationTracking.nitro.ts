import type { HybridObject } from 'react-native-nitro-modules';

// ─── Enums ───────────────────────────────────────────

export type AccuracyLevel = 'high' | 'balanced' | 'low';
export type ConnectionState = 'connected' | 'disconnected' | 'reconnecting';

// ─── Types ───────────────────────────────────────────

export interface LocationData {
  latitude: number;
  longitude: number;
  altitude: number;
  speed: number; // m/s
  bearing: number; // degrees
  accuracy: number; // meters
  timestamp: number; // unix ms
  isMockLocation?: boolean; // true when from a mock provider
}

export interface LocationConfig {
  desiredAccuracy: AccuracyLevel;
  distanceFilter: number; // meters
  intervalMs: number; // Android only
  fastestIntervalMs: number; // Android only
  stopTimeout: number; // minutes before declaring stopped
  stopOnTerminate: boolean; // keep tracking after app close (Android)
  startOnBoot: boolean; // restart tracking after reboot (Android)
  foregroundNotificationTitle: string;
  foregroundNotificationText: string;
  /**
   * Opt-in adaptive accuracy (default: false). When true, the library downgrades
   * GPS to low power while the device is stationary — as reported by the motion
   * engine — and restores `desiredAccuracy` when it starts moving again, saving
   * battery on long idle stretches.
   *
   * The motion engine prefers OS activity recognition (ACTIVITY_RECOGNITION on
   * Android, Motion & Fitness on iOS); if that permission is unavailable it
   * falls back to speed-based motion detection from the location stream. The
   * `stopTimeout` value is used as the debounce before declaring the device
   * stationary.
   */
  adaptiveAccuracy?: boolean;
  /**
   * Opt-in native Kalman smoothing of the live location stream (default: false).
   * When true, each fix is run through a position Kalman filter before it reaches
   * JS / Live Push / trip stats, so GPS jitter (the "dancing" dot while standing
   * still) and outlier spikes are suppressed. The filter weights each fix by its
   * reported `accuracy`, so inaccurate fixes move the estimate less.
   *
   * NOTE: this changes the emitted `latitude` / `longitude` — they become the
   * filtered estimate, not the raw chip output.
   */
  kalmanFilter?: boolean;
  /**
   * Process noise for the Kalman filter, in meters/second (default 1.0). Roughly
   * "how fast the device is expected to move between fixes" — higher trusts new
   * fixes more (less smoothing, more responsive), lower trusts the model more
   * (smoother, laggier). Only used when `kalmanFilter` is true.
   */
  kalmanProcessNoiseMps?: number;
  /**
   * Drop fixes whose reported horizontal `accuracy` is worse than this many
   * meters, before they reach JS or any downstream consumer (default 0 =
   * disabled). A cheap gate that discards low-confidence fixes outright; combine
   * with `kalmanFilter` for smoothing on top.
   */
  accuracyFilter?: number;
}

export interface ConnectionConfig {
  wsUrl: string;
  authToken: string;
  reconnectIntervalMs: number;
  maxReconnectAttempts: number;
}

/**
 * Config for the native live pusher: a per-fix HTTP POST that runs on the
 * native thread so it keeps firing while the JS thread is suspended (screen
 * off / backgrounded / Doze). Generic by design — the lib owns transport, the
 * app owns schema.
 */
export interface LivePushConfig {
  url: string; // full endpoint, e.g. https://api.example.com/tracking
  authToken: string; // sent as `Authorization: Bearer <authToken>`
  /**
   * JSON object string merged into every POST body alongside the location
   * fields, e.g. '{"courier_id":"c1","delivery_id":null,"active":true}'.
   * A string (not a typed map) so values may be string / number / bool / null.
   */
  extraFieldsJson: string;
  /**
   * false → body carries { latitude, longitude, timestamp } only.
   * true  → also include speed, bearing, accuracy, altitude.
   */
  includeFullPoint: boolean;
  /**
   * Opt-in durable offline queue (default: false). When true, every fix is
   * written to a native SQLite queue and a background drainer POSTs it,
   * deleting each row only on a 2xx response and retrying with backoff on
   * failure. The queue survives app kill / reboot, so fixes captured with no
   * connectivity are delivered later — the production reliability floor.
   *
   * When false (default) Live Push stays fire-and-forget: one POST per fix,
   * dropped on failure. Existing integrations are unaffected.
   */
  persistQueue?: boolean;
  /**
   * Fixes per POST when draining the durable queue (default: 1). Only applies
   * when `persistQueue` is true.
   *
   * - `1` (or unset): body is a single JSON object, exactly as fire-and-forget.
   * - `> 1`: body is a JSON **array** of those per-fix objects, cutting request
   *   volume on metered / cheap data plans. Your endpoint must accept an array.
   */
  batchSize?: number;
  /**
   * When batching (`batchSize > 1`), flush a partial batch after this many
   * milliseconds even if it hasn't filled (default: 0 = flush as soon as any
   * fix is queued). Bounds worst-case delivery latency.
   */
  batchMaxDelayMs?: number;
  /**
   * Hard cap on queued fixes (default: 10000). When exceeded, the oldest rows
   * are dropped so the DB can't grow without bound during long outages. Only
   * applies when `persistQueue` is true.
   */
  maxQueueSize?: number;
}

/**
 * Outcome of a single native Live Push POST, delivered to the
 * `onLivePushResult` callback.
 *
 * Results are delivered live while the JS thread is alive (app foregrounded).
 * Outcomes that occur while JS is suspended (screen off / backgrounded / Doze)
 * are held in a small native ring buffer and replayed in order the next time a
 * callback is registered / JS resumes, so a "last sync OK / 401" indicator stays
 * reliable across suspension. The buffer is bounded (most-recent outcomes win),
 * so this is foreground observability, not a guaranteed per-fix audit log — for
 * guaranteed delivery, enable `persistQueue`.
 */
export interface LivePushResult {
  ok: boolean; // true on a 2xx response
  statusCode: number; // HTTP status code, or 0 for a network error / timeout
  error: string; // '' on success, else a short message ("timeout", "401", …)
}

export type LivePushResultCallback = (result: LivePushResult) => void;

export type LocationCallback = (location: LocationData) => void;
export type ConnectionStateCallback = (state: ConnectionState) => void;
export type MessageCallback = (message: string) => void;
export type MockLocationCallback = (isMockEnabled: boolean) => void;

export interface GeofenceRegion {
  id: string;
  latitude: number;
  longitude: number;
  radius: number; // meters
  notifyOnEntry: boolean;
  notifyOnExit: boolean;
  /**
   * Fire a `dwell` event when the device stays inside the region for
   * `loiteringDelayMs` (default false). Useful for "arrived and waiting"
   * detection (e.g. courier at the pickup point).
   */
  notifyOnDwell?: boolean;
  /**
   * How long (ms) the device must remain inside before a `dwell` event fires
   * (default 300000 = 5 min). Only used when `notifyOnDwell` is true.
   */
  loiteringDelayMs?: number;
}

export type GeofenceEvent = 'enter' | 'exit' | 'dwell';
export type GeofenceCallback = (event: GeofenceEvent, regionId: string) => void;

export interface SpeedConfig {
  maxSpeedKmh: number; // speed limit in km/h
  minSpeedKmh: number; // minimum speed threshold
  checkIntervalMs: number; // how often to evaluate
}

export type SpeedAlertType = 'exceeded' | 'normalized' | 'below_minimum';
export type SpeedAlertCallback = (
  alert: SpeedAlertType,
  currentSpeedKmh: number
) => void;

export interface TripStats {
  distanceMeters: number; // total distance traveled
  durationMs: number; // elapsed time since start
  averageSpeedKmh: number; // average speed
  maxSpeedKmh: number; // peak speed recorded
  pointCount: number; // number of location samples
}

/**
 * Result of `getEtaTo`. `etaSeconds` is `-1` when an ETA can't be estimated
 * (no known location, or the device is effectively stationary so speed is
 * unreliable). `distanceMeters` is `-1` only when there is no known location.
 */
export interface EtaResult {
  distanceMeters: number; // straight-line meters from last known location to target
  etaSeconds: number; // seconds at current speed, or -1 if not estimable
}

export type LocationProviderStatus = 'enabled' | 'disabled';
export type ProviderStatusCallback = (
  gps: LocationProviderStatus,
  network: LocationProviderStatus
) => void;

export type PermissionStatus =
  | 'notDetermined'
  | 'denied'
  | 'restricted'
  | 'whenInUse'
  | 'always';

export type PermissionStatusCallback = (status: PermissionStatus) => void;

// ─── Hybrid Object ──────────────────────────────────

export interface NitroLocationTracking
  extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {
  // === Location Engine ===
  configure(config: LocationConfig): void;
  startTracking(): void;
  stopTracking(): void;
  getCurrentLocation(): Promise<LocationData>;
  isTracking(): boolean;

  onLocation(callback: LocationCallback): void;
  onMotionChange(callback: (isMoving: boolean) => void): void;

  // === Connection Manager ===
  configureConnection(config: ConnectionConfig): void;
  connectWebSocket(): void;
  disconnectWebSocket(): void;
  sendMessage(message: string): void;
  getConnectionState(): ConnectionState;

  onConnectionStateChange(callback: ConnectionStateCallback): void;
  onMessage(callback: MessageCallback): void;

  // === Live Push (per-fix native POST, survives JS suspension) ===
  /** Set endpoint + token + body fields. Call on login / token refresh / new delivery. */
  configureLivePush(config: LivePushConfig): void;
  /** Cheap runtime on/off without losing config. Call on duty/online state changes. */
  setLivePushEnabled(enabled: boolean): void;
  /** Wipe config and disable. Call on logout. */
  clearLivePush(): void;
  /**
   * Observe the outcome of each native Live Push POST. Delivered live while the
   * JS thread is alive (app foregrounded); outcomes that occur during JS
   * suspension are held in a bounded native ring buffer and replayed in order
   * the next time a callback is registered / JS resumes. See `LivePushResult`.
   */
  onLivePushResult(callback: LivePushResultCallback): void;

  // === Sync Control ===
  /**
   * Drain the durable Live Push queue now (when `persistQueue` is enabled).
   * Resolves `true` if the queue is empty afterwards (all fixes delivered),
   * `false` if fixes remain (e.g. still offline). No-op resolving `true` when
   * the queue is disabled.
   */
  forceSync(): Promise<boolean>;
  /** Number of fixes currently waiting in the durable queue (0 when disabled). */
  getQueuedCount(): number;

  // === Fake GPS Detection ===
  isFakeGpsEnabled(): boolean;
  setRejectMockLocations(reject: boolean): void;
  onMockLocationDetected(callback: MockLocationCallback): void;

  // === Geofencing ===
  addGeofence(region: GeofenceRegion): void;
  removeGeofence(regionId: string): void;
  removeAllGeofences(): void;
  onGeofenceEvent(callback: GeofenceCallback): void;

  // === Speed Monitoring ===
  configureSpeedMonitor(config: SpeedConfig): void;
  onSpeedAlert(callback: SpeedAlertCallback): void;
  getCurrentSpeed(): number;

  // === Distance Calculator ===
  startTripCalculation(): void;
  stopTripCalculation(): TripStats;
  getTripStats(): TripStats;
  resetTripCalculation(): void;

  // === Location Provider Status ===
  isLocationServicesEnabled(): boolean;
  onProviderStatusChange(callback: ProviderStatusCallback): void;

  // === Permission Status ===
  getLocationPermissionStatus(): PermissionStatus;
  requestLocationPermission(): Promise<PermissionStatus>;
  onPermissionStatusChange(callback: PermissionStatusCallback): void;
  /**
   * Prompt the user to enable device location (GPS).
   *
   * On Android this shows the native Google Play Services in-app resolution
   * dialog ("For better experience, turn on device location…"). The promise
   * resolves `true` if GPS is enabled (either because it already was, or
   * because the user accepted the dialog), and `false` if the user declined
   * or the dialog could not be shown.
   *
   * On iOS there is no in-app dialog for enabling location services, so this
   * opens the app's Settings page. The promise resolves after the app
   * returns to foreground: `true` if `locationServicesEnabled()` is now on,
   * `false` otherwise.
   */
  openLocationSettings(): Promise<boolean>;

  // === Device State Monitoring ===
  isAirplaneModeEnabled(): boolean;
  onAirplaneModeChange(callback: (isEnabled: boolean) => void): void;

  // === Background execution / battery optimization ===
  // On many Android OEMs (Xiaomi/MIUI, Huawei/EMUI, Oppo/ColorOS, Vivo, Samsung)
  // the system kills background services unless the app is whitelisted from
  // battery optimization AND allowed to auto-start. Without this, background
  // location tracking silently stops. These helpers let you detect and prompt
  // for the needed exemptions. All are Android-only; iOS has no equivalent and
  // returns the safe default noted per method.

  /** The device manufacturer, lowercased (e.g. `"xiaomi"`). iOS returns `"apple"`. */
  getDeviceManufacturer(): string;

  /**
   * Whether the app is currently exempt from Doze / battery optimization.
   * Android: reflects `PowerManager.isIgnoringBatteryOptimizations`. iOS: always
   * `true` (no such restriction).
   */
  isIgnoringBatteryOptimizations(): boolean;

  /**
   * Ask the OS to exempt the app from battery optimization. If the host app
   * declares the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission, this shows
   * the one-tap system dialog; otherwise it opens the battery-optimization
   * settings list (no special permission required). Resolves with the resulting
   * `isIgnoringBatteryOptimizations()` state after the user returns. iOS: no-op
   * that resolves `true`.
   *
   * NOTE: Google Play restricts `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to apps
   * with a qualifying background use case (background location qualifies) and
   * requires a Play Console declaration. The library does NOT bundle it — add it
   * to your app manifest if you want the one-tap dialog.
   */
  requestIgnoreBatteryOptimizations(): Promise<boolean>;

  /**
   * Open the OEM-specific auto-start / protected-apps settings screen (Xiaomi,
   * Huawei, Oppo, Vivo, Samsung, …) so the user can allow the app to run in the
   * background. Falls back to the app's system details page when no known OEM
   * screen exists. Resolves `true` if a settings screen was opened. iOS: no-op
   * that resolves `false`.
   */
  openOemAutoStartSettings(): Promise<boolean>;

  // === Distance Utilities ===
  getDistanceBetween(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
  ): number; // meters
  getDistanceToGeofence(regionId: string): number; // meters from last known location to geofence center, -1 if not found

  // === Odometer (persisted across app kill / reboot) ===
  /** Total meters traveled while tracking. Persisted; survives app kill / reboot. */
  getOdometer(): number;
  /** Reset the persisted odometer back to 0. */
  resetOdometer(): void;

  // === ETA ===
  /** Straight-line distance + ETA from the last known location to a target coordinate. */
  getEtaTo(latitude: number, longitude: number): EtaResult;

  // === Notifications ===
  showLocalNotification(title: string, body: string): void;
  updateForegroundNotification(title: string, body: string): void;

  // === Live Activity (iOS Dynamic Island / Lock Screen) ===
  startLiveActivity(
    orderId: string,
    customerName: string,
    deliveryAddress: string,
    orderCount: number,
    status: string,
    statusText: string,
    estimatedMinutes: number,
    distanceMeters: number
  ): void;
  updateLiveActivity(
    status: string,
    statusText: string,
    estimatedMinutes: number,
    distanceMeters: number
  ): void;
  endLiveActivity(): void;

  // === Lifecycle ===
  destroy(): void;
}
