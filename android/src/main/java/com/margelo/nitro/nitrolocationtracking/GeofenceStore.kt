package com.margelo.nitro.nitrolocationtracking

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable record of the geofences the app has registered, so they can be
 * re-armed after process death / reboot (Google Play Services drops geofences
 * on reboot). Backed by SharedPreferences; small by nature (a handful of
 * regions), so the whole set is read/written at once.
 */
class GeofenceStore(context: Context) {

  companion object {
    private const val TAG = "NitroGeofenceStore"
    private const val PREFS = "nitro_geofences"
    private const val KEY = "regions"
  }

  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun save(region: GeofenceRegion) {
    val map = readAll().associateBy { it.id }.toMutableMap()
    map[region.id] = region
    writeAll(map.values)
  }

  fun remove(id: String) {
    val remaining = readAll().filterNot { it.id == id }
    writeAll(remaining)
  }

  fun clear() {
    prefs.edit().remove(KEY).apply()
  }

  fun readAll(): List<GeofenceRegion> {
    val raw = prefs.getString(KEY, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      (0 until arr.length()).mapNotNull { i -> fromJson(arr.getJSONObject(i)) }
    } catch (e: Exception) {
      Log.w(TAG, "readAll failed: ${e.message}")
      emptyList()
    }
  }

  private fun writeAll(regions: Collection<GeofenceRegion>) {
    val arr = JSONArray()
    regions.forEach { arr.put(toJson(it)) }
    prefs.edit().putString(KEY, arr.toString()).apply()
  }

  private fun toJson(r: GeofenceRegion): JSONObject = JSONObject().apply {
    put("id", r.id)
    put("latitude", r.latitude)
    put("longitude", r.longitude)
    put("radius", r.radius)
    put("notifyOnEntry", r.notifyOnEntry)
    put("notifyOnExit", r.notifyOnExit)
    put("notifyOnDwell", r.notifyOnDwell ?: false)
    put("loiteringDelayMs", r.loiteringDelayMs ?: JSONObject.NULL)
  }

  private fun fromJson(o: JSONObject): GeofenceRegion? = try {
    GeofenceRegion(
      id = o.getString("id"),
      latitude = o.getDouble("latitude"),
      longitude = o.getDouble("longitude"),
      radius = o.getDouble("radius"),
      notifyOnEntry = o.optBoolean("notifyOnEntry", true),
      notifyOnExit = o.optBoolean("notifyOnExit", true),
      notifyOnDwell = o.optBoolean("notifyOnDwell", false),
      loiteringDelayMs = if (o.isNull("loiteringDelayMs")) null else o.getDouble("loiteringDelayMs")
    )
  } catch (e: Exception) {
    Log.w(TAG, "fromJson failed: ${e.message}")
    null
  }
}
