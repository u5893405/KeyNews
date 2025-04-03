package com.example.keynews.data.model

// CodeCleaner_Start_2a6979d8-55bd-4465-b14f-369623c979df
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_source")
data class NewsSource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val rssUrl: String,
    // We can store an integer offset or a full timezone string
    val timezoneOffsetMinutes: Int = 0
)

// CodeCleaner_End_2a6979d8-55bd-4465-b14f-369623c979df
