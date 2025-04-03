package com.example.keynews.ui.articles
// CodeCleaner_Start_7c0628b0-336d-4839-8360-0079d1e4f43c
import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.keynews.data.model.RepeatedSessionRule
import com.example.keynews.data.model.RepeatedSessionWithRules
import com.example.keynews.data.model.RuleType
import com.example.keynews.util.AlarmTimeTracker
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Manager class for the information bar in ArticlesFragment.
 * Shows the name of the next session to run (if any) and a countdown until it starts,
 * plus reading progress if a session is currently reading.
 */
class InfoBarManager(
    private val context: Context,                 // <-- add context so we can query AlarmTimeTracker
        private val tvSessionStatus: TextView,
            private val tvNextSessionTimer: TextView,
                private val tvReadingProgress: TextView
) {

    private var countdownTimer: CountDownTimer? = null
        private var currentArticleIndex: Int = 0
            private var totalArticles: Int = 0
                private var currentSessionName: String = ""

                    /**
                     * Update the session status portion of the info bar.
                     * Pass a list of all sessions that have at least one active rule.
                     */
                    fun updateSessionStatus(enabledSessions: List<RepeatedSessionWithRules>) {
                        countdownTimer?.cancel()
                        countdownTimer = null

                        if (enabledSessions.isEmpty()) {
                            tvSessionStatus.text = "No repeated sessions rules active"
                            tvNextSessionTimer.text = ""
                            return
                        }

                        // Among all *active* rules in these sessions, find the earliest future time
                        val (earliestSession, earliestTime) = findNextSession(enabledSessions)

                        if (earliestSession == null || earliestTime == null) {
                            tvSessionStatus.text = "No upcoming sessions"
                            tvNextSessionTimer.text = ""
                        } else {
                            currentSessionName = earliestSession.session.name
                            tvSessionStatus.text = "Next: $currentSessionName"
                            startCountdownTimer(earliestTime)
                        }
                    }

                    /**
                     * Update the reading progress part of the info bar.
                     */
                    fun updateReadingProgress(currentIndex: Int, total: Int) {
                        currentArticleIndex = currentIndex
                        totalArticles = total

                        if (currentIndex > 0 && total > 0) {
                            tvReadingProgress.text = "Now reading ($currentSessionName): $currentIndex of $total"
                        } else {
                            tvReadingProgress.text = "Reading session: not active"
                        }
                    }

                    /**
                     * Reset the reading progress (e.g., called when TTS finishes).
                     */
                    fun resetReadingProgress() {
                        currentArticleIndex = 0
                        totalArticles = 0
                        tvReadingProgress.text = "Reading session: not active"
                    }

                    /**
                     * Find the next session's rule that will run soonest, and return that session + time in ms.
                     */
                    private fun findNextSession(
                        sessions: List<RepeatedSessionWithRules>
                    ): Pair<RepeatedSessionWithRules?, Long?> {
                        var earliestSession: RepeatedSessionWithRules? = null
                        var earliestTime: Long? = null
                        val now = System.currentTimeMillis()

                        for (sessionWithRules in sessions) {
                            for (rule in sessionWithRules.rules) {
                                if (!rule.isActive) continue

                                    // figure out next run time
                                    val nextTime = calculateNextRunTime(rule)
                                    if (nextTime > now) {
                                        if (earliestTime == null || nextTime < earliestTime) {
                                            earliestTime = nextTime
                                            earliestSession = sessionWithRules
                                        }
                                    }
                            }
                        }

                        return Pair(earliestSession, earliestTime)
                    }

                    /**
                     * Calculate the next run time for a rule.
                     * For interval rules, we first see if there's a pending alarm time tracked in AlarmTimeTracker.
                     * If so, we convert it to absolute time and use it. If not, we do naive "now + interval".
                     * For scheduled rules, we do the typical next-scheduled-day approach as before.
                     */
                    private fun calculateNextRunTime(rule: RepeatedSessionRule): Long {
                        val now = System.currentTimeMillis()

                        return when (rule.type) {
                            RuleType.INTERVAL -> {
                                val nextAlarmElapsed = AlarmTimeTracker.getNextAlarmTime(context, rule.id)
                                if (nextAlarmElapsed > 0) {
                                    val nextAlarmAbsolute = AlarmTimeTracker.convertToAbsoluteTime(nextAlarmElapsed)
                                    if (nextAlarmAbsolute > now) {
                                        return nextAlarmAbsolute
                                    }
                                }
                                // If we get here, no alarm is really scheduled yet, so do NOT invent a next time
                                -1L  // indicates “no next run”
                            }

                            RuleType.SCHEDULE -> {
                                val timeOfDay = rule.timeOfDay ?: return Long.MAX_VALUE
                                val daysOfWeek = rule.daysOfWeek?.split(",")?.mapNotNull { it.toIntOrNull() }
                                ?: return Long.MAX_VALUE

                                val calendar = Calendar.getInstance()
                                val parts = timeOfDay.split(":")
                                if (parts.size != 2) return Long.MAX_VALUE

                                    return try {
                                        val hour = parts[0].toInt()
                                        val minute = parts[1].toInt()

                                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                                        calendar.set(Calendar.MINUTE, minute)
                                        calendar.set(Calendar.SECOND, 0)
                                        calendar.set(Calendar.MILLISECOND, 0)

                                        if (calendar.timeInMillis < now) {
                                            // if it's in the past for today, we add 1 day before searching
                                            calendar.add(Calendar.DAY_OF_MONTH, 1)
                                        }

                                        // check up to 7 days to find the next match
                                        for (i in 0 until 7) {
                                            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                                            // convert to 1..7 for Mon..Sun
                                            val mappedDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
                                            if (daysOfWeek.contains(mappedDay)) {
                                                return calendar.timeInMillis
                                            }
                                            calendar.add(Calendar.DAY_OF_MONTH, 1)
                                        }

                                        // if none matched in a full 7-day cycle, no valid next time
                                        Long.MAX_VALUE
                                    } catch (e: Exception) {
                                        Long.MAX_VALUE
                                    }
                            }
                        }
                    }

                    /**
                     * Start a countdown timer until [targetTime].
                     */
                    private fun startCountdownTimer(targetTime: Long) {
                        countdownTimer?.cancel()

                        val now = System.currentTimeMillis()
                        val timeUntilNext = targetTime - now

                        if (timeUntilNext <= 0) {
                            tvNextSessionTimer.text = "Starting now"
                            return
                        }

                        countdownTimer = object : CountDownTimer(timeUntilNext, 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                                tvNextSessionTimer.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                            }

                            override fun onFinish() {
                                tvNextSessionTimer.text = "Starting now"
                                Handler(Looper.getMainLooper()).postDelayed({
                                    tvNextSessionTimer.text = "Refreshing..."
                                }, 5000)
                            }
                        }.start()
                    }

                    /**
                     * Clean up resources when no longer needed
                     */
                    fun cleanup() {
                        countdownTimer?.cancel()
                        countdownTimer = null
                    }
}
// CodeCleaner_End_7c0628b0-336d-4839-8360-0079d1e4f43c
