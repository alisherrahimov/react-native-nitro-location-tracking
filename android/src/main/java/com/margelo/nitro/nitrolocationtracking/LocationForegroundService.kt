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

        private const val TAG = "LocationFGS"
    }

    /** True once startForeground() has been accepted at least once. */
    private var isPromoted = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // startForegroundService() arms a watchdog that ONLY a successful
        // startForeground() disarms — stopSelf() does not. So promote first,
        // with a notification that cannot throw: no PendingIntent, no
        // PackageManager lookup, no intent extras. The caller's title/text and
        // the tap-to-open target are applied in onStartCommand, once safe.
        promoteToForeground(minimalNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "Location Active"
        val text = intent?.getStringExtra("text") ?: "Tracking your location"

        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            // ACTION_START, ACTION_UPDATE, or a null action (system restart):
            // onCreate already promoted us, so this only upgrades the
            // notification content. Failure here is cosmetic, never fatal.
            else -> updateNotification(title, text)
        }

        // Deliberately NOT START_STICKY. A system-initiated restart lands while
        // the app is in the background, where Android 12+ forbids promoting a
        // location-typed FGS — the promotion fails, the watchdog fires, and the
        // process crashes, repeatedly, with no user action able to stop it.
        // Tracking is re-armed from JS when the app is foreground and the
        // location permission is confirmed granted.
        return START_NOT_STICKY
    }

    /**
     * Required by the SHORT_SERVICE contract (API 34+): the system calls this
     * when a short-service promotion approaches its time limit, and failing to
     * stop promptly is itself a crash. We only ever hold SHORT_SERVICE for the
     * few milliseconds of [disarmWatchdogAndStop], so this should never fire —
     * but an unimplemented onTimeout turns "should never" into a crash class.
     */
    override fun onTimeout(startId: Int) {
        Log.w(TAG, "short-service timeout reached — stopping")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    // ─── Promotion ────────────────────────────────────────────────────

    /**
     * Promote to a foreground service.
     *
     * Android 14+ (API 34) rejects a `location`-typed FGS unless a runtime
     * location permission is held (SecurityException); Android 12+ rejects a
     * background start (ForegroundServiceStartNotAllowedException).
     *
     * Neither may escape: an unhandled throw crashes the process AND leaves the
     * service un-promoted, tripping the ForegroundServiceDidNotStartInTime
     * watchdog. stopSelf() alone does NOT clear that watchdog, so a refused
     * promotion falls back to SHORT_SERVICE — no runtime permission required,
     * declared in the library manifest alongside `location` because API 34+
     * only accepts types declared there — purely to mark the service as having
     * started foreground, after which shutting down is safe.
     */
    private fun promoteToForeground(notification: Notification) {
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
            isPromoted = true
        } catch (e: Exception) {
            Log.w(TAG, "location-typed startForeground failed (${e.javaClass.simpleName}: ${e.message})")
            disarmWatchdogAndStop(notification)
        }
    }

    private fun disarmWatchdogAndStop(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                )
                isPromoted = true
            } catch (e: Exception) {
                // Nothing left to try. Swallow it — rethrowing here crashes the
                // process, which is the exact outcome this path exists to avoid.
                Log.w(TAG, "shortService fallback also failed (${e.javaClass.simpleName}: ${e.message})")
            }
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    // ─── Notifications ────────────────────────────────────────────────

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        } catch (e: Exception) {
            // A missing channel makes the notification invisible, not fatal —
            // it must never block the promotion that follows.
            Log.w(TAG, "createNotificationChannel failed: ${e.message}")
        }
    }

    /**
     * The promotion-safe notification: static strings only, no PendingIntent,
     * no PackageManager access. Everything that can throw lives in
     * [richNotification], which never runs before we are promoted.
     */
    private fun minimalNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Active")
            .setContentText("Tracking your location")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /**
     * Full notification: caller's copy plus a tap-to-open target.
     *
     * getLaunchIntentForPackage() returns null while the package is being
     * replaced (app update) and on some restricted-profile setups, and
     * PendingIntent.getActivity() throws on a null intent. That threw straight
     * out of onCreate in earlier versions and was the single largest source of
     * ForegroundServiceDidNotStartInTimeException — hence the null check, and
     * hence this is never used to promote.
     */
    private fun richNotification(title: String, text: String): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        return builder.build()
    }

    /** Cosmetic refresh of an already-promoted service. Failure is ignored. */
    private fun updateNotification(title: String, text: String) {
        if (!isPromoted) return
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, richNotification(title, text))
        } catch (e: Exception) {
            Log.w(TAG, "notification update failed: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
