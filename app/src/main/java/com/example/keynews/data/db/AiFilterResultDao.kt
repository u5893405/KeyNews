package com.example.keynews.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.keynews.data.model.AiFilterResult

/**
 * Data Access Object (DAO) for AI filtering results cache.
 */
@Dao
interface AiFilterResultDao {
    /**
     * Insert a single AI filtering result
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(filterResult: AiFilterResult)
    
    /**
     * Insert multiple AI filtering results
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(filterResults: List<AiFilterResult>)
    
    /**
     * Get AI filtering result for a specific article
     */
    @Query("SELECT * FROM ai_filter_result WHERE articleLink = :articleLink LIMIT 1")
    suspend fun getByLink(articleLink: String): AiFilterResult?
    
    /**
     * Get all cached AI filtering results
     */
    @Query("SELECT * FROM ai_filter_result")
    suspend fun getAll(): List<AiFilterResult>
    
    /**
     * Delete all cached AI filtering results
     */
    @Query("DELETE FROM ai_filter_result")
    suspend fun deleteAll()
    
    /**
     * Delete filtering results older than a specific timestamp
     */
    @Query("DELETE FROM ai_filter_result WHERE filterTimestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int
    
    /**
     * Count total number of cached results
     */
    @Query("SELECT COUNT(*) FROM ai_filter_result")
    suspend fun count(): Int
    
    /**
     * Get links that pass the filter
     */
    @Query("SELECT articleLink FROM ai_filter_result WHERE passesFilter = 1")
    suspend fun getPassingLinks(): List<String>
    
    /**
     * Get links that don't pass the filter
     */
    @Query("SELECT articleLink FROM ai_filter_result WHERE passesFilter = 0")
    suspend fun getFailingLinks(): List<String>
}
