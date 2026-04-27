package com.eaglepay.listener

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Phase 1 Fix 1: Foreground service that keeps the process alive.
 * Android 8+ aggressively kills background services. By running as a
 * foreground service with a persistent notification, the OS treats this
 * process as user-visible and will not kill it during normal operation.
 *
 * Started automatically on boot via BootReceiver, and from MainActivity.
 */
class ForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart service if killed by system
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Restart self if destroyed
        val restart = Intent(applicationContext, ForegroundService::class.java)
        startForegroundService(restart)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Eagle Pay Listener",
            NotificationManager.IMPORTANCE_LOW  // Low importance = no sound, no popup
        ).apply {
            description = "Keeps Eagle Pay running in background"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Eagle Pay")
            .setContentText("Listening for UPI payments...")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(openApp)
            .setOngoing(true)          // Cannot be dismissed by user
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "eagle_pay_foreground"
        const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, ForegroundService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ForegroundService::class.java))
        }
    }
}
