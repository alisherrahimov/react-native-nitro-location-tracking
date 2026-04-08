import { useState, useEffect, useCallback, useRef } from 'react';
import { NitroModules } from 'react-native-nitro-modules';
import type {
  NitroLocationTracking,
  LocationData,
  LocationConfig,
  ConnectionConfig,
} from './NitroLocationTracking.nitro';

const NitroLocationModule =
  NitroModules.createHybridObject<NitroLocationTracking>(
    'NitroLocationTracking'
  );

export default NitroLocationModule;
export { requestLocationPermission } from './requestPermission';
export { LocationSmoother } from './LocationSmoother';
export { shortestRotation, calculateBearing } from './bearing';
// export * from './db'
export type {
  NitroLocationTracking,
  LocationData,
  LocationConfig,
  ConnectionConfig,
  GeofenceRegion,
  GeofenceEvent,
  GeofenceCallback,
  SpeedConfig,
  SpeedAlertType,
  SpeedAlertCallback,
  TripStats,
  LocationProviderStatus,
  ProviderStatusCallback,
  PermissionStatus,
  PermissionStatusCallback,
  MockLocationCallback,
} from './NitroLocationTracking.nitro';

export function useDriverLocation(config: LocationConfig) {
  const [location, setLocation] = useState<LocationData | null>(null);
  const [isMoving, setIsMoving] = useState(false);
  const [isTracking, setIsTracking] = useState(false);

  // Stabilize config by value so the effect doesn't re-run on every render
  const configJson = JSON.stringify(config);

  // Keep a ref to track whether we started, so cleanup only stops if we did
  const trackingRef = useRef(false);

  useEffect(() => {
    const parsed = JSON.parse(configJson) as LocationConfig;
    NitroLocationModule.configure(parsed);
    NitroLocationModule.onLocation(setLocation);
    NitroLocationModule.onMotionChange(setIsMoving);
    return () => {
      if (trackingRef.current) {
        NitroLocationModule.stopTracking();
        trackingRef.current = false;
      }
    };
  }, [configJson]);

  return {
    location,
    isMoving,
    isTracking,
    startTracking: useCallback(() => {
      NitroLocationModule.startTracking();
      trackingRef.current = true;
      setIsTracking(true);
    }, []),
    stopTracking: useCallback(() => {
      NitroLocationModule.stopTracking();
      trackingRef.current = false;
      setIsTracking(false);
    }, []),
  };
}

export function useRideConnection(config: ConnectionConfig) {
  const [connectionState, setConnectionState] = useState<
    'connected' | 'disconnected' | 'reconnecting'
  >('disconnected');
  const [lastMessage, setLastMessage] = useState<string | null>(null);

  useEffect(() => {
    NitroLocationModule.configureConnection(config);
    NitroLocationModule.onConnectionStateChange(setConnectionState);
    NitroLocationModule.onMessage(setLastMessage);
    return () => {
      NitroLocationModule.disconnectWebSocket();
    };
  }, [config]);

  return {
    connectionState,
    lastMessage,
    connect: useCallback(() => NitroLocationModule.connectWebSocket(), []),
    disconnect: useCallback(
      () => NitroLocationModule.disconnectWebSocket(),
      []
    ),
    send: useCallback((m: string) => NitroLocationModule.sendMessage(m), []),
  };
}
