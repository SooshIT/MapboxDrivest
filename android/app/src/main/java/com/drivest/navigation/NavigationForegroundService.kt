package com.drivest.navigation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps navigation process priority high while guidance is active in background.
 */
class NavigationForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START,
            ACTION_UPDATE,
            null -> {
                val mode = SessionMode.fromStorage(intent?.getStringExtra(EXTRA_SESSION_MODE))
                startAsForeground(mode)
                return START_STICKY
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stop guidance requested from notification action")
                sendBroadcast(
                    Intent(ACTION_STOP_GUIDANCE_REQUEST).setPackage(packageName)
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> return START_STICKY
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User explicitly removed the app task from recents: stop the foreground service so the
        // notification does not keep the app process alive unexpectedly.
        Log.d(TAG, "Navigation foreground service stopping due to task removal")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun startAsForeground(mode: SessionMode) {
        val notification = buildNotification(mode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(mode: SessionMode): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                AppFlow.EXTRA_APP_MODE,
                if (mode == SessionMode.NAVIGATION) AppFlow.MODE_NAV else AppFlow.MODE_PRACTICE
            )
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            1010,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopGuidanceIntent = Intent(this, NavigationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopGuidancePendingIntent = PendingIntent.getService(
            this,
            1011,
            stopGuidanceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (titleRes, textRes) = when (mode) {
            SessionMode.NAVIGATION -> {
                R.string.navigation_service_nav_title to R.string.navigation_service_nav_text
            }
            SessionMode.PRACTICE -> {
                R.string.navigation_service_practice_title to R.string.navigation_service_practice_text
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_overview_route)
            .setContentTitle(getString(titleRes))
            .setContentText(getString(textRes))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.navigation_service_action_stop),
                stopGuidancePendingIntent
            )
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.navigation_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.navigation_service_channel_description)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    enum class SessionMode(val storageValue: String) {
        NAVIGATION("navigation"),
        PRACTICE("practice");

        companion object {
            fun fromStorage(value: String?): SessionMode {
                return entries.firstOrNull { it.storageValue == value } ?: NAVIGATION
            }
        }
    }

    companion object {
        private const val TAG = "NavForegroundService"
        private const val CHANNEL_ID = "drivest_nav_foreground_channel"
        private const val NOTIFICATION_ID = 9011
        private const val ACTION_START = "com.drivest.navigation.action.NAV_FOREGROUND_START"
        private const val ACTION_UPDATE = "com.drivest.navigation.action.NAV_FOREGROUND_UPDATE"
        private const val ACTION_STOP = "com.drivest.navigation.action.NAV_FOREGROUND_STOP"
        private const val EXTRA_SESSION_MODE = "extra_session_mode"
        const val ACTION_STOP_GUIDANCE_REQUEST = "com.drivest.navigation.action.STOP_GUIDANCE_REQUEST"

        fun start(context: Context, mode: SessionMode) {
            val intent = Intent(context, NavigationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_MODE, mode.storageValue)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun update(context: Context, mode: SessionMode) {
            val intent = Intent(context, NavigationForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_SESSION_MODE, mode.storageValue)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavigationForegroundService::class.java))
        }
    }
}
