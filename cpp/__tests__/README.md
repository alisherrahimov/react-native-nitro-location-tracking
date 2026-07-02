# C++ unit tests

Standalone tests for the pure-C++ geo-math in
[`../HybridNitroLocationComplexLogicsCalculation.cpp`](../HybridNitroLocationComplexLogicsCalculation.cpp).

They compile the **real** implementation file — no copy, no reimplementation —
against lightweight stubs of the nitrogen-generated types (`stubs/`), so the
algorithms run without the NitroModules / JSI runtime, a simulator, or a device.

## Run

```sh
yarn test:cpp
# or directly:
bash cpp/__tests__/run.sh
```

Requires a C++20 compiler (`clang++` by default; override with `CXX=g++`).
Exits non-zero if any assertion fails, so it is CI-friendly.

## What is and isn't covered

- **Covered:** the algorithm layer — `calculateBearing`, `calculateTotalTripStats`
  (Haversine), `filterAnomalousPoints`, `smoothPath` (Douglas–Peucker), and
  `encodeGeohash`, including edge cases (empty input, precision clamping, etc.).
- **Not covered:** the JS ⇄ C++ JSI marshalling, which is Nitro's generated glue
  (not code in this repo) and only exercises at runtime in the app.

## Files

- `test_complex_logics.cpp` — the assertions / test harness.
- `stubs/` — minimal stand-ins for the generated `LocationPoint`, `TripMathStats`,
  and the abstract spec base class. Field layout and method signatures mirror the
  generated headers so the real implementation compiles unmodified.
- `run.sh` — builds and runs the test. Build artifacts land in `.build/`.

This folder is excluded from the published npm package via the `files` field in
`package.json` (`!**/__tests__`).
