---
sidebar_position: 14
---

# Snap to Road (pluggable)

For cleaner trip polylines, snap raw GPS fixes to the road network. The
library **bundles no map vendor** — you implement `SnapToRoadProvider`
around whatever service you use (Google Roads API, Mapbox Map Matching,
Valhalla, OSRM, …) and feed fixes through `SnapToRoad`, which buffers them,
batches provider calls, and falls back to raw points if the provider errors.

```tsx
import NitroLocationModule, {
  SnapToRoad,
  type SnapToRoadProvider,
  type SnapPoint,
} from 'react-native-nitro-location-tracking';

// 1. Wrap your roads service.
const googleRoads: SnapToRoadProvider = {
  async snap(points: SnapPoint[]) {
    const path = points.map((p) => `${p.latitude},${p.longitude}`).join('|');
    const res = await fetch(
      `https://roads.googleapis.com/v1/snapToRoads?interpolate=true&path=${path}&key=YOUR_KEY`
    );
    const json = await res.json();
    return (json.snappedPoints ?? []).map((sp: any) => ({
      latitude: sp.location.latitude,
      longitude: sp.location.longitude,
    }));
  },
};

// 2. Buffer fixes as they arrive.
const snapper = new SnapToRoad(googleRoads, { minDistanceMeters: 5 });
NitroLocationModule.onLocation((loc) => snapper.add(loc));

// 3. Snap when you need the polyline (clears the buffer).
const polyline = await snapper.flush();
```

- `batchSize` (default 100) splits large buffers into sequential provider
  calls.
- `minDistanceMeters` thins dense/duplicate fixes before snapping.
- A failed or empty provider response falls back to that batch's raw points,
  so the returned line is always continuous.
