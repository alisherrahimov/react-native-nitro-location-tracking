---
sidebar_position: 12
---

# Network Monitoring

Monitor cellular generation and transport changes — useful for adapting
Live Push batching/backoff, or surfacing connectivity state to the driver.

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';
import type { NetworkStatus } from 'react-native-nitro-location-tracking';

// Idempotent — calling twice does not double-subscribe or double-emit.
NitroLocationModule.startNetworkMonitoring();

// Synchronous snapshot for cold reads.
const status: NetworkStatus = NitroLocationModule.getNetworkStatus();
console.log(status.transport, status.generation, status.radioTechnology);

// Fires only on change (native dedupes against the last emitted status),
// plus once immediately on startNetworkMonitoring() with the current state.
NitroLocationModule.onNetworkChange((status) => {
  console.log(`Network: ${status.transport} (${status.generation})`);
  if (status.isExpensive) {
    console.log('On cellular / hotspot — consider larger Live Push batches');
  }
});

NitroLocationModule.stopNetworkMonitoring();
```

## `NetworkStatus`

```ts
interface NetworkStatus {
  transport: 'cellular' | 'wifi' | 'ethernet' | 'other' | 'none';
  // 'unknown' whenever transport !== 'cellular', or when the OS won't say.
  generation: 'gen2' | 'gen3' | 'gen4' | 'gen5' | 'unknown';
  // Raw OS radio string, for diagnostics/Sentry.
  // iOS: CTRadioAccessTechnology*. Android: TelephonyManager network-type name.
  radioTechnology: string;
  // Cellular or personal hotspot.
  isExpensive: boolean;
  // iOS Low Data Mode. Always false on Android.
  isConstrained: boolean;
}
```
