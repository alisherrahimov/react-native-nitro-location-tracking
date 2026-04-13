package com.margelo.nitro.nitrolocationtracking

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Periodically polls whether a mock/fake GPS provider is active on the device.
 * This works independently of location tracking — it detects the presence of
 * mock location apps (e.g. Fake GPS, Mock Locations) at the system level.
 *
 * On each poll it checks:
 * - Pre-API 23: Settings.Secure.ALLOW_MOCK_LOCATION
 * - API 23+: AppOpsManager OP_MOCK_LOCATION (op code 58)
 * - Also checks all installed packages for mock location permission
 *
 * The callback fires only when the state changes (deduplicated).
 */
class MockLocationMonitor(private val context: Context) {

    companion object {
        private const val TAG = "MockLocationMonitor"
        /** Default poll interval in milliseconds */
        private const val DEFAULT_POLL_INTERVAL_MS = 3000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: ((Boolean) -> Unit)? = null
    private var lastState: Boolean? = null
    private var polling = false
    private var pollIntervalMs = DEFAULT_POLL_INTERVAL_MS

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            checkAndNotify()
            mainHandler.postDelayed(this, pollIntervalMs)
        }
    }

    fun setCallback(callback: (Boolean) -> Unit) {
        this.callback = callback
        // Emit current state immediately
        val current = isMockLocationActive()
        lastState = current
        callback.invoke(current)
        // Start polling
        startPolling()
    }

    fun startPolling() {
        if (polling) return
        polling = true
        mainHandler.postDelayed(pollRunnable, pollIntervalMs)
        Log.d(TAG, "Mock location polling started (interval=${pollIntervalMs}ms)")
    }

    fun stopPolling() {
        polling = false
        mainHandler.removeCallbacks(pollRunnable)
        Log.d(TAG, "Mock location polling stopped")
    }

    private fun checkAndNotify() {
        val current = isMockLocationActive()
        if (current != lastState) {
            lastState = current
            Log.d(TAG, "Mock location state changed: isMockEnabled=$current")
            callback?.invoke(current)
        }
    }

    /**
     * Comprehensive check for mock location activity on the device.
     */
    @SuppressLint("DiscouragedPrivateApi")
    fun isMockLocationActive(): Boolean {
        // Pre-API 23: check the global mock location setting
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val mockSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION
            )
            return mockSetting == "1"
        }

        // API 23+: scan ALL installed packages for OP_MOCK_LOCATION permission.
        // The previous approach only checked our own UID which always returned false
        // since the mock location app (not ours) holds the permission.
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val opMockLocation = 58 // OP_MOCK_LOCATION
            val checkOp = AppOpsManager::class.java.getMethod(
                "checkOp",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )

            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val packages = pm.getInstalledApplications(0)
            for (appInfo in packages) {
                // Skip our own package
                if (appInfo.packageName == context.packageName) continue
                try {
                    val result = checkOp.invoke(
                        appOps, opMockLocation, appInfo.uid, appInfo.packageName
                    ) as Int
                    if (result == AppOpsManager.MODE_ALLOWED) {
                        Log.d(TAG, "Mock location provider detected: ${appInfo.packageName}")
                        return true
                    }
                } catch (_: Exception) {
                    // Some packages may not be queryable — skip
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check mock location app ops: ${e.message}")
        }

        // Fallback: check for known mock location app package names
        try {
            val pm = context.packageManager
            val knownMockApps = listOf(
                "com.lexa.fakegps",
                "com.incorporateapps.fakegps.fre",
                "com.fakegps.mock",
                "com.lkr.fakegps",
                "com.fake.gps.go.location.spoofer.free",
                "com.theappninjas.gpsjoystick",
                "com.evezzon.fgl",
                "com.mock.location"
            )
            for (pkg in knownMockApps) {
                try {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                    Log.d(TAG, "Known mock location app detected: $pkg")
                    return true
                } catch (_: Exception) {
                    // Not installed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check for mock location apps: ${e.message}")
        }

        return false
    }

    fun destroy() {
        stopPolling()
        callback = null
        lastState = null
    }
}
