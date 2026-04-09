#pragma once

#include "HybridNitroLocationComplexLogicsCalculationSpec.hpp"
#include <vector>

namespace margelo::nitro::nitrolocationtracking {

  class HybridNitroLocationComplexLogicsCalculation : public HybridNitroLocationComplexLogicsCalculationSpec {
  public:
    HybridNitroLocationComplexLogicsCalculation() : HybridObject(TAG) {}

    // Methods
    TripMathStats calculateTotalTripStats(const std::vector<LocationPoint>& points) override;
    
    std::vector<LocationPoint> filterAnomalousPoints(const std::vector<LocationPoint>& points, double maxSpeedLimitMs) override;
    
    std::vector<LocationPoint> smoothPath(const std::vector<LocationPoint>& points, double toleranceMeters) override;
    
    double calculateBearing(double lat1, double lon1, double lat2, double lon2) override;
    
    std::string encodeGeohash(double latitude, double longitude, double precision) override;
    
  private:
    double haversineDistance(double lat1, double lon1, double lat2, double lon2);
    double perpendicularDistance(const LocationPoint& pt, const LocationPoint& lineStart, const LocationPoint& lineEnd);
    void douglasPeucker(const std::vector<LocationPoint>& points, double tolerance, int firstIdx, int lastIdx, std::vector<int>& keepIndexes);
  };

} // namespace margelo::nitro::nitrolocationtracking
