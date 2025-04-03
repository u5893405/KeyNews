package com.example.keynews.data.model

// CodeCleaner_Start_f1c4f8f1-30e6-48fb-abc8-099b33c5c345
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_feed")
data class ReadingFeed(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val isDefault: Boolean = false  // New field
)
// CodeCleaner_End_f1c4f8f1-30e6-48fb-abc8-099b33c5c345

