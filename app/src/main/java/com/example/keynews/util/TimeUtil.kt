package com.example.keynews.util

/**
 * Utility for working with timestamps and time formatting
 */
object TimeUtil {

    /**
     * Format the time as a relative string that can be used both for display and TTS.
     * Examples: "this minute", "5 minutes ago", "3 hours ago", "2 days ago"
     *
     * @param publishTimeMillis Publication time in milliseconds since epoch
     * @return Formatted relative time string
     */
    fun getRelativeTimeString(publishTimeMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - publishTimeMillis
        
        // Convert to appropriate units
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            seconds < 60 -> "this minute"
            minutes < 60 -> "${minutes} ${if (minutes == 1L) "minute" else "minutes"} ago"
            hours < 24 -> "${hours} ${if (hours == 1L) "hour" else "hours"} ago"
            else -> "${days} ${if (days == 1L) "day" else "days"} ago"
        }
    }
}
