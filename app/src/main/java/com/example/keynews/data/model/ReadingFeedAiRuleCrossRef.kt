package com.example.keynews.data.model

import androidx.room.Entity

@Entity(
    tableName = "reading_feed_ai_rule",
    primaryKeys = ["feedId", "ruleId"]
)
data class ReadingFeedAiRuleCrossRef(
    val feedId: Long,
    val ruleId: Long,
    val isWhitelist: Boolean  // Store whether this is a whitelist or blacklist selection
)
