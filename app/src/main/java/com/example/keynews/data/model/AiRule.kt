package com.example.keynews.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_rule")
data class AiRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val ruleText: String,
    val isWhitelist: Boolean // true = whitelist, false = blacklist
)
