import { useEffect } from 'react';
import {
  Text,
  View,
  StyleSheet,
  Button,
  ScrollView,
  Alert,
  AppState,
  Linking,
} from 'react-native';
import NitroLocation, {
  useDriverLocation,
  useRideConnection,
} from 'react-native-nitro-location-tracking';
import { requestLocationPermission } from '../../src/requestPermission';

export default function App() {
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

  // Reconnect WebSocket when app comes to foreground
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') connect();
    });
    return () => sub.remove();
  }, [connect]);

  // Log location updates
  useEffect(() => {
    if (!location) return;
    console.log(
      `[Location] ${location.latitude.toFixed(6)}, ${location.longitude.toFixed(
        6
      )} | speed: ${location.speed.toFixed(
        1
      )} m/s | bearing: ${location.bearing.toFixed(0)}°`
    );
  }, [location]);

  // Handle incoming messages
  useEffect(() => {
    if (!lastMessage) return;
    console.log('[WS Message]', lastMessage);
  }, [lastMessage]);

  const handleGetCurrentLocation = async () => {
    try {
      const loc = await NitroLocation.getCurrentLocation();
      Alert.alert(
        'Current Location',
        `Lat: ${loc.latitude.toFixed(6)}\nLng: ${loc.longitude.toFixed(
          6
        )}\nAccuracy: ${loc.accuracy.toFixed(0)}m`
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

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>NitroLocation Example</Text>

      {/* Location Info */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Location</Text>
        {location ? (
          <>
            <Text style={styles.mono}>
              {location.latitude.toFixed(6)}, {location.longitude.toFixed(6)}
            </Text>
            <Text>Speed: {location.speed.toFixed(1)} m/s</Text>
            <Text>Bearing: {location.bearing.toFixed(0)}°</Text>
            <Text>Accuracy: {location.accuracy.toFixed(0)}m</Text>
            <Text>Moving: {isMoving ? 'Yes' : 'No'}</Text>
          </>
        ) : (
          <Text style={styles.muted}>No location yet</Text>
        )}
      </View>

      {/* Tracking Controls */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Tracking</Text>
        <Text>Status: {isTracking ? 'Active' : 'Stopped'}</Text>
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
      </View>

      {/* Connection */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>WebSocket</Text>
        <Text>State: {connectionState}</Text>
        {lastMessage && <Text>Last msg: {lastMessage.slice(0, 50)}</Text>}
        <View style={styles.row}>
          <Button title="Connect" onPress={connect} />
          <Button title="Disconnect" onPress={disconnect} />
          <Button title="Send Ping" onPress={() => send('ping')} />
        </View>
      </View>

      {/* Actions */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Actions</Text>
        <View style={styles.row}>
          <Button title="Force Sync" onPress={handleForceSync} />
          <Button title="Notification" onPress={handleShowNotification} />
        </View>
      </View>

      {/* Open Maps (to test background tracking) */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Open Maps (test background)</Text>
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
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    padding: 20,
    paddingTop: 60,
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
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
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
});
