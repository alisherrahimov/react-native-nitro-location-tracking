#include "HybridNitroLocationComplexLogicsCalculation.hpp"
#include <cmath>
#include <algorithm>

namespace margelo::nitro::nitrolocationtracking {

  // Mathematical constants
  constexpr double PI = 3.14159265358979323846;
  constexpr double EARTH_RADIUS_M = 6371000.0;

  double HybridNitroLocationComplexLogicsCalculation::haversineDistance(double lat1, double lon1, double lat2, double lon2) {
      double p = PI / 180.0;
      double a = 0.5 - std::cos((lat2 - lat1) * p)/2.0 + 
                 std::cos(lat1 * p) * std::cos(lat2 * p) * 
                 (1.0 - std::cos((lon2 - lon1) * p))/2.0;

      return 2.0 * EARTH_RADIUS_M * std::asin(std::sqrt(a));
  }

  TripMathStats HybridNitroLocationComplexLogicsCalculation::calculateTotalTripStats(const std::vector<LocationPoint>& points) {
      TripMathStats stats = {0.0, 0.0, 0.0, 0.0};
      
      if (points.size() < 2) return stats;

      double totalDist = 0.0;
      double maxSpeed = 0.0;
      double totalTimeMs = points.back().timestamp - points.front().timestamp;

      for (size_t i = 1; i < points.size(); ++i) {
          const auto& p1 = points[i-1];
          const auto& p2 = points[i];
          
          double dist = haversineDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
          totalDist += dist;
          
          if (p2.speed > maxSpeed) {
              maxSpeed = p2.speed;
          }
      }

      stats.totalDistanceMeters = totalDist;
      stats.elapsedTimeMs = totalTimeMs;
      
      // Convert max speed m/s to km/h if it comes in m/s, assume it's m/s
      stats.maxSpeedKmh = maxSpeed * 3.6;
      
      if (totalTimeMs > 0) {
          double avgSpeedMps = totalDist / (totalTimeMs / 1000.0);
          stats.averageSpeedKmh = avgSpeedMps * 3.6;
      }

      return stats;
  }

  std::vector<LocationPoint> HybridNitroLocationComplexLogicsCalculation::filterAnomalousPoints(const std::vector<LocationPoint>& points, double maxSpeedLimitMs) {
      std::vector<LocationPoint> filtered;
      if (points.empty()) return filtered;

      filtered.push_back(points.front());

      for (size_t i = 1; i < points.size(); ++i) {
          const auto& curr = points[i];
          const auto& last = filtered.back();
          
          double dist = haversineDistance(last.latitude, last.longitude, curr.latitude, curr.longitude);
          double timeDiffSec = (curr.timestamp - last.timestamp) / 1000.0;
          
          if (timeDiffSec <= 0) continue; // Invalid time
          
          double calcSpeed = dist / timeDiffSec;
          
          // If the calculated speed is ridiculously high (teleportation), ignore point
          if (calcSpeed <= maxSpeedLimitMs) {
              filtered.push_back(curr);
          }
      }

      return filtered;
  }

  double HybridNitroLocationComplexLogicsCalculation::perpendicularDistance(const LocationPoint& pt, const LocationPoint& lineStart, const LocationPoint& lineEnd) {
      double dx = lineEnd.longitude - lineStart.longitude;
      double dy = lineEnd.latitude - lineStart.latitude;
      
      double mag = std::sqrt(dx*dx + dy*dy);
      if (mag > 0.0) {
          dx /= mag;
          dy /= mag;
      }
      
      double pvx = pt.longitude - lineStart.longitude;
      double pvy = pt.latitude - lineStart.latitude;
      
      double pvdot = dx * pvx + dy * pvy;
      
      double ax = pvx - pvdot * dx;
      double ay = pvy - pvdot * dy;
      
      // Calculate distance in coordinates and roughly convert to meters using Haversine
      double projLat = pt.latitude - ay;
      double projLon = pt.longitude - ax;
      return haversineDistance(pt.latitude, pt.longitude, projLat, projLon);
  }

  void HybridNitroLocationComplexLogicsCalculation::douglasPeucker(const std::vector<LocationPoint>& points, double tolerance, int firstIdx, int lastIdx, std::vector<int>& keepIndexes) {
      if (lastIdx <= firstIdx + 1) return;

      double maxDist = 0.0;
      int index = firstIdx;

      for (int i = firstIdx + 1; i < lastIdx; ++i) {
          double dist = perpendicularDistance(points[i], points[firstIdx], points[lastIdx]);
          if (dist > maxDist) {
              index = i;
              maxDist = dist;
          }
      }

      if (maxDist > tolerance) {
          keepIndexes.push_back(index);
          douglasPeucker(points, tolerance, firstIdx, index, keepIndexes);
          douglasPeucker(points, tolerance, index, lastIdx, keepIndexes);
      }
  }

  std::vector<LocationPoint> HybridNitroLocationComplexLogicsCalculation::smoothPath(const std::vector<LocationPoint>& points, double toleranceMeters) {
      if (points.size() < 3) return points;

      std::vector<int> keepIndexes;
      keepIndexes.push_back(0);
      keepIndexes.push_back(points.size() - 1);

      douglasPeucker(points, toleranceMeters, 0, points.size() - 1, keepIndexes);

      std::sort(keepIndexes.begin(), keepIndexes.end());

      std::vector<LocationPoint> smoothed;
      for (int idx : keepIndexes) {
          smoothed.push_back(points[idx]);
      }

      return smoothed;
  }

  double HybridNitroLocationComplexLogicsCalculation::calculateBearing(double lat1, double lon1, double lat2, double lon2) {
      double p = PI / 180.0;
      double dLon = (lon2 - lon1) * p;
      double rLat1 = lat1 * p;
      double rLat2 = lat2 * p;

      double y = std::sin(dLon) * std::cos(rLat2);
      double x = std::cos(rLat1) * std::sin(rLat2) -
                 std::sin(rLat1) * std::cos(rLat2) * std::cos(dLon);

      double bearing = std::atan2(y, x) * (180.0 / PI);
      return std::fmod((bearing + 360.0), 360.0);
  }

  std::string HybridNitroLocationComplexLogicsCalculation::encodeGeohash(double latitude, double longitude, double precisionRaw) {
      static const char BASE32[] = "0123456789bcdefghjkmnpqrstuvwxyz";
      
      int precision = static_cast<int>(precisionRaw);
      if (precision < 1) precision = 1;
      if (precision > 12) precision = 12;

      bool is_even = true;
      double lat[2] = {-90.0, 90.0};
      double lon[2] = {-180.0, 180.0};
      int bit = 0;
      int ch = 0;
      std::string geohash = "";

      while (geohash.length() < precision) {
          if (is_even) {
              double mid = (lon[0] + lon[1]) / 2;
              if (longitude > mid) {
                  ch |= (1 << (4 - bit));
                  lon[0] = mid;
              } else {
                  lon[1] = mid;
              }
          } else {
              double mid = (lat[0] + lat[1]) / 2;
              if (latitude > mid) {
                  ch |= (1 << (4 - bit));
                  lat[0] = mid;
              } else {
                  lat[1] = mid;
              }
          }

          is_even = !is_even;
          if (bit < 4) {
              bit++;
          } else {
              geohash += BASE32[ch];
              bit = 0;
              ch = 0;
          }
      }
      return geohash;
  }

} // namespace margelo::nitro::nitrolocationtracking
