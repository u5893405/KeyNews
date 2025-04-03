package com.example.keynews.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.ui.rep_session.RepeatedSessionScheduler
import com.example.keynews.data.model.RuleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Receiver for repeated session alarms.
 * Triggered by the AlarmManager to start repeated reading sessions.
 */
class RepeatedSessionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "RepSessionReceiver"
        
        // Action constants
        const val ACTION_TRIGGER_REPEATED_SESSION = "com.example.keynews.ACTION_TRIGGER_REPEATED_SESSION"
        const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        const val ACTION_SESSION_STARTED = "com.example.keynews.ACTION_SESSION_STARTED"
        
        // Extra constants
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_SESSION_NAME = "session_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent action: ${intent.action}")
        
        when (intent.action) {
            ACTION_TRIGGER_REPEATED_SESSION -> {
                handleRepeatedSession(context, intent)
            }
            ACTION_BOOT_COMPLETED -> {
                // Device has rebooted, reschedule all alarms
                handleBootCompleted(context)
            }
        }
    }
    
    // In RepeatedSessionReceiver.kt

    private fun handleRepeatedSession(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
        if (sessionId == -1L) {
            Log.e(TAG, "Invalid session ID")
            return
        }

        Log.d(TAG, "Triggering repeated session $sessionId (rule $ruleId)")

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val dataManager = (context.applicationContext as? KeyNewsApp)?.dataManager
                if (dataManager != null) {
                    val sessionDao = dataManager.database.repeatedSessionDao()
                    val session = sessionDao.getRepeatedSessionById(sessionId)
                    val rule = sessionDao.getRepeatedSessionRule(ruleId)

                    if (session != null && rule != null) {
                        // --- NEW CODE: If an interval rule is triggered and is not active, activate it so the next alarm schedules.
                        if (rule.type == RuleType.INTERVAL && !rule.isActive) {
                            rule.isActive = true
                            sessionDao.updateRule(rule)
                            Log.d(TAG, "Activated interval rule $ruleId because session was explicitly triggered.")
                        }
                        // --- END NEW CODE

                        val broadcastIntent = Intent(ACTION_SESSION_STARTED).apply {
                            putExtra(EXTRA_SESSION_ID, sessionId)
                            putExtra(EXTRA_SESSION_NAME, session.name)
                        }
                        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)

                        withContext(Dispatchers.Main) {
                            val serviceIntent = Intent(context, TtsReadingService::class.java).apply {
                                putExtra(TtsReadingService.EXTRA_FEED_ID, session.feedId)
                                putExtra(TtsReadingService.EXTRA_HEADLINES_LIMIT, session.headlinesPerSession)
                                putExtra(TtsReadingService.EXTRA_DELAY_BETWEEN_HEADLINES, session.delayBetweenHeadlinesSec)
                                putExtra(TtsReadingService.EXTRA_READ_BODY, session.readBody)
                                putExtra(TtsReadingService.EXTRA_SESSION_NAME, session.name)
                                putExtra(TtsReadingService.EXTRA_SESSION_TYPE, TtsReadingService.SESSION_TYPE_REPEATED)
                                putExtra(TtsReadingService.EXTRA_READING_MODE, "unread")
                                putExtra(TtsReadingService.EXTRA_ARTICLE_AGE_THRESHOLD, session.articleAgeThresholdMinutes)
                                putExtra(TtsReadingService.EXTRA_ANNOUNCE_ARTICLE_AGE, session.announceArticleAge)
                                putExtra(RepeatedSessionReceiver.EXTRA_SESSION_ID, sessionId)
                                putExtra(RepeatedSessionReceiver.EXTRA_RULE_ID, ruleId)

                                Log.d(TAG, "handleRepeatedSession - Using feed ID: ${session.feedId} for session: ${session.name}")
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ContextCompat.startForegroundService(context, serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }

                        // For schedule-based rules, we re-schedule right away
                        if (rule.type == RuleType.SCHEDULE) {
                            RepeatedSessionScheduler.rescheduleAlarmsForSession(context, sessionId)
                        }
                    } else {
                        Log.e(TAG, "Session $sessionId or rule $ruleId not found")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling repeated session: ${e.message}")
            }
        }
    }



    
    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Device booted, rescheduling all alarms")
        
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val dataManager = (context.applicationContext as? KeyNewsApp)?.dataManager
                if (dataManager != null) {
                    val allSessions = dataManager.database.repeatedSessionDao().getRepeatedSessionsWithRules()
                    
                    for (sessionWithRules in allSessions) {
                        val session = sessionWithRules.session
                        val rules = sessionWithRules.rules
                        
                        for (rule in rules) {
                            RepeatedSessionScheduler.scheduleAlarmForRule(context, rule, session)
                        }
                    }
                    
                    Log.d(TAG, "Rescheduled alarms for ${allSessions.size} sessions")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms after boot: ${e.message}")
            }
        }
    }
}
