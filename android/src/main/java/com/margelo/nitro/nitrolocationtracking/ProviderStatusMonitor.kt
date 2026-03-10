package com.margelo.nitro.nitrolocationtracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.util.Log

class ProviderStatusMonitor(private val context: Context) {

    companion object {
        private const val TAG = "ProviderStatusMonitor"
    }

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var callback: ((LocationProviderStatus, LocationProviderStatus) -> Unit)? = null
    private var receiver: BroadcastReceiver? = null

    fun setCallback(callback: (LocationProviderStatus, LocationProviderStatus) -> Unit) {
        this.callback = callback
        registerReceiver()
    }

    fun isLocationServicesEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    notifyStatus()
                }
            }
        }

        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        Log.d(TAG, "Registered provider status receiver")
    }

    private fun notifyStatus() {
        val gps = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            LocationProviderStatus.ENABLED else LocationProviderStatus.DISABLED
        val network = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            LocationProviderStatus.ENABLED else LocationProviderStatus.DISABLED
        callback?.invoke(gps, network)
    }

    fun destroy() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        receiver = null
        callback = null
    }
}
