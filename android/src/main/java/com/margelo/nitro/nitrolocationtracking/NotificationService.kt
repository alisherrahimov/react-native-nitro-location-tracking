package com.margelo.nitro.nitrolocationtracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationService(private val context: Context) {

    companion object {
        const val LOCAL_CHANNEL_ID = "nitro_location_local"
        const val ACTIVITY_CHANNEL_ID = "nitro_delivery_activity"
        const val ACTIVITY_NOTIFICATION_ID = 77002
        private var notificationCounter = 0

        // Emoji parity with the iOS Live Activity status glyphs.
        private fun statusEmoji(status: String): String = when (status) {
            "picking_up" -> "📦"
            "on_the_way" -> "🚗"
            "arriving" -> "🏁"
            "delivered" -> "✅"
            else -> "📍"
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    LOCAL_CHANNEL_ID,
                    "Location Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            // Low-importance channel for the ongoing delivery activity card so it
            // stays quiet (no sound/vibration) while remaining persistently visible.
            manager.createNotificationChannel(
                NotificationChannel(
                    ACTIVITY_CHANNEL_ID,
                    "Delivery Activity",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
    }

    /**
     * Android counterpart to the iOS Live Activity: a rich, ongoing notification
     * that shows live delivery status. Same call surface as `startLiveActivity`,
     * so a single JS integration drives a Dynamic Island on iOS and this card on
     * Android.
     */
    fun startDeliveryActivity(
        customerName: String,
        deliveryAddress: String,
        orderCount: Int,
        status: String,
        statusText: String,
        estimatedMinutes: Int,
        distanceMeters: Double
    ) {
        notify(buildActivityNotification(
            customerName, deliveryAddress, orderCount, status, statusText, estimatedMinutes, distanceMeters
        ))
    }

    fun updateDeliveryActivity(
        customerName: String,
        deliveryAddress: String,
        orderCount: Int,
        status: String,
        statusText: String,
        estimatedMinutes: Int,
        distanceMeters: Double
    ) {
        notify(buildActivityNotification(
            customerName, deliveryAddress, orderCount, status, statusText, estimatedMinutes, distanceMeters
        ))
    }

    fun endDeliveryActivity() {
        NotificationManagerCompat.from(context).cancel(ACTIVITY_NOTIFICATION_ID)
    }

    private fun notify(notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(ACTIVITY_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — nothing to show.
        }
    }

    private fun buildActivityNotification(
        customerName: String,
        deliveryAddress: String,
        orderCount: Int,
        status: String,
        statusText: String,
        estimatedMinutes: Int,
        distanceMeters: Double
    ): android.app.Notification {
        val emoji = statusEmoji(status)
        val distanceText = if (distanceMeters >= 1000) {
            String.format("%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters.toInt()} m"
        }
        val title = "$emoji $statusText"
        val line1 = if (customerName.isNotBlank()) customerName else "Delivery"
        val subtitle = buildString {
            append("~$estimatedMinutes min · $distanceText")
            if (orderCount > 1) append(" · $orderCount orders")
        }
        val bigText = buildString {
            if (deliveryAddress.isNotBlank()) append(deliveryAddress).append('\n')
            append(subtitle)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = launchIntent?.let {
            PendingIntent.getActivity(
                context, 1, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(context, ACTIVITY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$line1 · $subtitle")
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(bigText))
            .setSubText(line1)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply { pi?.let { setContentIntent(it) } }
            .build()
    }

    fun showLocalNotification(title: String, body: String) {
        val notification = NotificationCompat.Builder(context, LOCAL_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(++notificationCounter, notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    fun updateForegroundNotification(title: String, body: String) {
        val intent = Intent(context, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_UPDATE
            putExtra("title", title)
            putExtra("text", body)
        }
        // Legal while our FGS is alive (the process is exempt from background
        // start limits then). If the FGS is NOT running and the app is
        // backgrounded, startService throws IllegalStateException — there is
        // nothing to update in that case, so swallow instead of crashing.
        try {
            context.startService(intent)
        } catch (_: IllegalStateException) {
        }
    }

    fun startForegroundService(title: String, text: String) {
        val intent = Intent(context, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_START
            putExtra("title", title)
            putExtra("text", text)
        }
        // Prefer plain startService(): it carries NO "must call startForeground
        // within 10s" watchdog, so a stalled main thread (GC pressure, heavy
        // navigation transition at journey start) cannot convert into a
        // ForegroundServiceDidNotStartInTimeException crash — the service still
        // promotes itself in onCreate, just without a deadline. startService is
        // only legal while the app is foreground (throws IllegalStateException
        // from the background on O+); in that case fall back to
        // startForegroundService, which is exactly the API meant for background
        // starts and arms the watchdog only on that rare path.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.startService(intent)
            } catch (_: IllegalStateException) {
                context.startForegroundService(intent)
            }
        } else {
            context.startService(intent)
        }
    }

    fun stopForegroundService() {
        val intent = Intent(context, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_STOP
        }
        // Same rationale as updateForegroundNotification: if the FGS is not
        // running and the app is backgrounded this throws — and a stop of a
        // non-running service is a no-op anyway. stopService() as fallback is
        // always legal and covers the started-but-not-yet-foregrounded edge.
        try {
            context.startService(intent)
        } catch (_: IllegalStateException) {
            context.stopService(Intent(context, LocationForegroundService::class.java))
        }
    }
}
