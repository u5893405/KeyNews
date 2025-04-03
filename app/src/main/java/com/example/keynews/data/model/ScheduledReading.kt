package com.example.keynews.data.model

// CodeCleaner_Start_6ef6466a-3f91-4ce6-b876-6e4efb9c413c
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_reading")
data class ScheduledReading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val feedId: Long,
    val daysOfWeek: String,  // "Mon,Tue,Fri" or store as bitmask
    val timesOfDay: String,  // "08:00,13:00" or separate table
    val headlinesLimit: Int,
    val delayBetweenArticlesSec: Int
)
// CodeCleaner_End_6ef6466a-3f91-4ce6-b876-6e4efb9c413c

