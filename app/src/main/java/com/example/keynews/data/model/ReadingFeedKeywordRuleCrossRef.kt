package com.example.keynews.data.model

// CodeCleaner_Start_401e539c-34f9-4196-a076-f15aa4d2e386
import androidx.room.Entity

@Entity(
    tableName = "reading_feed_keyword_rule",
    primaryKeys = ["feedId", "ruleId"]
)
data class ReadingFeedKeywordRuleCrossRef(
    val feedId: Long,
    val ruleId: Long
)
// CodeCleaner_End_401e539c-34f9-4196-a076-f15aa4d2e386

