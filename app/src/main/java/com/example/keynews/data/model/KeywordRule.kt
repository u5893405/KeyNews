package com.example.keynews.data.model

// CodeCleaner_Start_89c408cf-fa2e-4967-ace2-92478e3ba50a
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keyword_rule")
data class KeywordRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val isWhitelist: Boolean // true = whitelist, false = blacklist
    // We'll store the actual keywords in another table or we can do them as JSON.
    // Let's keep it simple and do a separate table for the keywords themselves:
)
// CodeCleaner_End_89c408cf-fa2e-4967-ace2-92478e3ba50a

