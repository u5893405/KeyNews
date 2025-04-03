package com.example.keynews.util

// CodeCleaner_Start_c3955109-54b4-4204-b0f6-55f1eb062af8
import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * Utility for tracking interval rule alarm times
 */
object AlarmTimeTracker {
    private const val TAG = "AlarmTimeTracker"
    private const val PREFS_NAME = "keynews_alarm_times"
    private const val KEY_PREFIX = "next_alarm_time_rule_"

    /**
     * Save the next alarm time for a rule
     * @param context Application context
     * @param ruleId Rule ID
     * @param nextTimeMs Next alarm time in elapsed realtime milliseconds
     */
    fun saveNextAlarmTime(context: Context, ruleId: Long, nextTimeMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("$KEY_PREFIX$ruleId", nextTimeMs).apply()
        Log.d(TAG, "Saved next alarm time for rule $ruleId: $nextTimeMs")
    }

    /**
     * Get the next alarm time for a rule
     * @param context Application context
     * @param ruleId Rule ID
     * @return next alarm time in elapsed realtime milliseconds, or -1 if not set
     */
    fun getNextAlarmTime(context: Context, ruleId: Long): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val time = prefs.getLong("$KEY_PREFIX$ruleId", -1L)
        
        // Check if the time is in the past
        if (time > 0 && time < SystemClock.elapsedRealtime()) {
            // Time is in the past, clear it
            clearAlarmTime(context, ruleId)
            return -1L
        }
        
        return time
    }

    /**
     * Convert an elapsed realtime timestamp to absolute time
     * @param elapsedRealtimeMs Timestamp in elapsed realtime milliseconds
     * @return Timestamp in absolute time (System.currentTimeMillis() equivalent)
     */
    fun convertToAbsoluteTime(elapsedRealtimeMs: Long): Long {
        if (elapsedRealtimeMs <= 0) return -1L
        
        val elapsedSinceBootMs = SystemClock.elapsedRealtime()
        val currentTimeMs = System.currentTimeMillis()
        
        // Calculate time base (when the device was booted in absolute time)
        val timeBase = currentTimeMs - elapsedSinceBootMs
        
        // Calculate when the alarm will fire in absolute time
        return timeBase + elapsedRealtimeMs
    }

    /**
     * Clear stored alarm time for a rule
     */
    fun clearAlarmTime(context: Context, ruleId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_PREFIX$ruleId").apply()
        Log.d(TAG, "Cleared alarm time for rule $ruleId")
    }
}
// CodeCleaner_End_c3955109-54b4-4204-b0f6-55f1eb062af8

