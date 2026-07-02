package com.margelo.nitro.nitrolocationtracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Motion-state source backed by Google Play Services Activity Recognition.
 *
 * Emits `onMotionChange(true)` when the device enters a locomotion activity
 * (walking / running / cycling / in-vehicle) and `onMotionChange(false)` when it
 * enters STILL. Requires the ACTIVITY_RECOGNITION runtime permission on API 29+
 * (the GMS permission is auto-granted below 29). When the permission is missing
 * or Activity Recognition is unavailable, `start()` returns false and callers
 * should fall back to speed-based motion detection — `isAvailable` reflects this.
 */
class MotionActivityManager(private val context: Context) {

  companion object {
    private const val TAG = "NitroMotionActivity"
    private const val ACTION = "com.margelo.nitro.nitrolocationtracking.ACTIVITY_TRANSITION"

    private val TRACKED_ACTIVITIES = listOf(
      DetectedActivity.STILL,
      DetectedActivity.WALKING,
      DetectedActivity.RUNNING,
      DetectedActivity.ON_FOOT,
      DetectedActivity.ON_BICYCLE,
      DetectedActivity.IN_VEHICLE
    )
  }

  var onMotionChange: ((Boolean) -> Unit)? = null

  /** True only while Activity Recognition is actively feeding this manager. */
  var isAvailable: Boolean = false
    private set

  private val client = ActivityRecognition.getClient(context)
  private var pendingIntent: PendingIntent? = null
  private var receiver: BroadcastReceiver? = null

  @SuppressLint("MissingPermission")
  fun start(): Boolean {
    if (isAvailable) return true
    if (!hasPermission()) {
      Log.d(TAG, "ACTIVITY_RECOGNITION not granted — falling back to speed-based motion")
      isAvailable = false
      return false
    }

    // Explicitly-typed list (Google's canonical pattern). Avoids a flatMap
    // return-type inference cascade that otherwise makes the builder chain fail
    // to resolve.
    val transitions = ArrayList<ActivityTransition>(TRACKED_ACTIVITIES.size)
    for (activity in TRACKED_ACTIVITIES) {
      transitions.add(
        ActivityTransition.Builder()
          .setActivityType(activity)
          .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
          .build()
      )
    }
    val request = ActivityTransitionRequest(transitions)

    val intent = Intent(ACTION).setPackage(context.packageName)
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
      (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
    val pi = PendingIntent.getBroadcast(context, 0, intent, flags)
    pendingIntent = pi

    val rcv = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context, received: Intent) {
        if (!ActivityTransitionResult.hasResult(received)) return
        val result = ActivityTransitionResult.extractResult(received) ?: return
        for (event in result.transitionEvents) {
          if (event.transitionType != ActivityTransition.ACTIVITY_TRANSITION_ENTER) continue
          val moving = event.activityType != DetectedActivity.STILL
          Log.d(TAG, "Transition ENTER activity=${event.activityType} → moving=$moving")
          onMotionChange?.invoke(moving)
        }
      }
    }
    receiver = rcv
    ContextCompat.registerReceiver(
      context, rcv, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED
    )

    return try {
      client.requestActivityTransitionUpdates(request, pi)
      isAvailable = true
      Log.d(TAG, "Activity Recognition transition updates started")
      true
    } catch (e: SecurityException) {
      Log.w(TAG, "requestActivityTransitionUpdates refused: ${e.message}")
      cleanup()
      false
    } catch (e: Exception) {
      Log.w(TAG, "requestActivityTransitionUpdates failed: ${e.message}")
      cleanup()
      false
    }
  }

  @SuppressLint("MissingPermission")
  fun stop() {
    pendingIntent?.let {
      try {
        client.removeActivityTransitionUpdates(it)
      } catch (e: Exception) {
        Log.w(TAG, "removeActivityTransitionUpdates failed: ${e.message}")
      }
    }
    cleanup()
  }

  private fun cleanup() {
    receiver?.let {
      try {
        context.unregisterReceiver(it)
      } catch (_: Exception) {
        // Receiver may not have been registered — ignore.
      }
    }
    receiver = null
    pendingIntent = null
    isAvailable = false
  }

  private fun hasPermission(): Boolean {
    // ACTIVITY_RECOGNITION became a runtime permission in API 29. Below that the
    // GMS-scoped permission is normal (install-time) and thus always granted.
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
}
