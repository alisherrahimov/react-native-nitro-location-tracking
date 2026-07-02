#pragma once
//
// Test-only stub mirroring the nitrogen-generated abstract spec.
//
// This lets the real cpp/HybridNitroLocationComplexLogicsCalculation.{hpp,cpp}
// compile and run unmodified without the NitroModules / JSI runtime. The virtual
// method signatures and the `TAG` constant match the generated spec so the real
// subclass overrides resolve correctly.
//
#include "TripMathStats.hpp"
#include "LocationPoint.hpp"
#include <vector>
#include <string>

namespace margelo::nitro {
  // Stand-in for <NitroModules/HybridObject.hpp>. Declared as a virtual base
  // (as in the generated spec) so the subclass ctor can initialize it directly.
  class HybridObject {
  public:
    explicit HybridObject(const char*) {}
    virtual ~HybridObject() = default;
  };
}

namespace margelo::nitro::nitrolocationtracking {
  using namespace margelo::nitro;

  class HybridNitroLocationComplexLogicsCalculationSpec : public virtual HybridObject {
  public:
    explicit HybridNitroLocationComplexLogicsCalculationSpec(): HybridObject(TAG) {}
    ~HybridNitroLocationComplexLogicsCalculationSpec() override = default;

    virtual TripMathStats calculateTotalTripStats(const std::vector<LocationPoint>& points) = 0;
    virtual std::vector<LocationPoint> filterAnomalousPoints(const std::vector<LocationPoint>& points, double maxSpeedLimitMs) = 0;
    virtual std::vector<LocationPoint> smoothPath(const std::vector<LocationPoint>& points, double toleranceMeters) = 0;
    virtual double calculateBearing(double lat1, double lon1, double lat2, double lon2) = 0;
    virtual std::string encodeGeohash(double latitude, double longitude, double precision) = 0;

  protected:
    static constexpr auto TAG = "NitroLocationComplexLogicsCalculation";
  };
}
