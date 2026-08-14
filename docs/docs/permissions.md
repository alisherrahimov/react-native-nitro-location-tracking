---
sidebar_position: 3
---

# Permissions

Call this before starting location tracking, especially on Android:

```tsx
import { requestLocationPermission } from 'react-native-nitro-location-tracking';

const granted = await requestLocationPermission();
if (!granted) {
  console.warn('Location permission denied');
}
```

You can customize the permission dialog messages:

```tsx
const granted = await requestLocationPermission(
  {
    title: 'Location Access',
    message: 'We need your location to show your position on the map.',
    buttonPositive: 'Allow',
    buttonNegative: 'Deny',
  },
  {
    title: 'Background Location',
    message:
      'Allow background location to keep tracking while the app is minimized.',
    buttonPositive: 'Allow',
    buttonNegative: 'Deny',
  }
);
```

:::info
On iOS, permissions are handled via `Info.plist` and the system prompt on
first access.
:::

## Checking status without prompting

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

const status = NitroLocationModule.getLocationPermissionStatus();

switch (status) {
  case 'always':
    console.log('Background location granted');
    break;
  case 'whenInUse':
    console.log('Foreground only — background tracking may not work');
    break;
  case 'denied':
    console.warn('Location permission denied');
    break;
  case 'restricted':
    console.warn('Location restricted by parental controls or MDM');
    break;
  case 'notDetermined':
    console.log('Permission not yet requested');
    break;
}
```

| Status          | iOS                      | Android                      |
| --------------- | ------------------------ | ----------------------------- |
| `notDetermined` | Not yet asked            | N/A (returns `denied`)        |
| `denied`        | User denied              | Fine location not granted     |
| `restricted`    | Parental/MDM restriction | N/A (returns `denied`)        |
| `whenInUse`     | Authorized when in use   | Fine granted, background not  |
| `always`        | Authorized always        | Fine + background granted     |

## Requesting via the native module

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

async function setup() {
  const current = NitroLocationModule.getLocationPermissionStatus();

  if (current === 'always' || current === 'whenInUse') {
    NitroLocationModule.startTracking();
    return;
  }

  const status = await NitroLocationModule.requestLocationPermission();

  switch (status) {
    case 'always':
    case 'whenInUse':
      NitroLocationModule.startTracking();
      break;
    case 'denied':
      Alert.alert('Location Required', 'Please enable location in Settings');
      break;
    case 'restricted':
      // Parental controls / MDM — cannot request
      break;
  }
}
```

**Platform behavior:**

| Platform | Behavior |
| -------- | -------- |
| iOS      | Calls `requestAlwaysAuthorization()`. If permission is already determined, resolves immediately with the current status. If `startTracking()` was called before permission was granted, tracking auto-starts once the user allows. |
| Android  | Uses React Native's `PermissionAwareActivity` to show the system permission dialog for `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`. Resolves with the resulting status after the user responds. |

## Listening for permission changes

The callback fires whenever the user changes the location permission (via the
system dialog, the Settings app, or MDM policy changes):

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

NitroLocationModule.onPermissionStatusChange((status) => {
  console.log('Permission changed to:', status);

  switch (status) {
    case 'always':
      console.log('Background location granted — full tracking available');
      break;
    case 'whenInUse':
      console.log('Foreground only — background tracking may not work');
      break;
    case 'denied':
      Alert.alert('Location Required', 'Please re-enable location in Settings');
      break;
    case 'restricted':
      console.warn('Location restricted by parental controls or MDM');
      break;
  }
});
```

| Platform | Mechanism                                        | When the callback fires                                                     |
| -------- | ------------------------------------------------- | ----------------------------------------------------------------------------- |
| iOS      | `locationManagerDidChangeAuthorization` delegate  | Immediately when the user changes permission (system dialog, Settings, MDM) |
| Android  | `ProcessLifecycleOwner` lifecycle observer         | When the app returns to foreground after the user changes permission in Settings |
