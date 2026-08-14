---
sidebar_position: 3
---

# Live Push

The JS thread is suspended when the screen is off or the app is backgrounded,
so a `fetch()` driven by `onLocation` stops firing. **Live Push** moves the
per-fix HTTP POST onto the native thread — which the location foreground
service keeps alive — so the server keeps receiving positions in the
background and in Doze.

It's generic by design: the lib owns the transport, your app owns the schema.
Each POST body is the parsed `extraFieldsJson` object with the location fields
merged on top. Config is pushed down from JS on rare events (login, token
refresh, new delivery); the native side then handles every fix on its own.

A fix is sent only when all three gates are open: **tracking is running** (no
fix otherwise) **and** push is **enabled** **and** a **config** is set. A
`401` response auto-clears the config until JS re-configures with a fresh
token.

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';
import type { LivePushConfig } from 'react-native-nitro-location-tracking';

// 1. Configure — on login / token refresh / new delivery (while JS is awake):
const config: LivePushConfig = {
  url: 'https://api.example.com/tracking', // full endpoint
  authToken: token, // sent as `Authorization: Bearer <token>`
  // Static fields merged into every POST body. A JSON string, so values may be
  // string / number / bool / null. The location fields are added on top.
  extraFieldsJson: JSON.stringify({ courier_id: 'c1', delivery_id: null, active: true }),
  // false → body = { latitude, longitude, timestamp, ...extraFields }
  // true  → also include speed, bearing, accuracy, altitude
  includeFullPoint: false,
  // Opt-in durable offline queue (default false). See "Durable queue" below.
  persistQueue: true,
  batchSize: 5, // POST up to 5 fixes per request (body becomes a JSON array)
  batchMaxDelayMs: 3000, // flush a partial batch after 3s
  maxQueueSize: 10000, // cap; oldest fixes dropped beyond this
};
NitroLocationModule.configureLivePush(config);

// 2. Toggle at runtime — cheap on/off that keeps config (duty / online state):
NitroLocationModule.setLivePushEnabled(true);
NitroLocationModule.setLivePushEnabled(false);

// 3. Clear — on logout (wipes config and disables):
NitroLocationModule.clearLivePush();

// Optional: observe each POST outcome (foreground only — see caveat below):
NitroLocationModule.onLivePushResult((r) => {
  if (!r.ok) {
    console.warn('live push failed', r.statusCode, r.error);
    // e.g. on 401, re-authenticate and call configureLivePush again
  }
});
```

:::info onLivePushResult outcomes are buffered across suspension
The POST keeps firing natively while the JS thread is suspended (screen off /
backgrounded / Doze). Outcomes that land during suspension are held in a
small native ring buffer (most-recent outcomes win) and replayed in order the
next time a callback is registered / JS resumes, so a "last sync OK / 401"
indicator stays reliable. The buffer is bounded, so this is foreground
observability — for guaranteed per-fix delivery, enable `persistQueue`.
`statusCode` is the HTTP code (or `0` for a network error / timeout); `error`
is `''` on success.
:::

## Durable queue (`persistQueue`)

By default Live Push is **fire-and-forget**: one POST per fix, dropped on
failure, and the next successful push corrects the server-side position.
That's fine for "where is the courier right now" but loses fixes captured
with no connectivity.

Set `persistQueue: true` to turn on the **durable offline queue**:

- Every fix is written to a native **SQLite** queue before sending.
- A background drainer POSTs the oldest fixes first, **deletes a row only on
  a 2xx** response, and **retries with exponential backoff** (2s → 60s) on
  network errors or `5xx` / `429`. Non-retryable codes (`400` / `401` /
  `413`) drop the offending rows so a poison payload can't wedge the queue.
- The queue **survives app kill / reboot** — fixes recorded offline are
  delivered when connectivity returns.
- **Batching:** `batchSize > 1` sends up to N fixes in one request. The body
  then becomes a **JSON array** of the per-fix objects (your endpoint must
  accept an array); `batchMaxDelayMs` bounds how long a partial batch waits
  before flushing.
- **Back-pressure:** `maxQueueSize` (default 10000) caps the DB; the oldest
  fixes are dropped beyond it during long outages.
- Call `forceSync()` to drain on demand (resolves `true` when the queue is
  empty), and `getQueuedCount()` to read how many fixes are still pending.
