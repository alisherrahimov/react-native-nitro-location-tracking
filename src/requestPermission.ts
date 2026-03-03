import { PermissionsAndroid, Platform } from 'react-native';

export async function requestLocationPermission(
  locationPermissionMessage?: {
    title: string | 'Location Permission';
    message:
      | string
      | 'This app needs access to your location to track your ride.';
    buttonPositive: string | 'Allow';
    buttonNegative: string | 'Deny';
  },
  locationPermissionBackground?: {
    title: string | 'Background Location Permission';
    message:
      | string
      | 'This app needs background location access to continue tracking while the app is minimized.';
    buttonPositive: string | 'Allow';
    buttonNegative: string | 'Deny';
  }
): Promise<boolean> {
  if (Platform.OS === 'ios') {
    // iOS permissions are handled via Info.plist + system prompt on first access
    return true;
  }

  const fineGranted = await PermissionsAndroid.request(
    PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
    {
      title: locationPermissionMessage?.title ?? 'Location Permission',
      message:
        locationPermissionMessage?.message ??
        'This app needs access to your location to track your ride.',
      buttonPositive: locationPermissionMessage?.buttonPositive ?? 'Allow',
      buttonNegative: locationPermissionMessage?.buttonNegative ?? 'Deny',
    }
  );

  if (fineGranted !== PermissionsAndroid.RESULTS.GRANTED) {
    return false;
  }

  // Request background location (required for Android 10+)
  if (Number(Platform.Version) >= 29) {
    const bgGranted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.ACCESS_BACKGROUND_LOCATION,
      {
        title:
          locationPermissionBackground?.title ??
          'Background Location Permission',
        message:
          locationPermissionBackground?.message ??
          'This app needs background location access to continue tracking while the app is minimized.',
        buttonPositive: locationPermissionBackground?.buttonPositive ?? 'Allow',
        buttonNegative: locationPermissionBackground?.buttonNegative ?? 'Deny',
      }
    );

    if (bgGranted !== PermissionsAndroid.RESULTS.GRANTED) {
      return false;
    }
  }

  // Request notification permission (required for Android 13+)
  if (Number(Platform.Version) >= 33) {
    await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS
    );
  }

  return true;
}
