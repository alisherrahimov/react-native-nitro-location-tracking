#pragma once
//
// Test-only stub of the nitrogen-generated `LocationPoint` struct.
//
// The real header (nitrogen/generated/shared/c++/LocationPoint.hpp) pulls in the
// full NitroModules / JSI stack for the JS<->C++ converters. For a standalone
// unit test of the pure algorithm code we only need the plain data struct, so we
// shadow it here via the include path (-I cpp/__tests__/stubs). The struct layout
// and field names match the generated version exactly.
//
namespace margelo::nitro::nitrolocationtracking {
  struct LocationPoint final {
    double latitude;
    double longitude;
    double timestamp;
    double speed;
    double accuracy;
    LocationPoint() = default;
    explicit LocationPoint(double latitude, double longitude, double timestamp, double speed, double accuracy)
      : latitude(latitude), longitude(longitude), timestamp(timestamp), speed(speed), accuracy(accuracy) {}
    friend bool operator==(const LocationPoint&, const LocationPoint&) = default;
  };
}
