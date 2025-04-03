package com.example.keynews.util

// CodeCleaner_Start_f6a74c54-8e05-45cb-b43d-920dd9cba7ab
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.support.v4.media.session.MediaSessionCompat
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.example.keynews.R
import com.example.keynews.MainActivity
import com.example.keynews.service.TtsReadingService

/**
 * Manages notifications for media playback and TTS reading
 */
class MediaNotificationManager(private val context: Context) {
    private val TAG = "MediaNotifManager"
    private val NOTIF_CHANNEL_ID = "TTS_CHANNEL"
    private val MEDIA_NOTIF_CHANNEL_ID = "TTS_MEDIA_CHANNEL"
    private val CONFLICT_NOTIF_CHANNEL_ID = "TTS_CONFLICT_CHANNEL"

    private val TTS_NOTIFICATION_ID = 12345
    private val CONFLICT_NOTIFICATION_ID = 12346

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    /**
     * Create all required notification channels for Android 8.0+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channels")

            // Main TTS channel
            val ttsChannel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "KeyNews TTS Reading",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background reading of news articles"
            }

            // Media controls channel
            val mediaChannel = NotificationChannel(
                MEDIA_NOTIF_CHANNEL_ID,
                "KeyNews TTS Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls for news reading"
                setShowBadge(true)
            }

            // Conflict notification channel
            val conflictChannel = NotificationChannel(
                CONFLICT_NOTIF_CHANNEL_ID,
                "KeyNews TTS Conflicts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when TTS reading conflicts with other audio"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 200, 200)
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }

            try {
                notificationManager.createNotificationChannel(ttsChannel)
                notificationManager.createNotificationChannel(mediaChannel)
                notificationManager.createNotificationChannel(conflictChannel)
                Log.d(TAG, "Notification channels created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channels: ${e.message}", e)
            }
        }
    }

    /**
     * Build a basic TTS notification
     */
    fun buildTtsNotification(info: String): Notification {
        Log.d(TAG, "Building basic TTS notification: $info")

        // Create a pending intent for returning to the app
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for stop action
        val stopIntent = Intent(context, TtsReadingService::class.java).apply {
            action = TtsReadingService.ACTION_STOP_TTS
        }

        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val notification = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setContentTitle("KeyNews TTS Reading")
            .setContentText(info)
            .setSmallIcon(R.drawable.ic_record_voice_over)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()

            return notification
        } catch (e: Exception) {
            Log.e(TAG, "Error building TTS notification: ${e.message}", e)

            // Fallback to a simpler notification if there's an error
            return NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setContentTitle("KeyNews TTS Reading")
            .setContentText(info)
            .setSmallIcon(R.drawable.ic_record_voice_over)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .build()
        }
    }

    /**
     * Build a TTS notification with media controls
     * @param sessionToken MediaSession token to link notification with media controls
     */
    fun buildMediaControlNotification(title: String, articleTitle: String, sessionToken: MediaSessionCompat.Token? = null): Notification {
    Log.d(TAG, "Building media control notification: $title - $articleTitle")
    
    // Create a pending intent for returning to the app
    val mainIntent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    
    val mainPendingIntent = PendingIntent.getActivity(
    context,
    0,
    mainIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    // Create intent for stop action
    val stopIntent = Intent(context, TtsReadingService::class.java).apply {
    action = TtsReadingService.ACTION_STOP_TTS
    }
    
    val stopPendingIntent = PendingIntent.getService(
    context,
    1,
    stopIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    // Create intent for pause/resume action
    val pauseResumeIntent = Intent(context, TtsReadingService::class.java).apply {
    action = TtsReadingService.ACTION_TOGGLE_PAUSE_TTS
    }
    
    val pauseResumePendingIntent = PendingIntent.getService(
    context,
    2,
    pauseResumeIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    // Get appropriate pause/resume icon based on current state
    val isPaused = TtsReadingService.isPaused
    val pauseResumeIcon = if (isPaused) {
    android.R.drawable.ic_media_play
    } else {
    android.R.drawable.ic_media_pause
    }
    
    try {
    val builder = NotificationCompat.Builder(context, MEDIA_NOTIF_CHANNEL_ID)
    .setContentTitle(title)
    .setContentText(articleTitle)
    .setSmallIcon(R.drawable.ic_record_voice_over)
    .setContentIntent(mainPendingIntent)
    .setOngoing(true)
    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    .setPriority(NotificationCompat.PRIORITY_MAX)
    
    // Swap order: Add STOP button first for better visibility
    .addAction(
    android.R.drawable.ic_menu_close_clear_cancel,
    "Stop",
        stopPendingIntent
    )
    .addAction(
    pauseResumeIcon,
    if (isPaused) "Resume" else "Pause",
        pauseResumePendingIntent
                )
                // Ensure visibility on lock screen too
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // Apply media style to the notification
            val mediaStyle = MediaNotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1)
            
            // Link with media session if token provided
        sessionToken?.let {
            mediaStyle.setMediaSession(it)
            Log.d(TAG, "Setting media session token to notification")
        }
        
        builder.setStyle(mediaStyle)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

            return builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Error building media control notification: ${e.message}", e)

            // Fallback to a simple notification if there's an error
            return NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(articleTitle)
            .setSmallIcon(R.drawable.ic_record_voice_over)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
        }
    }

    /**
     * Show a notification about audio conflict
     * @param sessionInfo Information about the session that couldn't start
     * @return The created notification ID
     */
    fun showAudioConflictNotification(sessionInfo: String): Int {
        Log.d(TAG, "Showing audio conflict notification for session: $sessionInfo")

        // Create a pending intent for the main app
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for force-starting the session
        val forceStartIntent = Intent(context, TtsReadingService::class.java).apply {
            action = TtsReadingService.ACTION_FORCE_START_SESSION
            // We don't need to add any extras because pendingIntentParams in the service
            // already contains all the parameters needed to start the session
        }

        val forceStartPendingIntent = PendingIntent.getService(
            context,
            3,
            forceStartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Build and show notification
            val notification = NotificationCompat.Builder(context, CONFLICT_NOTIF_CHANNEL_ID)
            .setContentTitle("Audio Playback Conflict")
            .setContentText("$sessionInfo couldn't start because audio is playing")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(mainPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_media_play,
                "Play Anyway",
                forceStartPendingIntent
            )
            .build()

            notificationManager.notify(CONFLICT_NOTIFICATION_ID, notification)
            Log.d(TAG, "Audio conflict notification shown, ID: $CONFLICT_NOTIFICATION_ID")

            // Vibrate to alert the user (2 short buzzes)
            vibrate(longArrayOf(0, 200, 200, 200))

            return CONFLICT_NOTIFICATION_ID
        } catch (e: Exception) {
            Log.e(TAG, "Error showing audio conflict notification: ${e.message}", e)
            return -1
        }
    }

    /**
     * Update an existing notification
     */
    fun updateNotification(id: Int, notification: Notification) {
        try {
            notificationManager.notify(id, notification)
            Log.d(TAG, "Notification updated, ID: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification: ${e.message}", e)
        }
    }

    /**
     * Cancel a notification
     */
    fun cancelNotification(id: Int) {
        try {
            notificationManager.cancel(id)
            Log.d(TAG, "Notification canceled, ID: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling notification: ${e.message}", e)
        }
    }

    /**
     * Pattern vibration
     */
    private fun vibrate(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(pattern, -1)
                Log.d(TAG, "Vibrating device (Android 12+)")
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
                    Log.d(TAG, "Vibrating device (Android 8-11)")
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                    Log.d(TAG, "Vibrating device (pre-Android 8)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating device: ${e.message}", e)
        }
    }
}
// CodeCleaner_End_f6a74c54-8e05-45cb-b43d-920dd9cba7ab

