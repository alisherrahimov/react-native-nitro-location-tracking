---
sidebar_position: 3
---

# Native Module Methods

All functionality is exposed on the default export, `NitroLocationModule`.

| Method | Returns | Description |
| --- | --- | --- |
| `configure(config)` | `void` | Set location tracking configuration |
| `startTracking()` | `void` | Start location tracking |
| `stopTracking()` | `void` | Stop location tracking |
| `getCurrentLocation()` | `Promise<LocationData>` | Get a one-shot location |
| `isTracking()` | `boolean` | Check if tracking is active |
| `onLocation(callback)` | `void` | Register location update callback |
| `onMotionChange(callback)` | `void` | Register motion state callback |
| `configureConnection(config)` | `void` | Set WebSocket/REST configuration |
| `connectWebSocket()` | `void` | Open WebSocket connection |
| `disconnectWebSocket()` | `void` | Close WebSocket connection |
| `sendMessage(message)` | `void` | Send a message via WebSocket |
| `getConnectionState()` | `ConnectionState` | Get current connection state |
| `onConnectionStateChange(callback)` | `void` | Register connection state callback |
| `onMessage(callback)` | `void` | Register incoming message callback |
| `forceSync()` | `Promise<boolean>` | Drain the durable Live Push queue now; resolves `true` when empty (no-op resolving `true` when the queue is disabled) |
| `getQueuedCount()` | `number` | Fixes currently waiting in the durable Live Push queue (`0` when disabled) |
| `configureLivePush(config)` | `void` | Set the native live-push endpoint, token, and body fields (call on login / token refresh / new delivery) |
| `setLivePushEnabled(enabled)` | `void` | Cheap runtime on/off for live push without losing config (call on duty/online state changes) |
| `clearLivePush()` | `void` | Wipe live-push config and disable it (call on logout) |
| `onLivePushResult(callback)` | `void` | Observe each live-push POST outcome (`LivePushResult`). Buffered across JS suspension |
| `isFakeGpsEnabled()` | `boolean` | Check if device-level mock location is enabled |
| `setRejectMockLocations(reject)` | `void` | Auto-reject mock locations when `true` |
| `onMockLocationDetected(callback)` | `void` | Register a callback that fires when device-level mock location is toggled on/off |
| `addGeofence(region)` | `void` | Start monitoring a circular geofence region |
| `removeGeofence(regionId)` | `void` | Stop monitoring a specific geofence |
| `removeAllGeofences()` | `void` | Remove all active geofences |
| `onGeofenceEvent(callback)` | `void` | Register geofence enter/exit/dwell callback |
| `getDistanceBetween(lat1, lon1, lat2, lon2)` | `number` | Calculate distance between two points in meters (native Haversine) |
| `getDistanceToGeofence(regionId)` | `number` | Get distance in meters from last known location to a geofence center (`-1` if not found) |
| `getOdometer()` | `number` | Total meters traveled while tracking; persisted across app kill / reboot |
| `resetOdometer()` | `void` | Reset the persisted odometer to 0 |
| `getEtaTo(latitude, longitude)` | `EtaResult` | Straight-line distance + ETA (seconds) from last known location to a target (`-1` fields when not estimable) |
| `configureSpeedMonitor(config)` | `void` | Set speed monitoring thresholds |
| `onSpeedAlert(callback)` | `void` | Register speed state-transition callback |
| `getCurrentSpeed()` | `number` | Get current speed in km/h |
| `startTripCalculation()` | `void` | Start recording trip distance/stats |
| `stopTripCalculation()` | `TripStats` | Stop recording and get final stats |
| `getTripStats()` | `TripStats` | Get current trip stats without stopping |
| `resetTripCalculation()` | `void` | Reset trip calculator |
| `isLocationServicesEnabled()` | `boolean` | Check if GPS/location is enabled on device |
| `openLocationSettings()` | `Promise<boolean>` | Prompt user to enable GPS. Resolves `true` if enabled, `false` if not |
| `onProviderStatusChange(callback)` | `void` | Register GPS/network provider status callback |
| `isAirplaneModeEnabled()` | `boolean` | Check if Airplane mode is active on Android |
| `onAirplaneModeChange(callback)` | `void` | Register Airplane mode state-transition callback |
| `startNetworkMonitoring()` | `void` | Start cellular/transport monitoring (idempotent) |
| `stopNetworkMonitoring()` | `void` | Stop cellular/transport monitoring |
| `getNetworkStatus()` | `NetworkStatus` | Synchronous snapshot of the current network status |
| `onNetworkChange(callback)` | `void` | Register a callback that fires on network status change |
| `getDeviceManufacturer()` | `string` | Lowercased device manufacturer (e.g. `"xiaomi"`); `"apple"` on iOS |
| `isIgnoringBatteryOptimizations()` | `boolean` | Android: is the app exempt from Doze battery optimization? iOS: always `true` |
| `requestIgnoreBatteryOptimizations()` | `Promise<boolean>` | Prompt for battery-optimization exemption; resolves resulting state (iOS resolves `true`) |
| `openOemAutoStartSettings()` | `Promise<boolean>` | Open the OEM auto-start / protected-apps screen; resolves `true` if opened (iOS `false`) |
| `getLocationPermissionStatus()` | `PermissionStatus` | Check current location permission without prompting |
| `requestLocationPermission()` | `Promise<PermissionStatus>` | Request location permission and return the resulting status |
| `onPermissionStatusChange(callback)` | `void` | Register a callback that fires when location permission status changes |
| `startLiveActivity(orderId, customerName, deliveryAddress, orderCount, status, statusText, estimatedMinutes, distanceMeters)` | `void` | Start a live delivery card — Lock Screen / Dynamic Island (iOS 16.2+) or an ongoing notification (Android) |
| `updateLiveActivity(status, statusText, estimatedMinutes, distanceMeters)` | `void` | Push a state update to the running Live Activity |
| `endLiveActivity()` | `void` | End and dismiss the Live Activity immediately |
| `showLocalNotification(title, body)` | `void` | Show a local notification |
| `updateForegroundNotification(title, body)` | `void` | Update the foreground service notification |
| `destroy()` | `void` | Stop tracking and disconnect |
