---
sidebar_position: 1
---

# Introduction

**react-native-nitro-location-tracking** is a high-performance React Native
location tracking library built with [Nitro Modules](https://nitro.margelo.com/).
It's designed for ride-hailing, delivery, and fleet tracking apps that need
background location, real-time connectivity, foreground service notifications,
and smooth map marker animations — all with near-native performance via JSI.

## Features

- **Background location tracking** with a foreground service (Android) and
  background modes (iOS)
- **WebSocket connection manager** with auto-reconnect and batch sync
- **Native Live Push** — per-fix HTTP POST sent from the native thread, so the
  server keeps receiving the courier's position even while the screen is off,
  the app is backgrounded, or the device is in Doze (no dependency on the JS
  thread)
- **Fake GPS detection** — detect mock locations and optionally reject them
- **Geofencing** — durable enter / exit / dwell events for circular regions
- **Speed monitoring** — configurable speed alerts with state-transition
  callbacks
- **Distance calculator** — running trip stats with Haversine distance
- **Odometer & ETA** — persisted trip odometer and straight-line ETA
- **Location provider status** — detect when GPS/location is turned on/off
- **Smooth map marker animations** via `LocationSmoother`
- **Bearing calculation** utilities for rotation/heading
- **Foreground notifications** (Android foreground service, iOS local
  notifications)
- **Live Activity / Dynamic Island** (iOS 16.2+) — a delivery card on the Lock
  Screen and Dynamic Island that updates in real time, with an Android ongoing
  notification equivalent
- **Network monitoring** — cellular generation and transport change events
- **Permission helpers** for fine, background, and notification permissions
- **Built on Nitro Modules** for near-native performance via JSI

## Design philosophy

**Single entry point.** Everything runs through the default export,
`NitroLocationModule` — the native HybridObject. There are no built-in React
hooks; wire callbacks into your own component state with `useState` /
`useEffect`. This keeps one source of truth and avoids hidden lifecycle magic.

Head to [Installation](./installation) to get set up, or jump straight to the
[API Reference](./api/types) if you already know what you're looking for.
