package com.example.keynews.data.db

// CodeCleaner_Start_049efb9a-0ab9-4f5e-9aa8-d2bb4c5c9589
// Source file: java/com/example/keynews/data/db/NewsSourceDao.kt

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.keynews.data.model.NewsSource

@Dao
interface NewsSourceDao {
    /**
     * Deletes all sources
     */
    @Query("DELETE FROM news_source")
    suspend fun deleteAllSources()
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<NewsSource>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: NewsSource): Long

    @Query("SELECT * FROM news_source")
    suspend fun getAllSources(): List<NewsSource>
    
    @Query("SELECT * FROM news_source WHERE id IN (:sourceIds)")
    suspend fun getSourcesByIds(sourceIds: List<Long>): List<NewsSource>

    @Delete
    suspend fun delete(source: NewsSource): Int // Delete by entity

    @Query("DELETE FROM news_source WHERE id = :sourceId")
    suspend fun deleteById(sourceId: Long): Int
}
// CodeCleaner_End_049efb9a-0ab9-4f5e-9aa8-d2bb4c5c9589

