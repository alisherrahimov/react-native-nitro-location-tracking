package com.margelo.nitro.nitrolocationtracking

import android.content.Context
import android.database.ContentObserver
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Monitors GPS / network location provider status changes using a ContentObserver
 * on Settings.Secure.LOCATION_MODE.
 *
 * Why ContentObserver instead of BroadcastReceiver?
 * Several OEMs (notably Samsung) suppress PROVIDERS_CHANGED_ACTION broadcasts,
 * making BroadcastReceiver unreliable.  ContentObserver watches the Settings DB
 * directly and fires on all devices.
 *
 * Implementation notes:
 * - ContentObserver.onChange fires BEFORE the LocationManager reflects the new
 *   state, so we post a short delayed read (150 ms) to get the correct values.
 * - onChange can fire multiple times per toggle; we debounce with a pending
 *   Runnable and deduplicate by comparing with lastGps / lastNetwork.
 */
class ProviderStatusMonitor(private val context: Context) {

    companion object {
        private const val TAG = "ProviderStatusMonitor"
        /** Delay (ms) to let the system apply the setting before we read it. */
        private const val READ_DELAY_MS = 150L
    }

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var callback: ((LocationProviderStatus, LocationProviderStatus) -> Unit)? = null
    private var contentObserver: ContentObserver? = null
    private var pendingNotify: Runnable? = null

    // Track last-notified values to avoid duplicate callbacks
    private var lastGps: LocationProviderStatus? = null
    private var lastNetwork: LocationProviderStatus? = null

    fun setCallback(callback: (LocationProviderStatus, LocationProviderStatus) -> Unit) {
        this.callback = callback
        registerObserver()
        // Emit current status immediately (no delay needed — values are already settled)
        emitCurrentStatus()
    }

    fun isLocationServicesEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    // ── Observer registration ────────────────────────────────────────────

    private fun registerObserver() {
        if (contentObserver != null) return

        contentObserver = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                scheduleNotify()
            }
        }

        // LOCATION_MODE is the single authoritative setting for the global
        // location toggle (GPS + Network).  Watching only this avoids the
        // double-fire that occurs when observing LOCATION_PROVIDERS_ALLOWED too.
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.LOCATION_MODE),
            false,
            contentObserver!!
        )

        Log.d(TAG, "ContentObserver registered")
    }

    // ── Debounced + delayed notification ─────────────────────────────────

    /**
     * Schedule a delayed read of the provider status.
     * If onChange fires multiple times in quick succession, the previous
     * pending Runnable is cancelled so we only read once after things settle.
     */
    private fun scheduleNotify() {
        pendingNotify?.let { mainHandler.removeCallbacks(it) }

        val runnable = Runnable { emitCurrentStatus() }
        pendingNotify = runnable
        mainHandler.postDelayed(runnable, READ_DELAY_MS)
    }

    /**
     * Read the current GPS + Network provider state and invoke the callback
     * only if the values actually changed since the last notification.
     */
    private fun emitCurrentStatus() {
        val gps = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            LocationProviderStatus.ENABLED else LocationProviderStatus.DISABLED
        val network = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            LocationProviderStatus.ENABLED else LocationProviderStatus.DISABLED

        // Deduplicate — only fire if something actually changed
        if (gps == lastGps && network == lastNetwork) return

        lastGps = gps
        lastNetwork = network
        Log.d(TAG, "Provider status changed: GPS=$gps, Network=$network")
        callback?.invoke(gps, network)
    }

    // ── Cleanup ──────────────────────────────────────────────────────────

    fun destroy() {
        pendingNotify?.let { mainHandler.removeCallbacks(it) }
        pendingNotify = null

        contentObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
        }
        contentObserver = null
        callback = null
        lastGps = null
        lastNetwork = null
    }
}
