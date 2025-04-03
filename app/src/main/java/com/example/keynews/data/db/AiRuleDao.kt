package com.example.keynews.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.keynews.data.model.AiRule

@Dao
interface AiRuleDao {
    /**
     * Gets all AI rules
     */
    @Query("SELECT * FROM ai_rule")
    suspend fun getAllRules(): List<AiRule>

    /**
     * Inserts an AI rule and returns its ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AiRule): Long

    /**
     * Deletes an AI rule
     */
    @Delete
    suspend fun deleteRule(rule: AiRule): Int

    /**
     * Deletes an AI rule by ID
     */
    @Query("DELETE FROM ai_rule WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: Long): Int

    /**
     * Gets an AI rule by ID
     */
    @Query("SELECT * FROM ai_rule WHERE id = :ruleId")
    suspend fun getRuleById(ruleId: Long): AiRule?
}
