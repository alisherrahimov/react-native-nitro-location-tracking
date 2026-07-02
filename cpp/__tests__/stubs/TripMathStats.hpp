#pragma once
//
// Test-only stub of the nitrogen-generated `TripMathStats` struct.
// See LocationPoint.hpp in this folder for why this shadow exists.
//
namespace margelo::nitro::nitrolocationtracking {
  struct TripMathStats final {
    double totalDistanceMeters;
    double elapsedTimeMs;
    double maxSpeedKmh;
    double averageSpeedKmh;
    TripMathStats() = default;
    explicit TripMathStats(double a, double b, double c, double d)
      : totalDistanceMeters(a), elapsedTimeMs(b), maxSpeedKmh(c), averageSpeedKmh(d) {}
    friend bool operator==(const TripMathStats&, const TripMathStats&) = default;
  };
}
