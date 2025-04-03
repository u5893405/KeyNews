package com.example.keynews.data.db

// CodeCleaner_Start_628d9479-edb0-4631-bd59-edfe1fa3669b
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Transaction
import com.example.keynews.data.model.AiRule
import com.example.keynews.data.model.KeywordRule
import com.example.keynews.data.model.ReadingFeed
import com.example.keynews.data.model.ReadingFeedAiRuleCrossRef
import com.example.keynews.data.model.ReadingFeedKeywordRuleCrossRef
import com.example.keynews.data.model.ReadingFeedSourceCrossRef

@Dao
interface ReadingFeedDao {
    /**
     * Deletes all feeds and their associations
     */
    @Query("DELETE FROM reading_feed")
    suspend fun deleteAllFeeds()
    
    /**
     * Gets all source cross references for a feed
     */
    @Query("SELECT * FROM reading_feed_source WHERE feedId = :feedId")
    suspend fun getSourceRefsForFeed(feedId: Long): List<ReadingFeedSourceCrossRef>
    
    /**
     * Gets all keyword rule cross references for a feed
     */
    @Query("SELECT * FROM reading_feed_keyword_rule WHERE feedId = :feedId")
    suspend fun getKeywordRuleRefsForFeed(feedId: Long): List<ReadingFeedKeywordRuleCrossRef>
    

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingFeed(feed: ReadingFeed): Long

    @Query("SELECT * FROM reading_feed")
    suspend fun getAllFeeds(): List<ReadingFeed>
    
    // Added method for RepeatedSessionDialogFragment
    @Query("SELECT * FROM reading_feed")
    suspend fun getAllReadingFeeds(): List<ReadingFeed>
    
    // Get feeds by IDs
    @Query("SELECT * FROM reading_feed WHERE id IN (:feedIds)")
    suspend fun getFeedsByIds(feedIds: List<Long>): List<ReadingFeed>
    
    // Added method for RepeatedSessionAdapter
    @Query("SELECT * FROM reading_feed WHERE id = :feedId")
    suspend fun getReadingFeedById(feedId: Long): ReadingFeed?

    @Delete
    suspend fun deleteFeed(feed: ReadingFeed) : Int // Delete ReadingFeed entity

    @Query("DELETE FROM reading_feed WHERE id = :feedId")
    suspend fun deleteFeedById(feedId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedSourceCrossRef(ref: ReadingFeedSourceCrossRef)

    @Delete
    suspend fun deleteFeedSourceCrossRef(ref: ReadingFeedSourceCrossRef): Int // Delete cross-ref entity

    @Query("DELETE FROM reading_feed_source WHERE feedId = :feedId AND sourceId = :sourceId")
    suspend fun deleteFeedSourceCrossRefByIds(feedId: Long, sourceId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedKeywordRuleCrossRef(ref: ReadingFeedKeywordRuleCrossRef)

    @Delete
    suspend fun deleteFeedKeywordRuleCrossRef(ref: ReadingFeedKeywordRuleCrossRef): Int // Delete cross-ref entity

    @Query("DELETE FROM reading_feed_keyword_rule WHERE feedId = :feedId AND ruleId = :ruleId")
    suspend fun deleteFeedKeywordRuleCrossRefByIds(feedId: Long, ruleId: Long): Int

    /**
     * Get all ReadingFeedSourceCrossRef rows for a specific feedId
     */
    @Query("SELECT * FROM reading_feed_source WHERE feedId = :feedId")
    suspend fun getSourceIdsForFeed(feedId: Long): List<ReadingFeedSourceCrossRef>
    
    /**
     * Get all ReadingFeedKeywordRuleCrossRef rows for a specific feedId
     */
    @Query("SELECT * FROM reading_feed_keyword_rule WHERE feedId = :feedId")
    suspend fun getKeywordRuleIdsForFeed(feedId: Long): List<ReadingFeedKeywordRuleCrossRef>
    
    /**
     * Get all ReadingFeedKeywordRuleCrossRef rows for a specific ruleId
     */
    @Query("SELECT * FROM reading_feed_keyword_rule WHERE ruleId = :ruleId")
    suspend fun getFeedIdsForKeywordRule(ruleId: Long): List<ReadingFeedKeywordRuleCrossRef>
    
    /**
     * Get all keyword rules for a feed
     */
    @Query("SELECT kr.* FROM keyword_rule kr " +
           "INNER JOIN reading_feed_keyword_rule rfkr ON kr.id = rfkr.ruleId " +
           "WHERE rfkr.feedId = :feedId")
    suspend fun getKeywordRulesForFeed(feedId: Long): List<KeywordRule>
    
    /**
     * Delete all keyword rule associations for a feed
     */
    @Query("DELETE FROM reading_feed_keyword_rule WHERE feedId = :feedId")
    suspend fun deleteAllKeywordRuleAssociationsForFeed(feedId: Long): Int
    
    /**
     * Delete all keyword rule associations for a rule
     */
    @Query("DELETE FROM reading_feed_keyword_rule WHERE ruleId = :ruleId")
    suspend fun deleteAllFeedAssociationsForKeywordRule(ruleId: Long): Int
    
    /**
     * Insert AI rule cross reference
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedAiRuleCrossRef(ref: ReadingFeedAiRuleCrossRef)
    
    /**
     * Delete AI rule cross reference
     */
    @Delete
    suspend fun deleteFeedAiRuleCrossRef(ref: ReadingFeedAiRuleCrossRef): Int
    
    /**
     * Delete AI rule cross reference by IDs
     */
    @Query("DELETE FROM reading_feed_ai_rule WHERE feedId = :feedId AND ruleId = :ruleId")
    suspend fun deleteFeedAiRuleCrossRefByIds(feedId: Long, ruleId: Long): Int
    
    /**
     * Get all AI rules for a feed
     */
    @Query("SELECT ar.* FROM ai_rule ar " +
           "INNER JOIN reading_feed_ai_rule rfar ON ar.id = rfar.ruleId " +
           "WHERE rfar.feedId = :feedId")
    suspend fun getAiRulesForFeed(feedId: Long): List<AiRule>
    
    /**
     * Get all AI rule cross references for a feed
     */
    @Query("SELECT * FROM reading_feed_ai_rule WHERE feedId = :feedId")
    suspend fun getAiRuleRefsForFeed(feedId: Long): List<ReadingFeedAiRuleCrossRef>
    
    /**
     * Get whitelist AI rule for a feed
     */
    @Query("SELECT ar.* FROM ai_rule ar " +
           "INNER JOIN reading_feed_ai_rule rfar ON ar.id = rfar.ruleId " +
           "WHERE rfar.feedId = :feedId AND rfar.isWhitelist = 1 LIMIT 1")
    suspend fun getWhitelistAiRuleForFeed(feedId: Long): AiRule?
    
    /**
     * Get blacklist AI rule for a feed
     */
    @Query("SELECT ar.* FROM ai_rule ar " +
           "INNER JOIN reading_feed_ai_rule rfar ON ar.id = rfar.ruleId " +
           "WHERE rfar.feedId = :feedId AND rfar.isWhitelist = 0 LIMIT 1")
    suspend fun getBlacklistAiRuleForFeed(feedId: Long): AiRule?
    
    /**
     * Delete all AI rule associations for a feed
     */
    @Query("DELETE FROM reading_feed_ai_rule WHERE feedId = :feedId")
    suspend fun deleteAllAiRuleAssociationsForFeed(feedId: Long): Int
    
    /**
     * Delete all AI rule associations for a rule
     */
    @Query("DELETE FROM reading_feed_ai_rule WHERE ruleId = :ruleId")
    suspend fun deleteAllFeedAssociationsForAiRule(ruleId: Long): Int
}
// CodeCleaner_End_628d9479-edb0-4631-bd59-edfe1fa3669b
