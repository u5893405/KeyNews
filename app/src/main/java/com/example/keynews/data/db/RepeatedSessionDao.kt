package com.example.keynews.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.keynews.data.model.RepeatedSession
import com.example.keynews.data.model.RepeatedSessionRule
import com.example.keynews.data.model.RepeatedSessionWithRules


@Dao
interface RepeatedSessionDao {
    /**
     * Deletes all repeated sessions
     */
    @Query("DELETE FROM repeated_session")
    suspend fun deleteAllSessions()
    
    /**
     * Gets all sessions without rules
     */
    @Query("SELECT * FROM repeated_session")
    suspend fun getAllSessions(): List<RepeatedSession>
    
    /**
     * Gets all sessions with their rules
     */
    @Transaction
    @Query("SELECT * FROM repeated_session")
    suspend fun getAllSessionsWithRules(): List<RepeatedSessionWithRules>
    

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepeatedSession(session: RepeatedSession): Long
    
    /**
     * Alternative name for insertRepeatedSession to match naming in BackupManager
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RepeatedSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepeatedSessionRule(rule: RepeatedSessionRule): Long
    
    /**
     * Alternative name for insertRepeatedSessionRule to match naming in BackupManager
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RepeatedSessionRule): Long

    @Query("SELECT * FROM repeated_session")
    suspend fun getAllRepeatedSessions(): List<RepeatedSession>

    @Query("SELECT * FROM repeated_session WHERE id = :sessionId")
    suspend fun getRepeatedSessionById(sessionId: Long): RepeatedSession?

    @Query("SELECT * FROM repeated_session_rule WHERE sessionId = :sessionId")
    suspend fun getRulesForSession(sessionId: Long): List<RepeatedSessionRule>

    @Transaction
    @Query("SELECT * FROM repeated_session")
    suspend fun getRepeatedSessionsWithRules(): List<RepeatedSessionWithRules>

    @Transaction
    @Query("SELECT * FROM repeated_session WHERE id = :sessionId")
    suspend fun getRepeatedSessionWithRules(sessionId: Long): RepeatedSessionWithRules?

    @Query("DELETE FROM repeated_session WHERE id = :sessionId")
    suspend fun deleteRepeatedSessionById(sessionId: Long): Int

    @Query("DELETE FROM repeated_session_rule WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: Long): Int

    @Query("SELECT COUNT(*) FROM repeated_session_rule WHERE sessionId = :sessionId")
    suspend fun countRulesForSession(sessionId: Long): Int

    @Query("SELECT * FROM repeated_session_rule WHERE id = :ruleId")
    suspend fun getRepeatedSessionRule(ruleId: Long): RepeatedSessionRule?

    @androidx.room.Update
    suspend fun updateRule(rule: RepeatedSessionRule)

    @Query("UPDATE repeated_session_rule SET isActive = :isActive WHERE id = :ruleId")
    suspend fun updateRuleActiveState(ruleId: Long, isActive: Boolean)

    @Query("SELECT * FROM repeated_session_rule WHERE id = :ruleId")
    suspend fun getRuleById(ruleId: Long): RepeatedSessionRule?
}
