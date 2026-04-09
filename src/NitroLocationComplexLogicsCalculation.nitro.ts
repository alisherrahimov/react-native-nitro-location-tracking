import type { HybridObject } from 'react-native-nitro-modules';

export interface LocationPoint {
  latitude: number;
  longitude: number;
  timestamp: number;
  speed: number;
  accuracy: number;
}

export interface TripMathStats {
  totalDistanceMeters: number;
  elapsedTimeMs: number;
  maxSpeedKmh: number;
  averageSpeedKmh: number;
}

export interface NitroLocationComplexLogicsCalculation
  extends HybridObject<{ ios: 'c++'; android: 'c++' }> {
  calculateTotalTripStats(points: LocationPoint[]): TripMathStats;

  filterAnomalousPoints(
    points: LocationPoint[],
    maxSpeedLimitMs: number
  ): LocationPoint[];

  smoothPath(points: LocationPoint[], toleranceMeters: number): LocationPoint[];

  calculateBearing(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
  ): number;

  encodeGeohash(latitude: number, longitude: number, precision: number): string;
}
