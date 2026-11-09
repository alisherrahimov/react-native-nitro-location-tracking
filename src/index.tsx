import { Platform } from 'react-native';
import { NitroModules } from 'react-native-nitro-modules';
import type { NitroLocationTracking } from './NitroLocationTracking.nitro';
import { createLiveActivityController } from './LiveActivity';

const NitroLocationModule =
  NitroModules.createHybridObject<NitroLocationTracking>(
    'NitroLocationTracking'
  );

export const NitroLocationCalculations = NitroModules.createHybridObject<
  import('./NitroLocationComplexLogicsCalculation.nitro').NitroLocationComplexLogicsCalculation
>('NitroLocationComplexLogicsCalculation');

export default NitroLocationModule;

/**
 * Fully dynamic Live Activity (iOS Lock Screen / Dynamic Island, Android
 * ongoing notification). Every text, icon, color, countdown and deep link is
 * controlled from JS — see `LiveActivityContent`.
 */
export const LiveActivity = createLiveActivityController(NitroLocationModule);

/**
 * true on iOS 16.2+ only. Use before the legacy positional API
 * (startLiveActivity / updateLiveActivity / endLiveActivity).
 * For the dynamic API prefer `LiveActivity.isSupported()`.
 */
export function isLiveActivitySupported(): boolean {
  return Platform.OS === 'ios' && NitroLocationModule.isLiveActivitySupported();
}

/** @deprecated Legacy state for startLiveActivity / updateLiveActivity. Use `LiveActivityContent`. */
export type LiveActivityState = {
  status: 'picking_up' | 'on_the_way' | 'arriving' | 'delivered';
  statusText: string;
  estimatedMinutes: number;
  distanceMeters: number;
};

export { createLiveActivityController } from './LiveActivity';
export type {
  LiveActivityController,
  LiveActivityEndOptions,
} from './LiveActivity';
export { calculateBearing, shortestRotation } from './bearing';
export { LocationSmoother } from './LocationSmoother';
export { SnapToRoad } from './SnapToRoad';
export type {
  SnapPoint,
  SnapToRoadOptions,
  SnapToRoadProvider,
} from './SnapToRoad';
export type {
  CellularGeneration,
  ConnectionConfig,
  ConnectionState,
  ConnectionStateCallback,
  EtaResult,
  GeofenceCallback,
  GeofenceEvent,
  GeofenceRegion,
  LiveActivityContent,
  LiveActivityIcon,
  LiveActivityIconType,
  LiveActivitySlot,
  LivePushConfig,
  LivePushResult,
  LivePushResultCallback,
  LocationConfig,
  LocationData,
  LocationProviderStatus,
  MessageCallback,
  MockLocationCallback,
  NetworkChangeCallback,
  NetworkStatus,
  NetworkTransport,
  NitroLocationTracking,
  PermissionStatus,
  PermissionStatusCallback,
  ProviderStatusCallback,
  SpeedAlertCallback,
  SpeedAlertType,
  SpeedConfig,
  TripStats,
} from './NitroLocationTracking.nitro';
export { requestLocationPermission } from './requestPermission';

export type {
  LocationPoint,
  NitroLocationComplexLogicsCalculation,
  TripMathStats,
} from './NitroLocationComplexLogicsCalculation.nitro';
