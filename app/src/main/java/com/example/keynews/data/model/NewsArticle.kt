package com.example.keynews.data.model

// CodeCleaner_Start_bcaf0097-c1b7-4979-94eb-45f9d435f079
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_article")
data class NewsArticle(
    @PrimaryKey
    val link: String,          // unique link as ID, or you could auto-generate
    val sourceId: Long,
    val title: String,
    val description: String?,
    val publishDateUtc: Long,  // store epoch millis in UTC
    val isRead: Boolean = false,
    val isReadLater: Boolean = false,  // Flag for articles marked for reading later
    val readLaterFeedId: Long? = null  // Optional field to track which feed it was added from
)
// CodeCleaner_End_bcaf0097-c1b7-4979-94eb-45f9d435f079

