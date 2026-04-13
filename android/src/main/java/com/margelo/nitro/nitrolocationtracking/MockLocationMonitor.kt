package com.margelo.nitro.nitrolocationtracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Periodically checks whether mock/fake GPS is actively being used on the device.
 * Works independently of location tracking — can be used on login/verify screens.
 *
 * Detection strategy:
 * - Pre-API 23: Settings.Secure.ALLOW_MOCK_LOCATION
 * - API 23+: Requests a single location via FusedLocationProviderClient and checks
 *   Location.isMock / isFromMockProvider. This is the only reliable way to know
 *   if mock location is currently ACTIVE (not just if a mock app is installed).
 *
 * The callback fires only when the state changes (deduplicated).
 */
class MockLocationMonitor(private val context: Context) {

    companion object {
        private const val TAG = "MockLocationMonitor"
        private const val DEFAULT_POLL_INTERVAL_MS = 3000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var callback: ((Boolean) -> Unit)? = null
    private var lastState: Boolean? = null
    private var polling = false
    private var pollIntervalMs = DEFAULT_POLL_INTERVAL_MS

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            checkMockLocation()
            mainHandler.postDelayed(this, pollIntervalMs)
        }
    }

    fun setCallback(callback: (Boolean) -> Unit) {
        this.callback = callback
        // Kick off an immediate check
        checkMockLocation()
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

    private fun notifyIfChanged(isMock: Boolean) {
        if (isMock != lastState) {
            lastState = isMock
            Log.d(TAG, "Mock location state changed: isMockEnabled=$isMock")
            callback?.invoke(isMock)
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkMockLocation() {
        // Pre-API 23: use the global settings flag
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val mockSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION
            )
            notifyIfChanged(mockSetting == "1")
            return
        }

        // API 23+: request a current location and check isMock flag
        try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(5000)
                .build()

            fusedClient.getCurrentLocation(request, null)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val isMock = isLocationMock(location)
                        mainHandler.post { notifyIfChanged(isMock) }
                    } else {
                        // No location available — try last location as fallback
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null) {
                                val isMock = isLocationMock(lastLoc)
                                mainHandler.post { notifyIfChanged(isMock) }
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to get current location for mock check: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "No location permission for mock check: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Error checking mock location: ${e.message}")
        }
    }

    private fun isLocationMock(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    fun destroy() {
        stopPolling()
        callback = null
        lastState = null
    }
}
