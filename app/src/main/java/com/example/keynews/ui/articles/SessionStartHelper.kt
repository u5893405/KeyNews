package com.example.keynews.ui.articles

// CodeCleaner_Start_30114cc8-007a-451b-aff9-a24badab984b
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.example.keynews.service.RepeatedSessionReceiver
import com.example.keynews.ui.rep_session.RepeatedSessionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Helper class to handle starting sessions with different options 
 */
class SessionStartHelper(private val context: Context) {
    
    private val TAG = "SessionStartHelper"
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    /**
     * Start a session now
     */
    fun startNow(sessionId: Long, onComplete: () -> Unit = {}) {
        RepeatedSessionScheduler.startSessionNow(context, sessionId)
        Toast.makeText(context, "Starting session now", Toast.LENGTH_SHORT).show()
        onComplete()
    }
    
    /**
     * Schedule a session to start after a delay (in minutes)
     */
    fun startAfterDelay(sessionId: Long, minutesDelay: Int, onComplete: () -> Unit = {}) {
        coroutineScope.launch {
            try {
                // Calculate the time when the session should start
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MINUTE, minutesDelay)
                
                // Get the alarm manager
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                
                // Create intent for the AlarmReceiver
                val intent = Intent(context, RepeatedSessionReceiver::class.java).apply {
                    action = RepeatedSessionReceiver.ACTION_TRIGGER_REPEATED_SESSION
                    putExtra(RepeatedSessionReceiver.EXTRA_SESSION_ID, sessionId)
                    // Set a unique action to ensure a new PendingIntent is created
                    putExtra("one_time", System.currentTimeMillis())
                }
                
                // Generate a unique request code for one-time scheduling
                val requestCode = (sessionId * 1000 + System.currentTimeMillis() % 1000).toInt()
                
                // Create PendingIntent
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // Schedule the alarm
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                    !alarmManager.canScheduleExactAlarms()) {
                    // Use inexact alarm on Android 12+ without permission
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Use exact alarm otherwise
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, 
                        "Session scheduled to start in $minutesDelay minutes", 
                        Toast.LENGTH_SHORT
                    ).show()
                    onComplete()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling session: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, 
                        "Error scheduling session: ${e.message}", 
                        Toast.LENGTH_SHORT
                    ).show()
                    onComplete()
                }
            }
        }
    }
    
    /**
     * Schedule a session to start at a specific time
     * @param timeString Format: "HH:mm"
     */
    fun startAtTime(sessionId: Long, timeString: String, onComplete: () -> Unit = {}) {
        coroutineScope.launch {
            try {
                // Parse the time
                val parts = timeString.split(":")
                if (parts.size != 2) {
                    throw IllegalArgumentException("Invalid time format. Expected HH:mm")
                }
                
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                
                // Set up calendar for the specified time
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                
                // If time is in the past, schedule for tomorrow
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                
                // Get the alarm manager
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                
                // Create intent for the AlarmReceiver
                val intent = Intent(context, RepeatedSessionReceiver::class.java).apply {
                    action = RepeatedSessionReceiver.ACTION_TRIGGER_REPEATED_SESSION
                    putExtra(RepeatedSessionReceiver.EXTRA_SESSION_ID, sessionId)
                    // Set a unique action to ensure a new PendingIntent is created
                    putExtra("one_time", System.currentTimeMillis())
                }
                
                // Generate a unique request code for one-time scheduling
                val requestCode = (sessionId * 1000 + System.currentTimeMillis() % 1000).toInt()
                
                // Create PendingIntent
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // Schedule the alarm
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                    !alarmManager.canScheduleExactAlarms()) {
                    // Use inexact alarm on Android 12+ without permission
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Use exact alarm otherwise
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, 
                        "Session scheduled to start at $timeString", 
                        Toast.LENGTH_SHORT
                    ).show()
                    onComplete()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling session: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, 
                        "Error scheduling session: ${e.message}", 
                        Toast.LENGTH_SHORT
                    ).show()
                    onComplete()
                }
            }
        }
    }
}
// CodeCleaner_End_30114cc8-007a-451b-aff9-a24badab984b

