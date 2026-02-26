import { NitroModules } from 'react-native-nitro-modules';
import type { NitroLocationTracking } from './NitroLocationTracking.nitro';

const NitroLocationTrackingHybridObject =
  NitroModules.createHybridObject<NitroLocationTracking>('NitroLocationTracking');

export function multiply(a: number, b: number): number {
  return NitroLocationTrackingHybridObject.multiply(a, b);
}
