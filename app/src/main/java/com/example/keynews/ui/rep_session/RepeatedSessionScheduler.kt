package com.example.keynews.ui.rep_session

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.keynews.KeyNewsApp
import com.example.keynews.data.model.RepeatedSession
import com.example.keynews.data.model.RepeatedSessionRule
import com.example.keynews.data.model.RuleType
import com.example.keynews.service.RepeatedSessionReceiver
import com.example.keynews.service.TtsReadingService
import com.example.keynews.util.AlarmTimeTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Helper class for scheduling alarms for repeated sessions
 */
object RepeatedSessionScheduler {
    private const val TAG = "RepSessionScheduler"

    /**
     * Schedule an alarm for a specific rule
     */
    fun scheduleAlarmForRule(
        context: Context,
        rule: RepeatedSessionRule,
        session: RepeatedSession
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        when (rule.type) {
            RuleType.INTERVAL -> {
                // Only schedule interval alarms if they're active
                if (rule.isActive) {
                    scheduleIntervalAlarm(context, alarmManager, rule, session)
                }
            }
            RuleType.SCHEDULE -> scheduleTimeAlarm(context, alarmManager, rule, session)
        }
    }

    /**
     * Schedule an interval-based alarm (repeats every X minutes)
     */
    private fun scheduleIntervalAlarm(
        context: Context,
        alarmManager: AlarmManager,
        rule: RepeatedSessionRule,
        session: RepeatedSession
    ) {
        val intervalMinutes = rule.intervalMinutes ?: return
        val intervalMillis = intervalMinutes * 60 * 1000L

        val intent = Intent(context, RepeatedSessionReceiver::class.java).apply {
            action = RepeatedSessionReceiver.ACTION_TRIGGER_REPEATED_SESSION
            putExtra(RepeatedSessionReceiver.EXTRA_SESSION_ID, session.id!!)
            putExtra(RepeatedSessionReceiver.EXTRA_RULE_ID, rule.id)
        }

        val requestCode = rule.id.toInt()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // We schedule the alarm to run after 'intervalMillis' from now (elapsedRealtime)
        val triggerTime = SystemClock.elapsedRealtime() + intervalMillis

        Log.d(TAG, "Scheduling interval alarm for rule ${rule.id}, interval: $intervalMinutes minutes")

        try {
            // setExact vs setRepeating logic, or exactAndAllowWhileIdle, etc.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    intervalMillis,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            // *** THIS LINE IS CRUCIAL ***
            AlarmTimeTracker.saveNextAlarmTime(context, rule.id, triggerTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling interval alarm: ${e.message}", e)
        }
    }

    fun scheduleOneTimeIntervalStart(context: Context, rule: RepeatedSessionRule, delayMillis: Long) {
        if (rule.intervalMinutes == null) {
            Log.e(TAG, "scheduleOneTimeIntervalStart: Rule has no intervalMinutes set, ignoring.")
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, RepeatedSessionReceiver::class.java).apply {
            action = RepeatedSessionReceiver.ACTION_TRIGGER_REPEATED_SESSION
            putExtra(RepeatedSessionReceiver.EXTRA_SESSION_ID, rule.sessionId)
            putExtra(RepeatedSessionReceiver.EXTRA_RULE_ID, rule.id)
            putExtra("one_time", System.currentTimeMillis())
        }

        val requestCode = (rule.id + System.currentTimeMillis() % 1000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = SystemClock.elapsedRealtime() + delayMillis
        Log.d(TAG, "scheduleOneTimeIntervalStart - ruleId=${rule.id}, in $delayMillis ms")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            // *** CRITICAL: store the time so InfoBarManager can pick it up! ***
            AlarmTimeTracker.saveNextAlarmTime(context, rule.id, triggerTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling one-time interval start: ${e.message}", e)
        }
    }

    /**
     * Schedule a time-based alarm (runs at specific times on specific days)
     */
    private fun scheduleTimeAlarm(
        context: Context,
        alarmManager: AlarmManager,
        rule: RepeatedSessionRule,
        session: RepeatedSession
    ) {
        val timeOfDay = rule.timeOfDay ?: return
        val daysOfWeek = rule.daysOfWeek ?: return

        // Parse the time
        val (hour, minute) = timeOfDay.split(":").map { it.toInt() }

        // Parse days of week into a list of ints (1-7, where 1 is Monday)
        val days = daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
        if (days.isEmpty()) return

            // Create the calendar for the next occurrence of this schedule
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            // If time is in the past for today, move to next day
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            // Find the next day that matches one of our specified days
            val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            // Convert from Calendar's day of week (1=Sunday) to our format (1=Monday)
            val todayConverted = if (today == Calendar.SUNDAY) 7 else today - 1

            var daysToAdd = 0
            var dayToFind = todayConverted

            // Check up to 7 days (a full week)
            for (i in 0 until 7) {
                dayToFind = ((todayConverted + i - 1) % 7) + 1
                if (days.contains(dayToFind)) {
                    daysToAdd = i
                    break
                }
            }

            calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)

            // Create the pending intent
            val intent = Intent(context, RepeatedSessionReceiver::class.java).apply {
                action = RepeatedSessionReceiver.ACTION_TRIGGER_REPEATED_SESSION
                putExtra(RepeatedSessionReceiver.EXTRA_SESSION_ID, session.id!!)
                putExtra(RepeatedSessionReceiver.EXTRA_RULE_ID, rule.id)
            }

            val requestCode = rule.id.toInt()

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            Log.d(TAG, "Scheduling time alarm for rule ${rule.id}, time: $timeOfDay, next trigger: ${calendar.time}")

            try {
                // Handle different Android versions appropriately:
                // Android 9-11: Can use exact alarms without special permission
                // Android 12+ (S): Need SCHEDULE_EXACT_ALARM permission for exact alarms
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    // On Android 12+, if we don't have SCHEDULE_EXACT_ALARM permission, use inexact alarms
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // For Android 6.0+ (M) up to Android 12 or with permission, use setExactAndAllowWhileIdle
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // For older versions, fall back to setExact
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling time alarm: ${e.message}")
            }
    }

    /**
     * Cancel all alarms for a specific session
     */
    fun cancelAlarmsForSession(context: Context, sessionId: Long) {
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val dataManager = (context.applicationContext as? KeyNewsApp)?.dataManager
                if (dataManager != null) {
                    val rules = dataManager.database.repeatedSessionDao().getRulesForSession(sessionId)

                    rules.forEach { rule ->
                        cancelAlarmForRule(context, rule.id)
                    }

                    Log.d(TAG, "Cancelled all alarms for session $sessionId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling alarms for session $sessionId: ${e.message}")
            }
        }
    }

    /**
     * Cancel a specific rule's alarm
     */
    fun cancelAlarmForRule(context: Context, ruleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Create an intent matching the one used to set the alarm
        val intent = Intent(context, RepeatedSessionReceiver::class.java).apply {
            action = RepeatedSessionReceiver.ACTION_TRIGGER_REPEATED_SESSION
            // We need to include the rule ID to properly match the existing PendingIntent
            putExtra(RepeatedSessionReceiver.EXTRA_RULE_ID, ruleId)
        }

        val requestCode = ruleId.toInt()

        // Use FLAG_UPDATE_CURRENT to ensure we match the existing intent even if extras have changed
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        // If the PendingIntent exists, cancel it
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for rule $ruleId")
        } else {
            // Try with a more generic intent as fallback
            val fallbackIntent = Intent(context, RepeatedSessionReceiver::class.java).apply {
                action = RepeatedSessionReceiver.ACTION_TRIGGER_REPEATED_SESSION
            }
            
            val fallbackPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                fallbackIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (fallbackPendingIntent != null) {
                alarmManager.cancel(fallbackPendingIntent)
                fallbackPendingIntent.cancel()
                Log.d(TAG, "Cancelled alarm for rule $ruleId using fallback intent")
            }
        }
    }

    /**
     * Start a session immediately
     */
    fun startSessionNow(context: Context, sessionId: Long) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val dataManager = (context.applicationContext as? KeyNewsApp)?.dataManager
                if (dataManager != null) {
                    val sessionDao = dataManager.database.repeatedSessionDao()
                    val session = sessionDao.getRepeatedSessionById(sessionId)

                    // --- OPTIONAL NEW CODE: ensure all interval rules become active when user forcibly starts them
                    if (session != null) {
                        val intervalRules = sessionDao.getRulesForSession(sessionId)
                        .filter { it.type == RuleType.INTERVAL && !it.isActive }
                        intervalRules.forEach { rule ->
                            rule.isActive = true
                            sessionDao.updateRule(rule)
                            Log.d(TAG, "Activated interval rule ${rule.id} for session $sessionId in startSessionNow()")
                        }
                    }
                    // --- END OPTIONAL NEW CODE

                    if (session != null) {
                        withContext(Dispatchers.Main) {
                            val serviceIntent = Intent(context, TtsReadingService::class.java).apply {
                                putExtra(TtsReadingService.EXTRA_FEED_ID, session.feedId)
                                putExtra(TtsReadingService.EXTRA_HEADLINES_LIMIT, session.headlinesPerSession)
                                putExtra(TtsReadingService.EXTRA_DELAY_BETWEEN_HEADLINES, session.delayBetweenHeadlinesSec)
                                putExtra(TtsReadingService.EXTRA_READ_BODY, session.readBody)
                                putExtra(TtsReadingService.EXTRA_SESSION_TYPE, TtsReadingService.SESSION_TYPE_REPEATED)
                                putExtra(TtsReadingService.EXTRA_SESSION_NAME, session.name)
                                putExtra(TtsReadingService.EXTRA_ARTICLE_AGE_THRESHOLD, session.articleAgeThresholdMinutes)
                                putExtra(TtsReadingService.EXTRA_ANNOUNCE_ARTICLE_AGE, session.announceArticleAge)
                                putExtra(TtsReadingService.EXTRA_READING_MODE, "unread")
                                putExtra(RepeatedSessionReceiver.EXTRA_SESSION_ID, sessionId)
                            }

                            ContextCompat.startForegroundService(context, serviceIntent)
                            Log.d(TAG, "Started session $sessionId immediately")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting session $sessionId: ${e.message}")
            }
        }
    }

    /**
     * Reschedule all alarms for a session (used when a rule is triggered)
     */
    fun rescheduleAlarmsForSession(context: Context, sessionId: Long) {
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val dataManager = (context.applicationContext as? KeyNewsApp)?.dataManager
                if (dataManager != null) {
                    val sessionWithRules = dataManager.database.repeatedSessionDao()
                    .getRepeatedSessionWithRules(sessionId)

                    if (sessionWithRules != null) {
                        val session = sessionWithRules.session
                        val rules = sessionWithRules.rules

                        // First, cancel all existing alarms for this session
                        for (rule in rules) {
                            cancelAlarmForRule(context, rule.id)
                        }

                        // Then, schedule only the active rules
                        for (rule in rules) {
                            if (rule.type == RuleType.SCHEDULE || rule.isActive) {
                                scheduleAlarmForRule(context, rule, session)
                            }
                        }

                        Log.d(TAG, "Rescheduled alarms for session $sessionId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms for session $sessionId: ${e.message}")
            }
        }
    }
}
