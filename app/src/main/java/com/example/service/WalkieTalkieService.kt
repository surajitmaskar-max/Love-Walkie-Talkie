package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class WalkieTalkieService : Service {

    constructor() : super()

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val partnerName = intent?.getStringExtra(EXTRA_PARTNER_NAME) ?: "My Love"
        val isSpeaking = intent?.getBooleanExtra(EXTRA_IS_SPEAKING, false) ?: false

        when (action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                val notification = buildNotification(partnerName, isSpeaking)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        0
                    }
                    startForeground(NOTIFICATION_ID, notification, foregroundType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }

        return START_STICKY
    }

    private fun buildNotification(partnerName: String, isSpeaking: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WalkieTalkieService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isSpeaking) {
            "Transmitting voice to $partnerName..."
        } else {
            "Connected with $partnerName • Live audio ready"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Love Walkie Talkie")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", pendingStopIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Love Walkie Talkie Voice Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps private voice connection running in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LoveWalkieTalkie::VoiceSessionWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L /* 10 minutes timeout */)
        }
    }

    private fun stopForegroundService() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopForegroundService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "love_walkie_talkie_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_UPDATE = "com.example.service.ACTION_UPDATE"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        const val EXTRA_PARTNER_NAME = "partner_name"
        const val EXTRA_IS_SPEAKING = "is_speaking"

        fun startService(context: Context, partnerName: String, isSpeaking: Boolean = false) {
            val intent = Intent(context, WalkieTalkieService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PARTNER_NAME, partnerName)
                putExtra(EXTRA_IS_SPEAKING, isSpeaking)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateService(context: Context, partnerName: String, isSpeaking: Boolean) {
            val intent = Intent(context, WalkieTalkieService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_PARTNER_NAME, partnerName)
                putExtra(EXTRA_IS_SPEAKING, isSpeaking)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WalkieTalkieService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
}
