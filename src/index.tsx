import { Platform } from 'react-native';
import { NitroModules } from 'react-native-nitro-modules';
import type { NitroLocationTracking } from './NitroLocationTracking.nitro';

const NitroLocationModule =
  NitroModules.createHybridObject<NitroLocationTracking>(
    'NitroLocationTracking'
  );

export const NitroLocationCalculations = NitroModules.createHybridObject<
  import('./NitroLocationComplexLogicsCalculation.nitro').NitroLocationComplexLogicsCalculation
>('NitroLocationComplexLogicsCalculation');

export default NitroLocationModule;

// Live Activities are an iOS-only feature (Android calls are safe no-ops).
// Use this guard before calling startLiveActivity / updateLiveActivity / endLiveActivity.
export function isLiveActivitySupported(): boolean {
  return Platform.OS === 'ios';
}

// Convenience type for the mutable state passed to startLiveActivity / updateLiveActivity.
export type LiveActivityState = {
  status: 'picking_up' | 'on_the_way' | 'arriving' | 'delivered';
  statusText: string;
  estimatedMinutes: number;
  distanceMeters: number;
};

export { calculateBearing, shortestRotation } from './bearing';
export { LocationSmoother } from './LocationSmoother';
export type {
  ConnectionConfig,
  ConnectionState,
  ConnectionStateCallback,
  GeofenceCallback,
  GeofenceEvent,
  GeofenceRegion,
  LivePushConfig,
  LivePushResult,
  LivePushResultCallback,
  LocationConfig,
  LocationData,
  LocationProviderStatus,
  MessageCallback,
  MockLocationCallback,
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
