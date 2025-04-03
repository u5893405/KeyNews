package com.example.keynews.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an AI filtering result for a specific article.
 * Used to cache filtering decisions to avoid redundant processing.
 */
@Entity(tableName = "ai_filter_result")
data class AiFilterResult(
    @PrimaryKey
    val articleLink: String,
    val passesFilter: Boolean,
    val filterTimestamp: Long = System.currentTimeMillis()
)
