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
}

export interface ConnectionConfig {
  wsUrl: string;
  restUrl: string;
  authToken: string;
  reconnectIntervalMs: number;
  maxReconnectAttempts: number;
  batchSize: number; // locations per batch upload
  syncIntervalMs: number; // how often to flush queue
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
}

/**
 * Outcome of a single native Live Push POST, delivered to the
 * `onLivePushResult` callback.
 *
 * IMPORTANT: this only reaches JS while the JS thread is alive (app
 * foregrounded). The POST itself still fires while JS is suspended (screen
 * off / backgrounded / Doze), but results that occur during suspension are
 * dropped — they are NOT buffered. Use this for foreground observability
 * (debug overlay, "last sync OK" indicator, surfacing a 401 to re-auth), not
 * as a guaranteed delivery-confirmation channel.
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
}

export type GeofenceEvent = 'enter' | 'exit';
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
   * Observe the outcome of each native Live Push POST. Fires only while the JS
   * thread is alive (app foregrounded); results during JS suspension are
   * dropped, not buffered. See `LivePushResult`.
   */
  onLivePushResult(callback: LivePushResultCallback): void;

  // === Sync Control ===
  forceSync(): Promise<boolean>;

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

  // === Distance Utilities ===
  getDistanceBetween(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
  ): number; // meters
  getDistanceToGeofence(regionId: string): number; // meters from last known location to geofence center, -1 if not found

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
