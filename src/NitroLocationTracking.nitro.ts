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

export type LocationCallback = (location: LocationData) => void;
export type ConnectionStateCallback = (state: ConnectionState) => void;
export type MessageCallback = (message: string) => void;

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

  // === Sync Control ===
  forceSync(): Promise<boolean>;

  // === Fake GPS Detection ===
  isFakeGpsEnabled(): boolean;
  setRejectMockLocations(reject: boolean): void;

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

  // === Notifications ===
  showLocalNotification(title: string, body: string): void;
  updateForegroundNotification(title: string, body: string): void;

  // === Lifecycle ===
  destroy(): void;
}
