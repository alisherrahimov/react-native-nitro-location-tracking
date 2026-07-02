//
// Standalone unit test for the pure-C++ geo-math in
// cpp/HybridNitroLocationComplexLogicsCalculation.cpp
//
// It compiles the REAL implementation file against lightweight stubs of the
// nitrogen-generated types (see ./stubs), so the algorithms run without the
// NitroModules / JSI runtime or a device. Run it with ./run.sh (or the
// `yarn test:cpp` script). Exits non-zero if any assertion fails.
//
#include "HybridNitroLocationComplexLogicsCalculation.hpp"
#include <cstdio>
#include <cmath>
#include <string>

using namespace margelo::nitro::nitrolocationtracking;

static int failures = 0;
static int total = 0;

static void check(const char* name, bool ok, const std::string& detail = "") {
  ++total;
  const char* color = ok ? "\033[32m" : "\033[31m";
  const char* label = ok ? "PASS" : "FAIL";
  std::printf("  %s%s\033[0m  %s %s\n", color, label, name, detail.c_str());
  if (!ok) ++failures;
}

static bool near(double a, double b, double tol) { return std::fabs(a - b) <= tol; }
static std::string got(double v) { return "(got " + std::to_string(v) + ")"; }

int main() {
  HybridNitroLocationComplexLogicsCalculation calc;
  std::printf("Unit test: NitroLocationComplexLogicsCalculation (real C++ impl)\n\n");

  // ---- calculateBearing ----
  std::printf("calculateBearing:\n");
  double bEast  = calc.calculateBearing(0, 0, 0, 1);   // due east from equator
  double bNorth = calc.calculateBearing(0, 0, 1, 0);   // due north
  double bWest  = calc.calculateBearing(0, 0, 0, -1);  // due west
  double bSouth = calc.calculateBearing(1, 0, 0, 0);   // due south
  check("east  ~= 90",  near(bEast, 90, 0.5),   got(bEast));
  check("north ~= 0",   near(bNorth, 0, 0.5),   got(bNorth));
  check("west  ~= 270", near(bWest, 270, 0.5),  got(bWest));
  check("south ~= 180", near(bSouth, 180, 0.5), got(bSouth));

  // ---- calculateTotalTripStats (uses Haversine internally) ----
  std::printf("\ncalculateTotalTripStats:\n");
  // 1 degree of longitude at the equator ~= 111194.9 m (geodesic reference).
  std::vector<LocationPoint> trip = {
    LocationPoint(0.0, 0.0, 0.0,     10.0, 5.0),   // t=0ms
    LocationPoint(0.0, 1.0, 60000.0, 20.0, 5.0),   // t=60s, 1 deg east
  };
  TripMathStats stats = calc.calculateTotalTripStats(trip);
  check("distance ~= 111195 m",           near(stats.totalDistanceMeters, 111194.9, 50.0), got(stats.totalDistanceMeters));
  check("elapsed  == 60000 ms",           near(stats.elapsedTimeMs, 60000.0, 0.001),       got(stats.elapsedTimeMs));
  check("maxSpeed == 20 m/s -> 72 km/h",  near(stats.maxSpeedKmh, 72.0, 0.001),            got(stats.maxSpeedKmh));
  check("avgSpeed  ~= 6672 km/h",         near(stats.averageSpeedKmh, 6672.0, 10.0),       got(stats.averageSpeedKmh));
  TripMathStats empty = calc.calculateTotalTripStats({});
  check("empty trip -> all zero", empty.totalDistanceMeters == 0 && empty.elapsedTimeMs == 0);

  // ---- filterAnomalousPoints ----
  std::printf("\nfilterAnomalousPoints:\n");
  std::vector<LocationPoint> raw = {
    LocationPoint(0.0,    0.0,    0.0,    0, 5),
    LocationPoint(0.0001, 0.0001, 1000.0, 0, 5),  // ~15.7 m in 1s -> ok
    LocationPoint(1.0,    1.0,    2000.0, 0, 5),  // teleport ~157 km in 1s -> drop
    LocationPoint(0.0002, 0.0002, 3000.0, 0, 5),  // small move from p1 -> keep
  };
  auto filtered = calc.filterAnomalousPoints(raw, 50.0 /* m/s max */);
  check("teleport dropped (4 -> 3)", filtered.size() == 3, got((double)filtered.size()));
  bool teleportGone = true;
  for (auto& p : filtered) if (p.latitude == 1.0 && p.longitude == 1.0) teleportGone = false;
  check("teleport point absent", teleportGone);
  check("empty input -> empty output", calc.filterAnomalousPoints({}, 50.0).empty());

  // ---- smoothPath (Douglas-Peucker) ----
  std::printf("\nsmoothPath:\n");
  std::vector<LocationPoint> line = {
    LocationPoint(0.0, 0.0, 0, 0, 5),
    LocationPoint(0.0, 1.0, 0, 0, 5),
    LocationPoint(0.0, 2.0, 0, 0, 5),
    LocationPoint(0.0, 3.0, 0, 0, 5),
    LocationPoint(0.0, 4.0, 0, 0, 5),
  };
  auto smoothed = calc.smoothPath(line, 10.0 /* meters tolerance */);
  check("collinear collapses to 2 endpoints", smoothed.size() == 2, got((double)smoothed.size()));
  check("endpoints preserved",
        smoothed.size() == 2 && smoothed.front().longitude == 0.0 && smoothed.back().longitude == 4.0);
  std::vector<LocationPoint> detour = {
    LocationPoint(0.0, 0.0, 0, 0, 5),
    LocationPoint(1.0, 1.0, 0, 0, 5),  // large deviation off the straight line
    LocationPoint(0.0, 2.0, 0, 0, 5),
  };
  auto keptDetour = calc.smoothPath(detour, 10.0);
  check("sharp detour retained (3 -> 3)", keptDetour.size() == 3, got((double)keptDetour.size()));
  check("<3 points returned as-is", calc.smoothPath({line[0], line[1]}, 10.0).size() == 2);

  // ---- encodeGeohash ----
  std::printf("\nencodeGeohash:\n");
  // Canonical reference: (57.64911, 10.40744) -> "u4pruydqqvj".
  std::string gh = calc.encodeGeohash(57.64911, 10.40744, 11);
  check("known geohash == u4pruydqqvj", gh == "u4pruydqqvj", "(got " + gh + ")");
  check("precision clamped to 12 max", calc.encodeGeohash(57.64911, 10.40744, 99).size() == 12);
  check("precision clamped to 1 min",  calc.encodeGeohash(57.64911, 10.40744, 0).size() == 1);
  std::string gh5 = calc.encodeGeohash(57.64911, 10.40744, 5);
  check("shorter is prefix of longer", gh.rfind(gh5, 0) == 0, "(" + gh5 + " vs " + gh + ")");

  std::printf("\n----------------------------------------\n");
  std::printf("%d/%d checks passed\n", total - failures, total);
  return failures == 0 ? 0 : 1;
}
