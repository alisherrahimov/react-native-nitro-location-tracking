import { SnapToRoad } from '../SnapToRoad';
import type { SnapPoint, SnapToRoadProvider } from '../SnapToRoad';

/** Provider that echoes its input — the "perfect snap" baseline. */
const identityProvider: SnapToRoadProvider = {
  snap: async (points) => points,
};

describe('SnapToRoad — buffering', () => {
  it('buffers added points and reports size', () => {
    const s = new SnapToRoad(identityProvider);
    s.add({ latitude: 1, longitude: 1 });
    s.add({ latitude: 2, longitude: 2 });
    expect(s.size).toBe(2);
  });

  it('clear() empties the buffer without calling the provider', async () => {
    const snap = jest.fn(async (p: SnapPoint[]) => p);
    const s = new SnapToRoad({ snap });
    s.add({ latitude: 1, longitude: 1 });
    s.clear();
    expect(s.size).toBe(0);
    expect(await s.flush()).toEqual([]);
    expect(snap).not.toHaveBeenCalled();
  });

  it('flush() on an empty buffer resolves [] and skips the provider', async () => {
    const snap = jest.fn(async (p: SnapPoint[]) => p);
    const s = new SnapToRoad({ snap });
    expect(await s.flush()).toEqual([]);
    expect(snap).not.toHaveBeenCalled();
  });
});

describe('SnapToRoad — distance thinning', () => {
  it('skips points closer than minDistanceMeters to the previous one', () => {
    const s = new SnapToRoad(identityProvider, { minDistanceMeters: 10 });
    s.add({ latitude: 0, longitude: 0 });
    s.add({ latitude: 0, longitude: 0.00001 }); // ~1.1 m → skipped
    expect(s.size).toBe(1);
    s.add({ latitude: 0, longitude: 0.001 }); // ~111 m → kept
    expect(s.size).toBe(2);
  });

  it('keeps every point when minDistanceMeters is 0 (default)', () => {
    const s = new SnapToRoad(identityProvider);
    s.add({ latitude: 0, longitude: 0 });
    s.add({ latitude: 0, longitude: 0.00001 });
    expect(s.size).toBe(2);
  });
});

describe('SnapToRoad — batching', () => {
  it('splits the buffer into batchSize chunks and concatenates results', async () => {
    const chunkSizes: number[] = [];
    const provider: SnapToRoadProvider = {
      async snap(points) {
        chunkSizes.push(points.length);
        return points;
      },
    };
    const s = new SnapToRoad(provider, { batchSize: 2 });
    for (let i = 0; i < 5; i++) s.add({ latitude: i, longitude: i });

    const out = await s.flush();
    expect(chunkSizes).toEqual([2, 2, 1]);
    expect(out).toHaveLength(5);
    expect(s.size).toBe(0); // buffer drained
  });
});

describe('SnapToRoad — fallback to raw', () => {
  it('uses the raw chunk when the provider returns an empty array', async () => {
    const raw: SnapPoint[] = [
      { latitude: 1, longitude: 1 },
      { latitude: 2, longitude: 2 },
    ];
    const s = new SnapToRoad({ snap: async () => [] });
    raw.forEach((p) => s.add(p));
    expect(await s.flush()).toEqual(raw);
  });

  it('uses the raw chunk when the provider throws', async () => {
    const s = new SnapToRoad({
      snap: async () => {
        throw new Error('provider down');
      },
    });
    s.add({ latitude: 3, longitude: 4 });
    expect(await s.flush()).toEqual([{ latitude: 3, longitude: 4 }]);
  });
});
