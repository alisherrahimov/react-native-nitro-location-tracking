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

  // === Notifications ===
  showLocalNotification(title: string, body: string): void;
  updateForegroundNotification(title: string, body: string): void;

  // === Lifecycle ===
  destroy(): void;
}
