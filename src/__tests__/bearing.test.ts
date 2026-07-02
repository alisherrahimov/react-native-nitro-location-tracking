import { calculateBearing, shortestRotation } from '../bearing';

describe('calculateBearing', () => {
  const origin = { latitude: 0, longitude: 0 };

  it('points ~90° (east) for a due-east target', () => {
    expect(calculateBearing(origin, { latitude: 0, longitude: 1 })).toBeCloseTo(
      90,
      5
    );
  });

  it('points ~0° (north) for a due-north target', () => {
    expect(calculateBearing(origin, { latitude: 1, longitude: 0 })).toBeCloseTo(
      0,
      5
    );
  });

  it('points ~270° (west) for a due-west target', () => {
    expect(
      calculateBearing(origin, { latitude: 0, longitude: -1 })
    ).toBeCloseTo(270, 5);
  });

  it('points ~180° (south) for a due-south target', () => {
    expect(calculateBearing({ latitude: 1, longitude: 0 }, origin)).toBeCloseTo(
      180,
      5
    );
  });

  it('always returns a value in [0, 360)', () => {
    const b = calculateBearing(
      { latitude: 41.31, longitude: 69.24 },
      { latitude: 41.29, longitude: 69.2 }
    );
    expect(b).toBeGreaterThanOrEqual(0);
    expect(b).toBeLessThan(360);
  });
});

describe('shortestRotation', () => {
  it('unwraps forward across the 360/0 boundary (350 → 10)', () => {
    // Continuous target so a marker rotates +20°, not -340°.
    expect(shortestRotation(350, 10)).toBe(370);
  });

  it('unwraps backward across the 0/360 boundary (10 → 350)', () => {
    expect(shortestRotation(10, 350)).toBe(-10);
  });

  it('leaves a small forward turn unchanged', () => {
    expect(shortestRotation(0, 90)).toBe(90);
  });

  it('returns the origin when from == to', () => {
    expect(shortestRotation(42, 42)).toBe(42);
  });
});
