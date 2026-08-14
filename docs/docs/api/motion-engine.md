---
sidebar_position: 2
---

# Adaptive Accuracy & Live Filtering

## Adaptive accuracy & the motion engine

`onMotionChange(callback)` reports whether the device is **moving** or
**stationary**. It is backed by a motion state machine, not a raw speed
check.

**How motion is detected (with graceful fallback):**

1. **OS activity recognition** (preferred) — Activity Recognition on
   Android, Core Motion "Motion & Fitness" on iOS. This is what lets the
   library know the device is parked even when GPS still reports small
   jitter.
2. **Speed-based fallback** — if the activity permission is missing or
   unavailable, motion is inferred from the location stream's speed. No
   extra permission required; slightly less precise.

Transitions are **debounced** by `stopTimeout` (minutes): the device must be
continuously still for that long before it is declared stationary, which
avoids flapping at traffic lights.

**Adaptive accuracy** (`adaptiveAccuracy: true`, default `false`): when the
motion engine reports stationary, the library drops GPS to low power (longer
interval, larger distance filter / coarser accuracy) and restores your
configured `desiredAccuracy` the moment it starts moving again — saving
battery on long idle stretches. It's opt-in so existing integrations keep
their current behavior.

**Permissions required for OS activity recognition:**

| Platform | Requirement |
| -------- | ----------- |
| Android  | `ACTIVITY_RECOGNITION` (added by the library's manifest; **runtime-requestable on API 29+** — request it in your app, or the engine silently uses the speed fallback) |
| iOS      | `NSMotionUsageDescription` in your app's `Info.plist` (or the engine uses the speed fallback) |

:::note
Without these, tracking and `onMotionChange` still work — they just fall
back to speed-based motion detection. Adaptive accuracy still functions on
top of whichever motion source is active.
:::

## Live location filtering (Kalman + accuracy gate)

Raw GPS is noisy: the position "dances" a few meters while you stand still,
and occasionally spikes far away near buildings/tunnels. Two opt-in, native
filters clean the **live stream** before it reaches JS, Live Push, or trip
stats — no post-processing needed.

```tsx
NitroLocationModule.configure({
  // ...other config
  accuracyFilter: 50, // drop fixes reporting worse than 50 m accuracy
  kalmanFilter: true, // smooth the survivors
  kalmanProcessNoiseMps: 1.0, // ~walking/driving; higher = less smoothing
});
```

- **`accuracyFilter`** (meters, default `0` = off) — a cheap gate that
  discards low-confidence fixes outright before anything downstream sees
  them.
- **`kalmanFilter`** (default `false`) — runs each surviving fix through a
  position **Kalman filter** that weights it by its reported accuracy, so
  jitter is smoothed and outlier spikes are dampened. **This changes the
  emitted `latitude`/`longitude`** — they become the filtered estimate, not
  the raw chip output. Speed and bearing are left as reported.
- **`kalmanProcessNoiseMps`** (m/s, default `1.0`) — tuning knob: higher
  trusts new fixes more (more responsive, less smoothing), lower trusts the
  motion model more (smoother, laggier).

The filter is rebuilt each time tracking starts, so it never carries stale
state into a new session. The odometer, speed monitor, and trip calculator
all consume the filtered stream when these are enabled.
