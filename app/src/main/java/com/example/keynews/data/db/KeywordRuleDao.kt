package com.example.keynews.data.db

// CodeCleaner_Start_4a8b82e8-0930-42d1-aa98-1d54064ac2e9
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.keynews.data.model.KeywordRule
import com.example.keynews.data.model.KeywordItem

@Dao
interface KeywordRuleDao {
    /**
     * Deletes all keyword rules and their items
     */
    @Query("DELETE FROM keyword_rule")
    suspend fun deleteAllRules()
    
    
    /**
     * Inserts a keyword rule and returns its ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: KeywordRule): Long

    /**
     * Inserts a keyword item
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyword(item: KeywordItem): Long

    @Query("SELECT * FROM keyword_rule")
    suspend fun getAllRules(): List<KeywordRule>

    @Delete
    suspend fun deleteRule(rule: KeywordRule): Int // Delete KeywordRule entity

    @Query("DELETE FROM keyword_rule WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: Long): Int

    @Query("SELECT * FROM keyword_item WHERE ruleId = :ruleId")
    suspend fun getKeywordsForRule(ruleId: Long): List<KeywordItem>

    @Delete
    suspend fun deleteKeyword(keyword: KeywordItem): Int // Delete KeywordItem entity

    @Query("DELETE FROM keyword_item WHERE id = :keywordId")
    suspend fun deleteKeywordById(keywordId: Long): Int
}
// CodeCleaner_End_4a8b82e8-0930-42d1-aa98-1d54064ac2e9