package com.mrrwhoo1.multidl

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Keeps MultiDL alive while it downloads in the background.
 *
 * Android freezes or kills an app that is not on screen, unless it runs a
 * "foreground service" and shows a notification. This service does no downloading
 * itself. Its only job is to be that foreground service. The downloads still run in
 * the download manager, and this just stops Android from stopping them.
 *
 * MultiDLScreen calls [show] whenever the queue changes and there is work to do,
 * and [stop] when the queue has nothing left to download.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    // Holds the phone's CPU awake. Without it, once the screen turns off Android can put
    // the CPU to sleep in the middle of a download or a video/audio merge, and the
    // download just freezes (the notification stays on the last number it showed).
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        running = true

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MultiDL:downloads"
        ).apply {
            setReferenceCounted(false)
            // Safety net: let go after 10 hours even if something goes wrong.
            acquire(10 * 60 * 60 * 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Starting downloads…"
        val percent = intent?.getIntExtra(EXTRA_PERCENT, -1) ?: -1

        createChannel(this)
        val notification = buildNotification(this, text, percent)

        // Android 10+ wants to know what kind of work the service does.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // If Android ever kills the app, the queue is gone anyway, so do not restart.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "multidl_downloads"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_PERCENT = "percent"

        // Used to avoid refreshing the notification when nothing changed.
        @Volatile private var running = false
        private var lastText = ""
        private var lastPercent = -2

        private val handler = Handler(Looper.getMainLooper())
        private var pendingStop: Runnable? = null

        /**
         * Starts the service the first time, then just updates its notification.
         * percent is 0-100, or -1 for "unknown" (shows a moving bar).
         */
        fun show(context: Context, text: String, percent: Int) {
            val appContext = context.applicationContext

            // New work arrived while a stop was waiting: cancel that stop.
            pendingStop?.let { handler.removeCallbacks(it) }
            pendingStop = null

            if (!running) {
                try {
                    val intent = Intent(appContext, DownloadService::class.java)
                        .putExtra(EXTRA_TEXT, text)
                        .putExtra(EXTRA_PERCENT, percent)
                    ContextCompat.startForegroundService(appContext, intent)
                    running = true
                    lastText = text
                    lastPercent = percent
                } catch (e: Exception) {
                    Log.w("MultiDL", "Could not start the download service: ${e.message}")
                }
                return
            }

            if (text == lastText && percent == lastPercent) return
            lastText = text
            lastPercent = percent

            updateNotification(appContext, text, percent)
        }

        /**
         * Stops the service. It waits one second first: stopping a service in the same
         * instant it was started can crash the app, and a new download may arrive anyway.
         */
        fun stop(context: Context) {
            if (!running) return
            if (pendingStop != null) return

            val appContext = context.applicationContext
            val stopper = Runnable {
                appContext.stopService(Intent(appContext, DownloadService::class.java))
                running = false
                pendingStop = null
            }

            pendingStop = stopper
            handler.postDelayed(stopper, 1000)
        }

        @SuppressLint("MissingPermission")
        private fun updateNotification(context: Context, text: String, percent: Int) {
            // Without the notification permission, Android simply does not show it.
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, buildNotification(context, text, percent))
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW   // quiet: no sound or pop-up
            ).apply {
                description = "Shows progress while MultiDL downloads in the background"
            }

            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context, text: String, percent: Int): Notification {
            // Tapping the notification opens the app.
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("MultiDL")
                .setContentText(text)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent.coerceAtLeast(0), percent < 0)
                .build()
        }
    }
}
