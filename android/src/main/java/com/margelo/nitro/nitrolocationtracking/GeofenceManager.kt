package com.margelo.nitro.nitrolocationtracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        private const val ACTION_GEOFENCE = "com.margelo.nitro.nitrolocationtracking.GEOFENCE_EVENT"
    }

    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)
    private val activeRegions = mutableMapOf<String, GeofenceRegion>()
    private var callback: ((GeofenceEvent, String) -> Unit)? = null
    private var receiver: BroadcastReceiver? = null

    fun setCallback(callback: (GeofenceEvent, String) -> Unit) {
        this.callback = callback
        registerReceiver()
    }

    @SuppressLint("MissingPermission")
    fun addGeofence(region: GeofenceRegion) {
        val geofence = Geofence.Builder()
            .setRequestId(region.id)
            .setCircularRegion(region.latitude, region.longitude, region.radius.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(buildTransitionTypes(region))
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(request, getGeofencePendingIntent())
            .addOnSuccessListener {
                activeRegions[region.id] = region
                Log.d(TAG, "Geofence added: ${region.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to add geofence ${region.id}: ${e.message}")
            }
    }

    fun removeGeofence(regionId: String) {
        geofencingClient.removeGeofences(listOf(regionId))
            .addOnSuccessListener {
                activeRegions.remove(regionId)
                Log.d(TAG, "Geofence removed: $regionId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove geofence $regionId: ${e.message}")
            }
    }

    fun removeAllGeofences() {
        geofencingClient.removeGeofences(getGeofencePendingIntent())
            .addOnSuccessListener {
                activeRegions.clear()
                Log.d(TAG, "All geofences removed")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove all geofences: ${e.message}")
            }
    }

    private fun buildTransitionTypes(region: GeofenceRegion): Int {
        var types = 0
        if (region.notifyOnEntry) types = types or Geofence.GEOFENCE_TRANSITION_ENTER
        if (region.notifyOnExit) types = types or Geofence.GEOFENCE_TRANSITION_EXIT
        return types
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(ACTION_GEOFENCE).apply {
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private fun registerReceiver() {
        if (receiver != null) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_GEOFENCE) return
                val geofencingEvent = com.google.android.gms.location.GeofencingEvent.fromIntent(intent)
                if (geofencingEvent == null || geofencingEvent.hasError()) {
                    Log.e(TAG, "Geofencing event error: ${geofencingEvent?.errorCode}")
                    return
                }

                val transition = geofencingEvent.geofenceTransition
                val event = when (transition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceEvent.ENTER
                    Geofence.GEOFENCE_TRANSITION_EXIT -> GeofenceEvent.EXIT
                    else -> return
                }

                geofencingEvent.triggeringGeofences?.forEach { geofence ->
                    callback?.invoke(event, geofence.requestId)
                }
            }
        }

        val filter = IntentFilter(ACTION_GEOFENCE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
          ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
          )
        }
    }

    fun destroy() {
        removeAllGeofences()
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        receiver = null
        callback = null
    }
}
