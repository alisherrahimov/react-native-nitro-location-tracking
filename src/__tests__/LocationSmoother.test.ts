import { LocationSmoother } from '../LocationSmoother';
import type { MarkerRef } from '../LocationSmoother';
import type { LocationData } from '../NitroLocationTracking.nitro';

const loc = (latitude: number, longitude: number): LocationData => ({
  latitude,
  longitude,
  altitude: 0,
  speed: 0,
  bearing: 0,
  accuracy: 5,
  timestamp: 0,
});

describe('LocationSmoother', () => {
  let animate: jest.Mock;
  let markerRef: { current: MarkerRef | null };
  let smoother: LocationSmoother;

  beforeEach(() => {
    jest.useFakeTimers();
    animate = jest.fn();
    markerRef = { current: { animateMarkerToCoordinate: animate } };
    smoother = new LocationSmoother(markerRef);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('skips the first location (marker mounts with it as initial coordinate)', () => {
    smoother.feed(loc(1, 1));
    expect(animate).not.toHaveBeenCalled();
  });

  it('animates to the second location with a duration', () => {
    smoother.feed(loc(1, 1)); // skipped
    smoother.feed(loc(2, 2)); // animates synchronously
    expect(animate).toHaveBeenCalledTimes(1);
    expect(animate).toHaveBeenLastCalledWith(
      { latitude: 2, longitude: 2 },
      expect.any(Number)
    );
  });

  it('drains queued locations in order over time', async () => {
    smoother.feed(loc(1, 1)); // skipped
    smoother.feed(loc(2, 2)); // animates now, then sleeps
    smoother.feed(loc(3, 3)); // queued while animating

    await jest.advanceTimersByTimeAsync(5000); // let the drain loop run

    expect(animate).toHaveBeenCalledTimes(2);
    expect(animate).toHaveBeenNthCalledWith(
      1,
      { latitude: 2, longitude: 2 },
      expect.any(Number)
    );
    expect(animate).toHaveBeenNthCalledWith(
      2,
      { latitude: 3, longitude: 3 },
      expect.any(Number)
    );
  });

  it('does nothing when the marker ref is null', () => {
    markerRef.current = null;
    smoother.feed(loc(1, 1)); // skipped
    expect(() => smoother.feed(loc(2, 2))).not.toThrow();
    expect(animate).not.toHaveBeenCalled();
  });

  it('treats the next feed as the first again after clear()', () => {
    smoother.feed(loc(1, 1)); // skipped
    smoother.feed(loc(2, 2)); // animates
    animate.mockClear();

    smoother.clear();
    smoother.feed(loc(9, 9)); // skipped again (fresh initial coordinate)
    expect(animate).not.toHaveBeenCalled();
  });
});
