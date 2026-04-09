package com.margelo.nitro.nitrolocationtracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log

class AirplaneModeMonitor(private val context: Context) {

    companion object {
        private const val TAG = "AirplaneModeMonitor"
    }

    private var callback: ((Boolean) -> Unit)? = null
    private var lastState: Boolean? = null
    private var receiver: BroadcastReceiver? = null

    fun setCallback(callback: (Boolean) -> Unit) {
        this.callback = callback
        
        // Emit current state immediately
        val current = isAirplaneModeEnabled()
        lastState = current
        callback.invoke(current)
        
        registerReceiver()
    }

    fun isAirplaneModeEnabled(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
    }

    private fun registerReceiver() {
        if (receiver != null) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                    val isAirplaneModeOn = intent.getBooleanExtra("state", false)
                    if (isAirplaneModeOn != lastState) {
                        lastState = isAirplaneModeOn
                        Log.d(TAG, "Airplane mode changed: $isAirplaneModeOn")
                        callback?.invoke(isAirplaneModeOn)
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        context.registerReceiver(receiver, filter)
        Log.d(TAG, "Airplane mode receiver registered")
    }

    fun destroy() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
        receiver = null
        callback = null
        lastState = null
    }
}
