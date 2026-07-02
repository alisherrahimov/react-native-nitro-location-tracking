/**
 * Pluggable snap-to-road support.
 *
 * The library deliberately bundles no map vendor. Instead you implement the
 * {@link SnapToRoadProvider} interface around whatever service you use (Google
 * Roads API, Mapbox Map Matching, Valhalla, OSRM, …) and feed raw fixes through
 * {@link SnapToRoad}, which buffers them and returns road-aligned coordinates
 * for cleaner trip polylines.
 */

export interface SnapPoint {
  latitude: number;
  longitude: number;
}

/**
 * Implement this around your routing/roads service. Given a sequence of raw
 * coordinates (in order), return the road-snapped coordinates. You may return a
 * different number of points than you received (map-matching services often
 * interpolate); an empty array means "could not snap" and the raw points are
 * kept as a fallback.
 */
export interface SnapToRoadProvider {
  snap(points: SnapPoint[]): Promise<SnapPoint[]>;
}

export interface SnapToRoadOptions {
  /**
   * Max points sent to the provider per `snap()` call (default 100 — the Google
   * Roads API limit). Larger buffers are split into sequential requests.
   */
  batchSize?: number;
  /**
   * Skip fixes closer than this many meters to the previous buffered fix
   * (default 0 = keep all). Thins dense/duplicate points before snapping.
   */
  minDistanceMeters?: number;
}

const EARTH_RADIUS_M = 6371000;

function haversineMeters(a: SnapPoint, b: SnapPoint): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.latitude - a.latitude);
  const dLon = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

/**
 * Buffers raw fixes and snaps them to roads via a user-supplied provider.
 *
 * ```ts
 * const snapper = new SnapToRoad(myGoogleRoadsProvider, { minDistanceMeters: 5 });
 * NitroLocationModule.onLocation((loc) => snapper.add(loc));
 * // later, e.g. when rendering the route:
 * const polyline = await snapper.flush();
 * ```
 */
export class SnapToRoad {
  private buffer: SnapPoint[] = [];
  private readonly batchSize: number;
  private readonly minDistanceMeters: number;

  constructor(
    private readonly provider: SnapToRoadProvider,
    options: SnapToRoadOptions = {}
  ) {
    this.batchSize = Math.max(1, options.batchSize ?? 100);
    this.minDistanceMeters = Math.max(0, options.minDistanceMeters ?? 0);
  }

  /** Buffer a raw fix. Anything with `latitude`/`longitude` is accepted. */
  add(point: SnapPoint): void {
    if (this.minDistanceMeters > 0 && this.buffer.length > 0) {
      const last = this.buffer[this.buffer.length - 1]!;
      if (haversineMeters(last, point) < this.minDistanceMeters) return;
    }
    this.buffer.push({ latitude: point.latitude, longitude: point.longitude });
  }

  /** Number of raw points currently buffered. */
  get size(): number {
    return this.buffer.length;
  }

  /**
   * Snap all buffered points (in `batchSize` chunks) and clear the buffer.
   * Returns the concatenated snapped polyline. If a batch fails or the provider
   * returns nothing, that batch's raw points are used as a fallback so the
   * caller always gets a continuous line.
   */
  async flush(): Promise<SnapPoint[]> {
    if (this.buffer.length === 0) return [];
    const pending = this.buffer;
    this.buffer = [];

    const out: SnapPoint[] = [];
    for (let i = 0; i < pending.length; i += this.batchSize) {
      const chunk = pending.slice(i, i + this.batchSize);
      try {
        const snapped = await this.provider.snap(chunk);
        out.push(...(snapped.length > 0 ? snapped : chunk));
      } catch {
        out.push(...chunk); // fall back to raw on provider error
      }
    }
    return out;
  }

  /** Drop all buffered points without snapping. */
  clear(): void {
    this.buffer = [];
  }
}
