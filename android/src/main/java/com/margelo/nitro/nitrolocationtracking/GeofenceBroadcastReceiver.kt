package com.margelo.nitro.nitrolocationtracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Manifest-declared receiver for geofence transitions. Being a static component
 * (not a runtime-registered receiver) it is delivered to even after the app
 * process has been killed — the OS spins the process up to run it. It maps the
 * GMS transition to a [GeofenceEvent] and forwards it to
 * [GeofenceManager.processWideCallback] (null when no consumer is attached yet).
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object { private const val TAG = "GeofenceReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            Log.w(TAG, "geofence event error: ${event?.errorCode}")
            return
        }
        val mapped = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceEvent.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> GeofenceEvent.EXIT
            Geofence.GEOFENCE_TRANSITION_DWELL -> GeofenceEvent.DWELL
            else -> return
        }
        val sink = GeofenceManager.processWideCallback
        event.triggeringGeofences?.forEach { geofence ->
            sink?.invoke(mapped, geofence.requestId)
        }
    }
}
