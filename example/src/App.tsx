import { useEffect, useState, useCallback } from 'react';
import {
  Text,
  View,
  StyleSheet,
  Button,
  ScrollView,
  Alert,
  AppState,
  Linking,
  Switch,
} from 'react-native';
import NitroLocation, {
  useDriverLocation,
  useRideConnection,
} from 'react-native-nitro-location-tracking';
import type {
  GeofenceEvent,
  SpeedAlertType,
  TripStats,
  LocationProviderStatus,
  PermissionStatus,
} from 'react-native-nitro-location-tracking';
import { requestLocationPermission } from '../../src/requestPermission';

// ─── Section Component ────────────────────────────────
function Section({
  title,
  children,
  collapsed: initialCollapsed = true,
}: {
  title: string;
  children: React.ReactNode;
  collapsed?: boolean;
}) {
  const [collapsed, setCollapsed] = useState(initialCollapsed);
  return (
    <View style={styles.card}>
      <Button
        title={`${collapsed ? '▶' : '▼'} ${title}`}
        onPress={() => setCollapsed((c) => !c)}
      />
      {!collapsed && <View style={styles.sectionBody}>{children}</View>}
    </View>
  );
}

// ─── Main App ─────────────────────────────────────────
export default function App() {
  // ── Location Tracking ────────────────────────────────
  const { location, isMoving, isTracking, startTracking, stopTracking } =
    useDriverLocation({
      desiredAccuracy: 'high',
      distanceFilter: 0,
      intervalMs: 1000,
      fastestIntervalMs: 1000,
      stopTimeout: 5,
      stopOnTerminate: false,
      startOnBoot: true,
      foregroundNotificationTitle: 'NitroLocation Example',
      foregroundNotificationText: 'Tracking your location...',
    });

  // ── WebSocket Connection ─────────────────────────────
  const { connectionState, lastMessage, connect, disconnect, send } =
    useRideConnection({
      wsUrl: 'wss://echo.websocket.org',
      restUrl: 'https://httpbin.org',
      authToken: 'test-token',
      reconnectIntervalMs: 3000,
      maxReconnectAttempts: 5,
      batchSize: 50,
      syncIntervalMs: 100,
    });

  // ── Geofencing State ─────────────────────────────────
  const [geofenceLogs, setGeofenceLogs] = useState<string[]>([]);
  const [geofenceAdded, setGeofenceAdded] = useState(false);

  // ── Speed Monitoring State ───────────────────────────
  const [speedAlerts, setSpeedAlerts] = useState<string[]>([]);
  const [speedMonitorActive, setSpeedMonitorActive] = useState(false);
  const [currentSpeed, setCurrentSpeed] = useState<number>(0);

  // ── Trip Calculator State ────────────────────────────
  const [tripActive, setTripActive] = useState(false);
  const [tripStats, setTripStats] = useState<TripStats | null>(null);

  // ── Fake GPS State ───────────────────────────────────
  const [rejectMock, setRejectMock] = useState(false);

  // ── Provider Status State ────────────────────────────
  const [providerStatus, setProviderStatus] = useState<{
    gps: LocationProviderStatus;
    network: LocationProviderStatus;
  } | null>(null);

  // ── Permission Status State ──────────────────────────
  const [permissionStatus, setPermissionStatus] =
    useState<PermissionStatus | null>(null);

  // ═══ Effects ═══

  // Reconnect WebSocket when app comes to foreground
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') connect();
    });
    return () => sub.remove();
  }, [connect]);

  // Log location updates + live-update speed display
  useEffect(() => {
    if (!location) return;
    console.log(
      `[Location] ${location.latitude.toFixed(6)}, ${location.longitude.toFixed(
        6
      )} | speed: ${location.speed.toFixed(
        1
      )} m/s | bearing: ${location.bearing.toFixed(0)}°`
    );
    // Keep speed display in sync with every location update (m/s → km/h)
    setCurrentSpeed(location.speed * 3.6);
  }, [location]);

  // Handle incoming messages
  useEffect(() => {
    if (!lastMessage) return;
    console.log('[WS Message]', lastMessage);
  }, [lastMessage]);

  // Register geofence event listener
  useEffect(() => {
    NitroLocation.onGeofenceEvent((event: GeofenceEvent, regionId: string) => {
      const msg = `[${new Date().toLocaleTimeString()}] ${event.toUpperCase()} → ${regionId}`;
      console.log('[Geofence]', msg);
      setGeofenceLogs((prev) => [msg, ...prev].slice(0, 20));
    });
  }, []);

  // Register speed alert listener
  useEffect(() => {
    NitroLocation.onSpeedAlert((alert: SpeedAlertType, speedKmh: number) => {
      const msg = `[${new Date().toLocaleTimeString()}] ${alert} @ ${speedKmh.toFixed(
        1
      )} km/h`;
      console.log('[Speed Alert]', msg);
      setSpeedAlerts((prev) => [msg, ...prev].slice(0, 20));
    });
  }, []);

  // Register provider status listener
  useEffect(() => {
    NitroLocation.onProviderStatusChange(
      (gps: LocationProviderStatus, network: LocationProviderStatus) => {
        console.log(`[Provider] GPS: ${gps}, Network: ${network}`);
        setProviderStatus({ gps, network });
      }
    );
  }, []);

  // ═══ Handlers ═══

  const handleGetCurrentLocation = async () => {
    try {
      const loc = await NitroLocation.getCurrentLocation();
      Alert.alert(
        'Current Location',
        `Lat: ${loc.latitude.toFixed(6)}\nLng: ${loc.longitude.toFixed(
          6
        )}\nAccuracy: ${loc.accuracy.toFixed(0)}m\nMock: ${
          loc.isMockLocation ?? 'N/A'
        }`
      );
    } catch (e) {
      Alert.alert('Error', String(e));
    }
  };

  const handleForceSync = async () => {
    const result = await NitroLocation.forceSync();
    Alert.alert('Sync', result ? 'Sync successful' : 'Sync failed');
  };

  const handleShowNotification = () => {
    NitroLocation.showLocalNotification(
      'Test Notification',
      'This is a test from NitroLocation!'
    );
  };

  const handleUpdateForegroundNotification = () => {
    NitroLocation.updateForegroundNotification(
      'Updated Title',
      `Updated at ${new Date().toLocaleTimeString()}`
    );
    Alert.alert('Done', 'Foreground notification updated');
  };

  // ── Geofencing Handlers ──────────────────────────────

  const handleAddGeofence = useCallback(() => {
    const lat = location?.latitude ?? 41.2995;
    const lng = location?.longitude ?? 69.2401;

    NitroLocation.addGeofence({
      id: 'test-zone-1',
      latitude: lat,
      longitude: lng,
      radius: 200,
      notifyOnEntry: true,
      notifyOnExit: true,
    });

    NitroLocation.addGeofence({
      id: 'test-zone-2',
      latitude: lat + 0.002,
      longitude: lng + 0.002,
      radius: 100,
      notifyOnEntry: true,
      notifyOnExit: false,
    });

    setGeofenceAdded(true);
    Alert.alert(
      'Geofences Added',
      `Zone 1: ${lat.toFixed(4)}, ${lng.toFixed(4)} (200m)\nZone 2: ${(
        lat + 0.002
      ).toFixed(4)}, ${(lng + 0.002).toFixed(4)} (100m)`
    );
  }, [location]);

  const handleRemoveGeofence = useCallback(() => {
    NitroLocation.removeGeofence('test-zone-1');
    Alert.alert('Removed', 'Geofence test-zone-1 removed');
  }, []);

  const handleRemoveAllGeofences = useCallback(() => {
    NitroLocation.removeAllGeofences();
    setGeofenceAdded(false);
    setGeofenceLogs([]);
    Alert.alert('Cleared', 'All geofences removed');
  }, []);

  // ── Speed Monitoring Handlers ────────────────────────

  const handleStartSpeedMonitor = useCallback(() => {
    NitroLocation.configureSpeedMonitor({
      maxSpeedKmh: 80,
      minSpeedKmh: 5,
      checkIntervalMs: 2000,
    });
    setSpeedMonitorActive(true);
    Alert.alert(
      'Speed Monitor',
      'Configured: max 80 km/h, min 5 km/h, check every 2s'
    );
  }, []);

  const handleGetCurrentSpeed = useCallback(() => {
    const speedKmh = NitroLocation.getCurrentSpeed();
    setCurrentSpeed(speedKmh);
    Alert.alert(
      'Current Speed',
      `${speedKmh.toFixed(1)} km/h (${(speedKmh / 3.6).toFixed(1)} m/s)`
    );
  }, []);

  // ── Trip Calculator Handlers ─────────────────────────

  const handleStartTrip = useCallback(() => {
    NitroLocation.startTripCalculation();
    setTripActive(true);
    setTripStats(null);
    Alert.alert('Trip', 'Trip calculation started');
  }, []);

  const handleStopTrip = useCallback(() => {
    const stats = NitroLocation.stopTripCalculation();
    setTripActive(false);
    setTripStats(stats);
    Alert.alert(
      'Trip Completed',
      `Distance: ${(stats.distanceMeters / 1000).toFixed(2)} km\n` +
        `Duration: ${(stats.durationMs / 60000).toFixed(1)} min\n` +
        `Avg Speed: ${stats.averageSpeedKmh.toFixed(1)} km/h\n` +
        `Max Speed: ${stats.maxSpeedKmh.toFixed(1)} km/h\n` +
        `Points: ${stats.pointCount}`
    );
  }, []);

  const handleGetTripStats = useCallback(() => {
    const stats = NitroLocation.getTripStats();
    setTripStats(stats);
  }, []);

  const handleResetTrip = useCallback(() => {
    NitroLocation.resetTripCalculation();
    setTripActive(false);
    setTripStats(null);
    Alert.alert('Trip', 'Trip calculation reset');
  }, []);

  // ── Fake GPS Handlers ────────────────────────────────

  const handleCheckFakeGps = useCallback(() => {
    const isFake = NitroLocation.isFakeGpsEnabled();
    Alert.alert(
      'Fake GPS',
      isFake ? '⚠️ Mock location detected!' : '✅ No mock location'
    );
  }, []);

  const handleToggleRejectMock = useCallback((value: boolean) => {
    NitroLocation.setRejectMockLocations(value);
    setRejectMock(value);
  }, []);

  // ── Provider & Permission Handlers ───────────────────

  const handleCheckLocationServices = useCallback(() => {
    const enabled = NitroLocation.isLocationServicesEnabled();
    Alert.alert(
      'Location Services',
      enabled
        ? '✅ Location services are enabled'
        : '❌ Location services are disabled'
    );
  }, []);

  const handleCheckPermission = useCallback(() => {
    const status = NitroLocation.getLocationPermissionStatus();
    setPermissionStatus(status);
    Alert.alert('Permission Status', `Current: ${status}`);
  }, []);

  // ── Lifecycle Handler ────────────────────────────────

  const handleDestroy = useCallback(() => {
    Alert.alert(
      'Destroy',
      'Are you sure? This will release all native resources.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Destroy',
          style: 'destructive',
          onPress: () => {
            NitroLocation.destroy();
            Alert.alert('Done', 'NitroLocation module destroyed');
          },
        },
      ]
    );
  }, []);

  // ═══ Render ═══

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>NitroLocation Example</Text>

      {/* ── Location Info ──────────────────────────────── */}
      <Section title="📍 Location" collapsed={false}>
        {location ? (
          <>
            <Text style={styles.mono}>
              {location.latitude.toFixed(6)}, {location.longitude.toFixed(6)}
            </Text>
            <Text>Speed: {location.speed.toFixed(1)} m/s</Text>
            <Text>Bearing: {location.bearing.toFixed(0)}°</Text>
            <Text>Accuracy: {location.accuracy.toFixed(0)}m</Text>
            <Text>Moving: {isMoving ? 'Yes' : 'No'}</Text>
            <Text>Mock: {location.isMockLocation ? '⚠️ YES' : 'No'}</Text>
          </>
        ) : (
          <Text style={styles.muted}>No location yet</Text>
        )}
      </Section>

      {/* ── Tracking Controls ──────────────────────────── */}
      <Section title="🎯 Tracking" collapsed={false}>
        <Text>Status: {isTracking ? '🟢 Active' : '🔴 Stopped'}</Text>
        <View style={styles.row}>
          <Button
            title="Start"
            onPress={async () => {
              const granted = await requestLocationPermission();
              if (granted) startTracking();
            }}
            disabled={isTracking}
          />
          <Button title="Stop" onPress={stopTracking} disabled={!isTracking} />
          <Button title="Get Current" onPress={handleGetCurrentLocation} />
        </View>
      </Section>

      {/* ── WebSocket Connection ───────────────────────── */}
      <Section title="🔌 WebSocket">
        <Text>State: {connectionState}</Text>
        {lastMessage && <Text>Last msg: {lastMessage.slice(0, 50)}</Text>}
        <View style={styles.row}>
          <Button title="Connect" onPress={connect} />
          <Button title="Disconnect" onPress={disconnect} />
          <Button title="Send Ping" onPress={() => send('ping')} />
        </View>
      </Section>

      {/* ── Geofencing ─────────────────────────────────── */}
      <Section title="🗺️ Geofencing">
        <Text>Status: {geofenceAdded ? '🟢 Active' : '⚪ Inactive'}</Text>
        <View style={styles.row}>
          <Button title="Add Zones" onPress={handleAddGeofence} />
          <Button
            title="Remove Zone 1"
            onPress={handleRemoveGeofence}
            disabled={!geofenceAdded}
          />
          <Button title="Remove All" onPress={handleRemoveAllGeofences} />
        </View>
        {geofenceLogs.length > 0 && (
          <View style={styles.logBox}>
            <Text style={styles.logTitle}>Event Log:</Text>
            {geofenceLogs.map((log, i) => (
              <Text key={i} style={styles.logLine}>
                {log}
              </Text>
            ))}
          </View>
        )}
      </Section>

      {/* ── Speed Monitoring ───────────────────────────── */}
      <Section title="🚗 Speed Monitor">
        <Text>Monitor: {speedMonitorActive ? '🟢 Active' : '⚪ Inactive'}</Text>
        <Text>
          Current Speed: {currentSpeed.toFixed(1)} km/h (
          {(currentSpeed / 3.6).toFixed(1)} m/s)
        </Text>
        <View style={styles.row}>
          <Button title="Configure" onPress={handleStartSpeedMonitor} />
          <Button title="Get Speed" onPress={handleGetCurrentSpeed} />
        </View>
        {speedAlerts.length > 0 && (
          <View style={styles.logBox}>
            <Text style={styles.logTitle}>Speed Alerts:</Text>
            {speedAlerts.map((alert, i) => (
              <Text key={i} style={styles.logLine}>
                {alert}
              </Text>
            ))}
          </View>
        )}
      </Section>

      {/* ── Trip / Distance Calculator ─────────────────── */}
      <Section title="📏 Trip Calculator">
        <Text>Trip: {tripActive ? '🟢 Recording' : '⚪ Idle'}</Text>
        <View style={styles.row}>
          <Button
            title="Start Trip"
            onPress={handleStartTrip}
            disabled={tripActive}
          />
          <Button
            title="Stop Trip"
            onPress={handleStopTrip}
            disabled={!tripActive}
          />
          <Button title="Get Stats" onPress={handleGetTripStats} />
          <Button title="Reset" onPress={handleResetTrip} />
        </View>
        {tripStats && (
          <View style={styles.statsBox}>
            <Text style={styles.statLabel}>
              Distance: {(tripStats.distanceMeters / 1000).toFixed(2)} km
            </Text>
            <Text style={styles.statLabel}>
              Duration: {(tripStats.durationMs / 60000).toFixed(1)} min
            </Text>
            <Text style={styles.statLabel}>
              Avg Speed: {tripStats.averageSpeedKmh.toFixed(1)} km/h
            </Text>
            <Text style={styles.statLabel}>
              Max Speed: {tripStats.maxSpeedKmh.toFixed(1)} km/h
            </Text>
            <Text style={styles.statLabel}>Points: {tripStats.pointCount}</Text>
          </View>
        )}
      </Section>

      {/* ── Fake GPS Detection ─────────────────────────── */}
      <Section title="🛡️ Fake GPS Detection">
        <View style={styles.switchRow}>
          <Text>Reject Mock Locations:</Text>
          <Switch value={rejectMock} onValueChange={handleToggleRejectMock} />
        </View>
        <View style={styles.row}>
          <Button title="Check Fake GPS" onPress={handleCheckFakeGps} />
        </View>
      </Section>

      {/* ── Location Provider Status ───────────────────── */}
      <Section title="📡 Provider Status">
        <Button
          title="Check Location Services"
          onPress={handleCheckLocationServices}
        />
        {providerStatus && (
          <View style={styles.statsBox}>
            <Text>
              GPS: {providerStatus.gps === 'enabled' ? '✅' : '❌'}{' '}
              {providerStatus.gps}
            </Text>
            <Text>
              Network: {providerStatus.network === 'enabled' ? '✅' : '❌'}{' '}
              {providerStatus.network}
            </Text>
          </View>
        )}
      </Section>

      {/* ── Permission Status ──────────────────────────── */}
      <Section title="🔐 Permission Status">
        <Button title="Check Permission" onPress={handleCheckPermission} />
        {permissionStatus && (
          <Text style={styles.statusBadge}>Current: {permissionStatus}</Text>
        )}
      </Section>

      {/* ── Notifications ──────────────────────────────── */}
      <Section title="🔔 Notifications">
        <View style={styles.row}>
          <Button title="Show Local" onPress={handleShowNotification} />
          <Button
            title="Update Foreground"
            onPress={handleUpdateForegroundNotification}
          />
        </View>
      </Section>

      {/* ── Sync & Actions ─────────────────────────────── */}
      <Section title="🔄 Sync & Actions">
        <View style={styles.row}>
          <Button title="Force Sync" onPress={handleForceSync} />
          <Button
            title="isTracking?"
            onPress={() => {
              const tracking = NitroLocation.isTracking();
              Alert.alert('isTracking', tracking ? '✅ Yes' : '❌ No');
            }}
          />
          <Button
            title="Connection State"
            onPress={() => {
              const state = NitroLocation.getConnectionState();
              Alert.alert('Connection State', state);
            }}
          />
        </View>
      </Section>

      {/* ── Open Maps (test background) ────────────────── */}
      <Section title="🗺️ Open Maps (test background)">
        <View style={styles.row}>
          <Button
            title="Google Maps"
            onPress={() => {
              const lat = location?.latitude ?? 0;
              const lng = location?.longitude ?? 0;
              Linking.openURL(`https://maps.google.com/?q=${lat},${lng}`);
            }}
          />
          <Button
            title="Yandex Maps"
            onPress={() => {
              const lat = location?.latitude ?? 0;
              const lng = location?.longitude ?? 0;
              Linking.openURL(`https://yandex.com/maps/?pt=${lng},${lat}&z=16`);
            }}
          />
        </View>
      </Section>

      {/* ── Lifecycle ──────────────────────────────────── */}
      <Section title="⚠️ Lifecycle">
        <Text style={styles.muted}>
          Destroy releases all native resources. Use with caution.
        </Text>
        <View style={styles.row}>
          <Button
            title="🗑️ Destroy Module"
            onPress={handleDestroy}
            color="#e74c3c"
          />
        </View>
      </Section>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    padding: 20,
    paddingTop: 60,
    paddingBottom: 40,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: '700',
    textAlign: 'center',
    marginBottom: 20,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  sectionBody: {
    marginTop: 12,
  },
  mono: {
    fontFamily: 'monospace',
    fontSize: 14,
    marginBottom: 4,
  },
  muted: {
    color: '#999',
  },
  row: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 8,
    flexWrap: 'wrap',
  },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  logBox: {
    marginTop: 12,
    padding: 10,
    backgroundColor: '#1e1e1e',
    borderRadius: 8,
    maxHeight: 200,
  },
  logTitle: {
    color: '#4fc3f7',
    fontWeight: '600',
    marginBottom: 4,
  },
  logLine: {
    color: '#e0e0e0',
    fontFamily: 'monospace',
    fontSize: 11,
    lineHeight: 16,
  },
  statsBox: {
    marginTop: 10,
    padding: 12,
    backgroundColor: '#f0f0f0',
    borderRadius: 8,
  },
  statLabel: {
    fontSize: 14,
    lineHeight: 22,
  },
  statusBadge: {
    marginTop: 8,
    fontSize: 14,
    fontWeight: '600',
    color: '#2196f3',
  },
});
