---
sidebar_position: 5
---

# Smooth Map Marker Animation

Use `LocationSmoother` to animate a map marker smoothly between location
updates:

```tsx
import { useEffect, useRef, useState } from 'react';
import { Marker } from 'react-native-maps';
import NitroLocationModule, {
  LocationSmoother,
} from 'react-native-nitro-location-tracking';
import type { LocationData } from 'react-native-nitro-location-tracking';

function MapScreen() {
  const markerRef = useRef(null);
  const smootherRef = useRef(new LocationSmoother(markerRef));
  const [location, setLocation] = useState<LocationData | null>(null);

  useEffect(() => {
    NitroLocationModule.configure(config);
    NitroLocationModule.onLocation((loc) => {
      // Feed each new location into the smoother, then keep the marker mounted
      smootherRef.current.feed(loc);
      setLocation(loc);
    });
    NitroLocationModule.startTracking();
    return () => NitroLocationModule.stopTracking();
  }, []);

  return (
    <MapView>
      {location && (
        <Marker
          ref={markerRef}
          coordinate={{
            latitude: location.latitude,
            longitude: location.longitude,
          }}
        />
      )}
    </MapView>
  );
}
```
