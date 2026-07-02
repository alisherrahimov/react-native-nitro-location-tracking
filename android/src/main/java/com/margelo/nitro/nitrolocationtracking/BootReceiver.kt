package com.margelo.nitro.nitrolocationtracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-arms persisted geofences after a device reboot. Google Play Services drops
 * all registered geofences on reboot, so without this they would silently stop
 * firing until the app is next opened. Registered in the manifest for
 * ACTION_BOOT_COMPLETED / ACTION_LOCKED_BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {

    companion object { private const val TAG = "NitroBootReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "boot completed — re-arming persisted geofences")
                try {
                    GeofenceManager.reRegisterAll(context.applicationContext)
                } catch (e: Exception) {
                    Log.w(TAG, "geofence re-arm on boot failed: ${e.message}")
                }
            }
        }
    }
}
