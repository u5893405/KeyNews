package com.example.keynews.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation

/**
 * Represents a repeated reading session that can have multiple rules for when it should run.
 */
@Entity(tableName = "repeated_session")
data class RepeatedSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    val name: String,
    val feedId: Long,
    val headlinesPerSession: Int = 10,
    val delayBetweenHeadlinesSec: Int = 4,
    val readBody: Boolean = false,
    val articleAgeThresholdMinutes: Int? = null, // Threshold for filtering articles by age in minutes
    val announceArticleAge: Boolean = false // Whether to announce the article age before reading
)

/**
 * Represents a rule for when a repeated session should be triggered.
 */
@Entity(
    tableName = "repeated_session_rule",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = RepeatedSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("sessionId")]
)
data class RepeatedSessionRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val type: RuleType,
    var isActive: Boolean = false,

    // For timer-based (INTERVAL) rule
    val intervalMinutes: Int? = null,

    // For schedule-based (SCHEDULE) rule
    val timeOfDay: String? = null,  // Format: "HH:mm"
    val daysOfWeek: String? = null  // Format: "1,2,5" (where 1=Monday, 7=Sunday)
)

enum class RuleType {
    INTERVAL,  // Repeats every X minutes
    SCHEDULE   // Runs at specific times on specific days
}

/**
 * A class that combines a RepeatedSession with all its rules
 * Uses Room's relationship annotations to properly map database results
 */
data class RepeatedSessionWithRules(
    @Embedded
    val session: RepeatedSession,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val rules: List<RepeatedSessionRule>
)
