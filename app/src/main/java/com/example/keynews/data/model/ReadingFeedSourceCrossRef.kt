package com.example.keynews.data.model

// CodeCleaner_Start_70c3aacb-b7f0-4c42-a235-c6472a6a7d0d
import androidx.room.Entity

@Entity(
    tableName = "reading_feed_source",
    primaryKeys = ["feedId", "sourceId"]
)
data class ReadingFeedSourceCrossRef(
    val feedId: Long,
    val sourceId: Long
)

// CodeCleaner_End_70c3aacb-b7f0-4c42-a235-c6472a6a7d0d
