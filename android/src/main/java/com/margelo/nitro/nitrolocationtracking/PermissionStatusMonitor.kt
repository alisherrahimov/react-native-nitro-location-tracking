package com.margelo.nitro.nitrolocationtracking

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Monitors location‐permission changes by comparing the permission status
 * every time the app comes to the foreground (`ON_START`).
 *
 * Android has no broadcast for permission changes, so consuming the
 * lifecycle is the standard approach (same as react-native-permissions).
 */
class PermissionStatusMonitor(private val context: Context) {

    companion object {
        private const val TAG = "PermissionStatusMonitor"
    }

    private var callback: ((PermissionStatus) -> Unit)? = null
    private var lastStatus: PermissionStatus? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            checkAndNotify()
        }
    }

    fun setCallback(callback: (PermissionStatus) -> Unit) {
        this.callback = callback
        // Capture the current status so we only fire on actual changes
        lastStatus = getCurrentPermissionStatus()

        // addObserver MUST be called on the main thread
        mainHandler.post {
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
                Log.d(TAG, "Registered lifecycle observer for permission changes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register lifecycle observer: ${e.message}")
            }
        }
    }

    private fun checkAndNotify() {
        val current = getCurrentPermissionStatus()
        if (current != lastStatus) {
            Log.d(TAG, "Permission status changed: $lastStatus -> $current")
            lastStatus = current
            callback?.invoke(current)
        }
    }

    private fun getCurrentPermissionStatus(): PermissionStatus {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            return PermissionStatus.DENIED
        }

        // Fine location granted — check background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bgGranted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return if (bgGranted) PermissionStatus.ALWAYS else PermissionStatus.WHENINUSE
        }

        // Pre-Android 10: fine location = always
        return PermissionStatus.ALWAYS
    }

    fun destroy() {
        mainHandler.post {
            try {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
            } catch (_: Exception) {}
        }
        callback = null
        lastStatus = null
    }
}
