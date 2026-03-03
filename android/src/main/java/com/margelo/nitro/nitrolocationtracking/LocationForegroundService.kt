package com.margelo.nitro.nitrolocationtracking

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "Location Active"
        val text = intent?.getStringExtra("text") ?: "Tracking your location"
        when (intent?.action) {
            ACTION_START -> startForeground(
                NOTIFICATION_ID, buildNotification(title, text))
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
