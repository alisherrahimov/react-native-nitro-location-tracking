package com.margelo.nitro.nitrolocationtracking

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helpers for surviving OEM background-execution restrictions on Android.
 *
 * Two independent mechanisms kill background services:
 *  1. **Doze / battery optimization** (stock Android) — handled by
 *     [isIgnoringBatteryOptimizations] / [buildIgnoreBatteryOptimizationsIntent].
 *  2. **OEM auto-start / "protected apps"** (Xiaomi/MIUI, Huawei/EMUI,
 *     Oppo·Realme/ColorOS, Vivo/FuntouchOS, Samsung, …) — handled by
 *     [buildAutoStartIntent], which targets known settings Activities per vendor.
 *
 * The OEM Activity components are undocumented and change across skin versions,
 * so [buildAutoStartIntent] returns the first candidate that actually resolves
 * on this device and falls back to the app's own system details page.
 */
object OemBatteryHelper {

    private const val TAG = "OemBatteryHelper"

    fun manufacturer(): String = (Build.MANUFACTURER ?: "").lowercase()

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Intent for the battery-optimization exemption.
     *
     * Prefers the one-tap per-app dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`),
     * which requires the host app to declare the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
     * permission. If that permission isn't held, returns the permission-free
     * settings-list intent instead so the flow still works.
     */
    fun buildIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            hasPermission(context, "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
        ) {
            return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
        }
        // Permission-free fallback: the full battery-optimization list.
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    /**
     * Best-effort intent for the OEM auto-start / protected-apps screen. Returns
     * a resolvable vendor Activity when known, else the app details settings page
     * (which always exists) so the user still lands somewhere useful.
     */
    fun buildAutoStartIntent(context: Context): Intent {
        for (component in autoStartComponentsFor(manufacturer())) {
            val intent = Intent().setComponent(component).addCategory(Intent.CATEGORY_DEFAULT)
            if (resolves(context, intent)) return intent
        }
        Log.d(TAG, "no known OEM auto-start screen for '${manufacturer()}' — using app details")
        return appDetailsIntent(context)
    }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))

    private fun autoStartComponentsFor(mfr: String): List<ComponentName> = when {
        mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
        )
        mfr.contains("huawei") || mfr.contains("honor") -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
        )
        mfr.contains("oppo") || mfr.contains("realme") -> listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
        )
        mfr.contains("vivo") || mfr.contains("iqoo") -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
        )
        mfr.contains("samsung") -> listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
        )
        mfr.contains("oneplus") -> listOf(
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        )
        mfr.contains("letv") -> listOf(
            ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
        )
        else -> emptyList()
    }

    private fun resolves(context: Context, intent: Intent): Boolean =
        intent.resolveActivity(context.packageManager) != null

    private fun hasPermission(context: Context, permission: String): Boolean =
        try {
            context.packageManager
                .getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.contains(permission) == true
        } catch (e: Exception) {
            Log.w(TAG, "permission check failed: ${e.message}")
            false
        }
}
