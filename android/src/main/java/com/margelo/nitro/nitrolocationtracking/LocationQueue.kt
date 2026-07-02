package com.margelo.nitro.nitrolocationtracking

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Durable FIFO queue of pending Live Push bodies, backed by SQLite so it
 * survives app kill / reboot. Each row is one serialized POST body (the
 * per-fix JSON object). The drainer in [LivePusher] peeks the oldest rows,
 * POSTs them, and deletes only on success.
 *
 * All methods are synchronous and thread-safe (SQLiteDatabase serializes its
 * own access); callers invoke them from LivePusher's single drain executor.
 */
class LocationQueue(context: Context) {

  companion object {
    private const val TAG = "NitroLocationQueue"
    private const val DB_NAME = "nitro_livepush_queue.db"
    private const val DB_VERSION = 1
    private const val TABLE = "pending"
    private const val COL_ID = "id"
    private const val COL_BODY = "body"
    private const val COL_CREATED = "created_at"
  }

  private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        "CREATE TABLE $TABLE ($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
          "$COL_BODY TEXT NOT NULL, $COL_CREATED INTEGER NOT NULL)"
      )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      // v1 only — nothing to migrate yet.
    }
  }

  /** One pending row: its primary key and serialized body. */
  data class Row(val id: Long, val body: String)

  private val helper = Helper(context.applicationContext)

  fun enqueue(body: String, nowMs: Long) {
    try {
      val values = ContentValues().apply {
        put(COL_BODY, body)
        put(COL_CREATED, nowMs)
      }
      helper.writableDatabase.insert(TABLE, null, values)
    } catch (e: Exception) {
      Log.w(TAG, "enqueue failed: ${e.message}")
    }
  }

  /** Oldest [limit] rows, FIFO order. */
  fun peek(limit: Int): List<Row> {
    if (limit <= 0) return emptyList()
    val rows = ArrayList<Row>(limit)
    try {
      helper.readableDatabase.query(
        TABLE, arrayOf(COL_ID, COL_BODY), null, null, null, null,
        "$COL_ID ASC", limit.toString()
      ).use { c ->
        val idIdx = c.getColumnIndexOrThrow(COL_ID)
        val bodyIdx = c.getColumnIndexOrThrow(COL_BODY)
        while (c.moveToNext()) rows.add(Row(c.getLong(idIdx), c.getString(bodyIdx)))
      }
    } catch (e: Exception) {
      Log.w(TAG, "peek failed: ${e.message}")
    }
    return rows
  }

  fun delete(ids: List<Long>) {
    if (ids.isEmpty()) return
    try {
      val placeholders = ids.joinToString(",") { "?" }
      val args = ids.map { it.toString() }.toTypedArray()
      helper.writableDatabase.delete(TABLE, "$COL_ID IN ($placeholders)", args)
    } catch (e: Exception) {
      Log.w(TAG, "delete failed: ${e.message}")
    }
  }

  fun count(): Int {
    return try {
      helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
      }
    } catch (e: Exception) {
      Log.w(TAG, "count failed: ${e.message}")
      0
    }
  }

  /** Drop oldest rows so the queue never exceeds [maxSize]. */
  fun trimToMax(maxSize: Int) {
    if (maxSize <= 0) return
    try {
      val excess = count() - maxSize
      if (excess <= 0) return
      // Delete the `excess` oldest ids.
      helper.writableDatabase.execSQL(
        "DELETE FROM $TABLE WHERE $COL_ID IN " +
          "(SELECT $COL_ID FROM $TABLE ORDER BY $COL_ID ASC LIMIT $excess)"
      )
      Log.d(TAG, "trimmed $excess oldest rows (cap=$maxSize)")
    } catch (e: Exception) {
      Log.w(TAG, "trimToMax failed: ${e.message}")
    }
  }
}
