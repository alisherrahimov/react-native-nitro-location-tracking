package com.margelo.nitro.nitrolocationtracking

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class LocationForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "nitro_location_channel"
        const val NOTIFICATION_ID = 77001
        const val ACTION_START = "com.nitrolocation.START"
        const val ACTION_STOP = "com.nitrolocation.STOP"
        const val ACTION_UPDATE = "com.nitrolocation.UPDATE"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        // Must call startForeground immediately to avoid
        // ForegroundServiceDidNotStartInTimeException.
        promoteToForeground("Location Active", "Tracking your location")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "Location Active"
        val text = intent?.getStringExtra("text") ?: "Tracking your location"
        when (intent?.action) {
            ACTION_START, null -> promoteToForeground(title, text)
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE -> (getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager).notify(
                NOTIFICATION_ID, buildNotification(title, text))
        }
        return START_STICKY
    }

    /**
     * Promote to a foreground service. On Android 14+ (API 34) the OS validates
     * that a runtime location permission is granted before allowing a
     * `location`-typed FGS; if it is not (never granted, revoked, or started
     * from the background) startForeground throws SecurityException — and on
     * Android 12+ a background start can throw ForegroundServiceStartNotAllowedException.
     *
     * We must not let that escape: an unhandled throw both crashes the process
     * AND leaves the service un-promoted, which then trips the
     * ForegroundServiceDidNotStartInTimeException watchdog. Instead we catch and
     * stopSelf() so the service shuts down cleanly.
     */
    private fun promoteToForeground(title: String, text: String) {
        val notification = buildNotification(title, text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(
                "LocationFGS",
                "startForeground failed (${e.javaClass.simpleName}: ${e.message}); " +
                    "stopping service to avoid crash/watchdog kill."
            )
            // Satisfy the watchdog contract BEFORE stopping. Production crashes
            // (Android 15/16, Xiaomi + Samsung) show that stopSelf() alone can
            // still trip ForegroundServiceDidNotStartInTimeException — the
            // timeout posted by startForegroundService() races service teardown.
            // A SHORT_SERVICE-typed promotion (API 34+) requires no runtime
            // permission and no manifest type declaration, so it cannot fail
            // for the same reason the location-typed one did; it marks the
            // service as "did start foreground", after which stopping is safe.
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                    )
                } catch (fallbackErr: Exception) {
                    Log.w(
                        "LocationFGS",
                        "shortService fallback promotion also failed: " +
                            "${fallbackErr.javaClass.simpleName}: ${fallbackErr.message}"
                    )
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
