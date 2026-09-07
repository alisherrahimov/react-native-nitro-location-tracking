# Changelog

## [0.1.31](https://dev.azure.com/bellissimouz/Bellissimo/_git/compare/v0.1.30...v0.1.31) (2026-09-07)


* fix(ios)!: rename CellularGeneration values to valid Swift identifiers ([68789c0](https://dev.azure.com/bellissimouz/Bellissimo/_git/commits/68789c0d8a045b1ac519085efdba912a18bc9663)), closes [String#untaint](https://dev.azure.com/String/issues/untaint)


### Bug Fixes

* **android:** refuse startTracking() when the app is backgrounded ([b614eb6](https://dev.azure.com/bellissimouz/Bellissimo/_git/commits/b614eb65a876ffcec65d37cf9e05999dd353aa52))
* **build:** exclude docs from the publish-time type build ([2841f61](https://dev.azure.com/bellissimouz/Bellissimo/_git/commits/2841f61805bb2cc98f6d1e25fb1dc24b629455ab))


### Features

* **docs:** initialize Docusaurus documentation site with essential components and styles ([bd6a67f](https://dev.azure.com/bellissimouz/Bellissimo/_git/commits/bd6a67fa944e07e3fef104895e9f3eec20250e02))


### BREAKING CHANGES

* NetworkStatus.generation now reports 'gen2' | 'gen3' | 'gen4' |
'gen5' | 'unknown' instead of '2g' | '3g' | '4g' | '5g' | 'unknown'. Android
consumers reading status.generation must update their comparisons; no iOS
consumer can be affected, since this enum never compiled on iOS.

Also excludes cpp/__tests__ from the podspec source globs -- the stubs there
redefine HybridObject and broke the example build once the Swift errors were out
of the way. Consumers were never exposed to it, since the npm files field
already strips __tests__.

The example's bundler setup is repaired alongside it: the lockfile pinned
BUNDLED WITH 1.17.2, which made bundler switch to a vendored copy that calls

## 0.1.30 (2026-08-14)

### Bug Fixes

* **android:** stop `ForegroundServiceDidNotStartInTimeException` crashes in `LocationForegroundService` ([88d5cbf](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/88d5cbf6fe82ee3aa0df8444d61e3c90b35f14c3))

  Three independent paths reached the watchdog. The promotion notification was
  built *before* the `startForeground` try block, and `PendingIntent.getActivity()`
  throws on the `null` that `getLaunchIntentForPackage()` returns while a package
  is being replaced — so an app update could throw straight out of `onCreate`
  with the service never promoted. Promotion now uses a notification that cannot
  throw (static strings, no `PendingIntent`, no `PackageManager`); the full
  notification with a tap target is applied afterwards, with its launch intent
  null-checked.

  The `SHORT_SERVICE` fallback added in 0.1.29 could never run: API 34+ rejects
  any type not declared in the manifest, and only `location` was declared, so the
  fallback threw and the watchdog stayed armed. The service now declares
  `location|shortService`.

  `onTimeout(startId)` is implemented, required by the `SHORT_SERVICE` contract
  now that the fallback can actually take effect.

* **android:** return `START_NOT_STICKY` from `LocationForegroundService` ([88d5cbf](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/88d5cbf6fe82ee3aa0df8444d61e3c90b35f14c3))

  A system-initiated restart lands while the app is in the background, where
  Android 12+ forbids promoting a `location`-typed foreground service — the
  promotion failed, the watchdog fired, and the process crashed, repeatedly.

  **Behaviour change:** tracking no longer revives itself after a process kill.
  Apps that relied on the sticky restart must call `startTracking()` again when
  the app returns to the foreground and the location permission is granted.

### Features

* add network monitoring for cellular generation and transport changes ([88d5cbf](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/88d5cbf6fe82ee3aa0df8444d61e3c90b35f14c3))

  New `startNetworkMonitoring()`, `stopNetworkMonitoring()`, `getNetworkStatus()`
  and `onNetworkChange()` on both platforms, reporting transport, cellular
  generation, radio technology, and the expensive/constrained flags. Adds the
  `ACCESS_NETWORK_STATE` permission on Android.

## 0.1.29 (2026-08-12)

> Released to npm and tagged without a changelog entry; reconstructed here from
> the commit range `v0.1.28..v0.1.29`.

### Bug Fixes

* **cpp:** avoid signed/unsigned comparison in the geohash precision loop ([e8de625](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/e8de625))

### Features

* wire up odometer, ETA, adaptive accuracy, Kalman filtering, durable Live Push queue, geofence dwell/persistence, OEM battery whitelisting, and airplane-mode APIs ([44559dd](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/44559dd))
* **android:** enhance foreground service handling and notification updates ([953d5fa](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/953d5fa))
* **android:** add OEM battery optimization and auto-start whitelisting ([4bf06b1](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/4bf06b1))
* **android:** persist geofences and re-arm monitoring across reboot ([e43bc43](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/e43bc43))
* **android,ios:** add durable SQLite-backed offline queue for Live Push ([f1767d2](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/f1767d2))
* **android,ios:** add native Kalman filter for live location smoothing ([d007264](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/d007264))
* **android,ios:** add motion-activity state machine ([0c4ce1c](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/0c4ce1c))
* add pluggable snap-to-road buffering hook ([ae5902b](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/ae5902b))

## 0.1.28 (2026-07-02)

> Baseline entry. Versions 0.1.0–0.1.28 were published to npm without git tags,
> so this first entry summarizes the full commit history up to this release in
> one block rather than a per-version breakdown. From the next release onward,
> each version gets its own dated entry generated automatically.

### Bug Fixes

* update activity result parameters for GPS resolution request handling ([d1d9639](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/d1d9639786bcd701c7efd43a28efc0c44799b8cd))
* update initialization syntax for TripMathStats in calculateTotalTripStats ([e75b548](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/e75b54829055f23608e7f32843db94606d177fc1))

### Features

* Add  API and improve iOS one-shot location request handling. ([97a5d2b](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/97a5d2ba126952ba24d307061ac8ef98b90fc18d))
* add cpp autolinking for NitroLocationComplexLogicsCalculation ([2af41ce](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/2af41ce0bd372ead40ce62d71d4cf2fe6be61c48))
* add cross-platform mock location detection monitor for iOS and Android ([7084fa8](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/7084fa8bdd35703df2a522146d36c2bd35008ff3))
* add initial implementation of CourierWidget with supporting assets and configurations ([43b53ff](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/43b53fff773e26032289d6d9ca7fb10614b6042c))
* Add Live Activity support for delivery tracking ([4b49e96](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/4b49e962c33627df2526af8597dd55fef85ae60d))
* add location calculation utilities and device state monitoring for airplane mode ([c775931](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/c775931c8a3c829cfc8ffa3594377ce6c31f28f5))
* add native distance utility methods for coordinate pairs and geofence proximity ([c77cbc5](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/c77cbc5250a2303d3ffe937aa68516358e99e317))
* add onLivePushResult callback for observing POST outcomes and update LivePushResult interface ([e89341b](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/e89341ba69a66b90c662b9ec82f858fb2c8d3a34))
* add onPermissionStatusChange listener for real-time location permission monitoring ([4ea78e6](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/4ea78e635d6c641d1dfbb1ec6ae1dc45ace72f84))
* add permission request support and implement status change deduplication with logging in example app ([b6fac67](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/b6fac67e17fc5708a0d31bbe63a3fa8340e2c736))
* add platform LocationManager fallback for location updates and update version to 0.1.23 ([19a9c21](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/19a9c213b2db32ef2e9c3b2f000b8046d559a4c3))
* Add speed monitoring, trip calculation, geofencing, mock location detection, and provider status monitoring features. ([417f36f](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/417f36fb01a4237c385ca12b57d9d043251359a7))
* enhance foreground service promotion with error handling and location type support ([0d0e592](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/0d0e59203048507a78642c82753ea282646ce755))
* enhance location tracking with improved callback handling and logging ([e7505cd](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/e7505cd42867d9d4031dccdad258c73eda19cd08))
* enhance mock location detection by scanning all installed packages for OP_MOCK_LOCATION permission ([4843287](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/4843287841736620c8268e9efa8ed71b8b8127d4))
* implement GPS enabling prompt and enhance permission handling across platforms ([4280af7](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/4280af7c0e2b3ea4f1d3962efc10d5d821913777))
* implement mock location detection and update UI to reflect state changes ([ce57b12](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/ce57b1296679eee025fa5caa9cc74e8b06ea1976))
* implement native live push for background location updates ([b4786bd](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/b4786bd18200b51f3bdebde9e34b502f4b9d6ab8))
* refactor location tracking and WebSocket connection handling to use NitroLocationModule directly ([476aea2](https://github.com/alisherrahimov/react-native-nitro-location-tracking/commit/476aea2f7f95597e5879d2b7fce44cba23108166))
