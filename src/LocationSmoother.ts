import type { LocationData } from './NitroLocationTracking.nitro';

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface MarkerRef {
  animateMarkerToCoordinate(
    coordinate: { latitude: number; longitude: number },
    duration: number
  ): void;
}

export class LocationSmoother {
  private queue: LocationData[] = [];
  private isAnimating = false;
  private markerRef: { current: MarkerRef | null };
  private hasInitialCoordinate = false;

  constructor(markerRef: { current: MarkerRef | null }) {
    this.markerRef = markerRef;
  }

  feed(location: LocationData) {
    // Skip the first location — let the Marker mount with it as its
    // initial `coordinate` prop. Calling animateMarkerToCoordinate before
    // the native marker has a position causes a NullPointerException.
    if (!this.hasInitialCoordinate) {
      this.hasInitialCoordinate = true;
      return;
    }

    this.queue.push(location);
    if (this.queue.length > 10) this.queue = this.queue.slice(-3);
    if (!this.isAnimating) this.drain();
  }

  private async drain() {
    this.isAnimating = true;
    while (this.queue.length > 0) {
      const next = this.queue.shift()!;
      const dur =
        this.queue.length > 3 ? 500 : this.queue.length > 1 ? 1000 : 2000;
      this.markerRef.current?.animateMarkerToCoordinate(
        { latitude: next.latitude, longitude: next.longitude },
        dur
      );
      await sleep(dur * 0.9);
    }
    this.isAnimating = false;
  }

  clear() {
    this.queue = [];
    this.isAnimating = false;
    this.hasInitialCoordinate = false;
  }
}
