package com.example.keynews.data.model

// CodeCleaner_Start_ae73b887-ac8f-4ca8-abc2-901a3099369c
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keyword_item")
data class KeywordItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val ruleId: Long,      // Foreign key to 'keyword_rule' table
    val keyword: String,
    val isCaseSensitive: Boolean = false,  // Whether the keyword is case-sensitive
    val isFullWordMatch: Boolean = false   // Whether to match only full words
)
// CodeCleaner_End_ae73b887-ac8f-4ca8-abc2-901a3099369c

