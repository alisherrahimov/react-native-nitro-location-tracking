---
sidebar_position: 13
---

# Background Reliability on Android OEMs

On many Android skins — Xiaomi/MIUI, Huawei/EMUI, Honor, Oppo·Realme/ColorOS,
Vivo, Samsung, OnePlus — the system aggressively kills background services
unless the app is **exempt from battery optimization** and **allowed to
auto-start**. Without this, background location tracking silently stops
after a while, even with a foreground service. These helpers let you detect
the state and prompt the user to fix it. **All are Android-only; iOS returns
the safe defaults noted below.**

```tsx
import NitroLocationModule from 'react-native-nitro-location-tracking';

async function ensureBackgroundReliable() {
  // 1. Battery optimization (stock Android / Doze).
  if (!NitroLocationModule.isIgnoringBatteryOptimizations()) {
    await NitroLocationModule.requestIgnoreBatteryOptimizations();
  }

  // 2. OEM auto-start / "protected apps" — only worth prompting on known OEMs.
  const oem = NitroLocationModule.getDeviceManufacturer();
  if (['xiaomi', 'redmi', 'poco', 'huawei', 'honor', 'oppo', 'realme', 'vivo']
        .some((m) => oem.includes(m))) {
    await NitroLocationModule.openOemAutoStartSettings();
  }
}
```

**Behavior & platform notes:**

- `getDeviceManufacturer()` — lowercased `Build.MANUFACTURER` (e.g.
  `"xiaomi"`); `"apple"` on iOS.
- `isIgnoringBatteryOptimizations()` — reflects
  `PowerManager.isIgnoringBatteryOptimizations`; always `true` on iOS.
- `requestIgnoreBatteryOptimizations()` — resolves with the resulting state
  **after the user returns**. If your app declares
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, it shows the one-tap system dialog;
  otherwise it opens the battery-optimization settings list. iOS resolves
  `true`.
- `openOemAutoStartSettings()` — opens the vendor auto-start screen
  (best-effort; OEM Activities are undocumented and vary by version),
  falling back to the app's details page. Resolves `true` if a screen
  opened; iOS resolves `false`.

:::info One-tap battery dialog (optional)
Google Play restricts `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to apps with a
qualifying background use case (background location qualifies) and requires
a Play Console declaration. The library does **not** bundle this permission
— add it to your app's manifest if you want the one-tap dialog. Without it,
`requestIgnoreBatteryOptimizations()` still works by opening the settings
list.

```xml
<!-- Optional: enables the one-tap battery-exemption dialog. Requires a Play
     Console declaration. Omit to use the settings-list fallback. -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```
:::
