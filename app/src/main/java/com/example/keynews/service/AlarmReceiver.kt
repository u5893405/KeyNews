package com.example.keynews.service

// CodeCleaner_Start_fb5b3ee5-d46f-4c9f-a077-04b3377696f4
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.keynews.service.TtsReadingService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val feedId = intent.getLongExtra("feedId", -1L)
        val limit = intent.getIntExtra("limit", 5)
        val readBody = intent.getBooleanExtra("readBody", false)

        val serviceIntent = Intent(context, TtsReadingService::class.java).apply {
            putExtra(TtsReadingService.EXTRA_FEED_ID, feedId)
            putExtra(TtsReadingService.EXTRA_HEADLINES_LIMIT, limit)
            putExtra(TtsReadingService.EXTRA_READ_BODY, readBody)
            // For scheduled sessions we always use "unread" mode.
            putExtra(TtsReadingService.EXTRA_READING_MODE, "unread")
            putExtra(TtsReadingService.EXTRA_SESSION_NAME, "Scheduled Reading")
            putExtra(TtsReadingService.EXTRA_SESSION_TYPE, TtsReadingService.SESSION_TYPE_SCHEDULED)
        }

        // Start the service appropriately based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ requires startForegroundService
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            // Pre-Oreo can use regular startService
            context.startService(serviceIntent)
        }
    }
}
// CodeCleaner_End_fb5b3ee5-d46f-4c9f-a077-04b3377696f4

