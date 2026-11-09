import { createLiveActivityController } from '../LiveActivity';
import type { LiveActivityContent } from '../NitroLocationTracking.nitro';

function makeNative() {
  return {
    isLiveActivitySupported: jest.fn(() => true),
    areLiveActivitiesEnabled: jest.fn(() => true),
    isLiveActivityRunning: jest.fn(() => false),
    startLiveActivityWithContent: jest.fn(),
    updateLiveActivityWithContent: jest.fn(),
    endLiveActivityWithContent: jest.fn(),
  };
}

const onTheWay: LiveActivityContent = {
  title: 'On the way',
  subtitle: '#10423',
  icon: { type: 'symbol', name: 'car.fill', color: '#1E88E5' },
  value: '12',
  valueUnit: 'min',
  secondaryValue: '2.4 km',
  deepLinkUrl: 'myapp://delivery/10423',
};

describe('createLiveActivityController', () => {
  it('passes capability queries through to native', () => {
    const native = makeNative();
    const la = createLiveActivityController(native);
    expect(la.isSupported()).toBe(true);
    expect(la.areEnabled()).toBe(true);
    expect(la.isRunning()).toBe(false);
  });

  it('starts with the full content', () => {
    const native = makeNative();
    createLiveActivityController(native).start(onTheWay);
    expect(native.startLiveActivityWithContent).toHaveBeenCalledWith(onTheWay);
  });

  it('skips identical consecutive updates', () => {
    const native = makeNative();
    const la = createLiveActivityController(native);
    la.start(onTheWay);
    expect(la.update({ ...onTheWay })).toBe(false);
    expect(la.update({ ...onTheWay, value: '11' })).toBe(true);
    expect(la.update({ ...onTheWay, value: '11' })).toBe(false);
    expect(native.updateLiveActivityWithContent).toHaveBeenCalledTimes(1);
  });

  it('does not record a payload when start throws', () => {
    const native = makeNative();
    native.startLiveActivityWithContent.mockImplementation(() => {
      throw new Error('disabled');
    });
    const la = createLiveActivityController(native);
    expect(() => la.start(onTheWay)).toThrow('disabled');
    expect(la.update(onTheWay)).toBe(true);
  });

  it('ends with optional final content and resets dedupe', () => {
    const native = makeNative();
    const la = createLiveActivityController(native);
    la.start(onTheWay);
    const delivered = { ...onTheWay, title: 'Delivered' };
    la.end({ finalContent: delivered, dismissAfterSeconds: 5 });
    expect(native.endLiveActivityWithContent).toHaveBeenCalledWith(
      delivered,
      5
    );
    la.end();
    expect(native.endLiveActivityWithContent).toHaveBeenLastCalledWith(
      undefined,
      undefined
    );
    expect(la.update(onTheWay)).toBe(true);
  });
});
