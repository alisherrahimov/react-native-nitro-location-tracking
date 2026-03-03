package com.margelo.nitro.nitrolocationtracking

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class NativeDBWriter(context: Context) :
    SQLiteOpenHelper(context, "nitro_location.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS locations (
                id TEXT PRIMARY KEY,
                latitude REAL NOT NULL, longitude REAL NOT NULL,
                altitude REAL, speed REAL, bearing REAL, accuracy REAL,
                timestamp INTEGER NOT NULL, ride_id TEXT,
                synced INTEGER DEFAULT 0, retry_count INTEGER DEFAULT 0,
                created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_locations_synced ON locations(synced)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_locations_timestamp ON locations(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_locations_ride_id ON locations(ride_id)")
        db.execSQL("PRAGMA journal_mode = WAL")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}

    fun insert(location: LocationData, rideId: String? = null) {
        writableDatabase.insert("locations", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("altitude", location.altitude)
            put("speed", location.speed)
            put("bearing", location.bearing)
            put("accuracy", location.accuracy)
            put("timestamp", location.timestamp.toLong())
            put("ride_id", rideId)
            put("synced", 0)
        })
    }

    fun getUnsyncedBatch(limit: Int): List<Pair<String, LocationData>> {
        val list = mutableListOf<Pair<String, LocationData>>()
        readableDatabase.query("locations", null, "synced = 0",
            null, null, null, "timestamp ASC", "$limit").use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow("id"))
                val data = LocationData(
                    latitude = it.getDouble(it.getColumnIndexOrThrow("latitude")),
                    longitude = it.getDouble(it.getColumnIndexOrThrow("longitude")),
                    altitude = it.getDouble(it.getColumnIndexOrThrow("altitude")),
                    speed = it.getDouble(it.getColumnIndexOrThrow("speed")),
                    bearing = it.getDouble(it.getColumnIndexOrThrow("bearing")),
                    accuracy = it.getDouble(it.getColumnIndexOrThrow("accuracy")),
                    timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")).toDouble()
                )
                list.add(Pair(id, data))
            }
        }
        return list
    }

    fun markSynced(ids: List<String>) {
        val ph = ids.joinToString(",") { "?" }
        writableDatabase.execSQL(
            "UPDATE locations SET synced = 1 WHERE id IN ($ph)",
            ids.toTypedArray())
    }

    fun clearOldSynced() {
        writableDatabase.execSQL("""
            DELETE FROM locations WHERE synced = 1
            AND timestamp < (strftime('%s','now') * 1000 - 86400000)
        """)
    }
}
