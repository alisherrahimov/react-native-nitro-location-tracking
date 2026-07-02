package com.margelo.nitro.nitrolocationtracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Circular geofencing with ENTER / EXIT / DWELL transitions, backed by Google
 * Play Services. Regions are persisted ([GeofenceStore]) and re-armed after
 * process death / reboot (see [GeofenceBroadcastReceiver] and [BootReceiver]),
 * so monitoring resumes without the app having to re-add them.
 *
 * Transition callbacks are delivered through a process-wide static
 * ([processWideCallback]) because the receiver is a manifest component that can
 * run in a freshly-created process. When JS is alive, this manager points that
 * static at the JS callback.
 */
class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        private const val DEFAULT_LOITERING_MS = 300_000 // 5 minutes

        /**
         * Process-wide sink for geofence transitions. Set by the active
         * GeofenceManager so [GeofenceBroadcastReceiver] can deliver events even
         * when it runs before the module is fully wired. Null when no consumer
         * is attached (e.g. app relaunched cold by a boot re-arm with no JS yet).
         */
        @Volatile
        @JvmStatic
        var processWideCallback: ((GeofenceEvent, String) -> Unit)? = null

        /** Explicit-component PendingIntent targeting the manifest receiver. */
        internal fun pendingIntent(context: Context): PendingIntent {
            val intent = android.content.Intent(context, GeofenceBroadcastReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, 0, intent, flags)
        }

        private fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        internal fun buildGeofence(region: GeofenceRegion): Geofence {
            var types = 0
            if (region.notifyOnEntry) types = types or Geofence.GEOFENCE_TRANSITION_ENTER
            if (region.notifyOnExit) types = types or Geofence.GEOFENCE_TRANSITION_EXIT
            if (region.notifyOnDwell == true) types = types or Geofence.GEOFENCE_TRANSITION_DWELL

            val builder = Geofence.Builder()
                .setRequestId(region.id)
                .setCircularRegion(region.latitude, region.longitude, region.radius.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(types)
            if (region.notifyOnDwell == true) {
                builder.setLoiteringDelay((region.loiteringDelayMs ?: DEFAULT_LOITERING_MS.toDouble()).toInt())
            }
            return builder.build()
        }

        /**
         * Re-register every persisted region. Called from [BootReceiver] after a
         * reboot (GMS drops geofences on reboot) and defensively on manager init.
         */
        @SuppressLint("MissingPermission")
        internal fun reRegisterAll(context: Context) {
            if (!hasLocationPermission(context)) {
                Log.w(TAG, "reRegisterAll skipped — location permission not granted")
                return
            }
            val regions = GeofenceStore(context).readAll()
            if (regions.isEmpty()) return
            val client = LocationServices.getGeofencingClient(context)
            regions.forEach { region ->
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofence(buildGeofence(region))
                    .build()
                try {
                    client.addGeofences(request, pendingIntent(context))
                        .addOnFailureListener { e -> Log.w(TAG, "re-arm ${region.id} failed: ${e.message}") }
                } catch (e: Exception) {
                    Log.w(TAG, "re-arm ${region.id} threw: ${e.message}")
                }
            }
            Log.d(TAG, "re-armed ${regions.size} persisted geofence(s)")
        }
    }

    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)
    private val store = GeofenceStore(context)
    private val activeRegions = mutableMapOf<String, GeofenceRegion>()
    private var callback: ((GeofenceEvent, String) -> Unit)? = null

    init {
        // Repopulate the in-memory map from storage and defensively re-arm, in
        // case GMS dropped the geofences (process death without a reboot).
        store.readAll().forEach { activeRegions[it.id] = it }
        if (activeRegions.isNotEmpty()) reRegisterAll(context)
    }

    fun setCallback(callback: (GeofenceEvent, String) -> Unit) {
        this.callback = callback
        processWideCallback = { event, id -> this.callback?.invoke(event, id) }
    }

    @SuppressLint("MissingPermission")
    fun addGeofence(region: GeofenceRegion) {
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(buildGeofence(region))
            .build()

        geofencingClient.addGeofences(request, pendingIntent(context))
            .addOnSuccessListener {
                activeRegions[region.id] = region
                store.save(region) // persist only after GMS accepts it
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
                store.remove(regionId)
                Log.d(TAG, "Geofence removed: $regionId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove geofence $regionId: ${e.message}")
            }
    }

    fun removeAllGeofences() {
        geofencingClient.removeGeofences(pendingIntent(context))
            .addOnSuccessListener {
                activeRegions.clear()
                store.clear()
                Log.d(TAG, "All geofences removed")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove all geofences: ${e.message}")
            }
    }

    fun distanceTo(regionId: String, lastLocation: android.location.Location?): Double {
        val region = activeRegions[regionId] ?: return -1.0
        val loc = lastLocation ?: return -1.0
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            loc.latitude, loc.longitude, region.latitude, region.longitude, results
        )
        return results[0].toDouble()
    }

    fun destroy() {
        // Stop delivering to this (soon-dead) callback, but DO NOT unregister the
        // geofences: they are durable by design and survive until explicitly
        // removed. Just detach the JS sink.
        processWideCallback = null
        callback = null
    }
}
