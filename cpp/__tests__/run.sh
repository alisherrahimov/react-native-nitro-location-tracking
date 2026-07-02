#!/usr/bin/env bash
#
# Build & run the standalone C++ unit test for the geo-math implementation.
# Usage: bash cpp/__tests__/run.sh   (or: yarn test:cpp)
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CPP_DIR="$(cd "$HERE/.." && pwd)"
OUT="$HERE/.build"
mkdir -p "$OUT"

CXX="${CXX:-clang++}"

"$CXX" -std=c++20 -Wall -Wextra \
  -I "$HERE/stubs" \
  -I "$CPP_DIR" \
  "$HERE/test_complex_logics.cpp" \
  "$CPP_DIR/HybridNitroLocationComplexLogicsCalculation.cpp" \
  -o "$OUT/test_complex_logics"

"$OUT/test_complex_logics"
