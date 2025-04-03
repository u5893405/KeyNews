package com.example.keynews.data.db

// CodeCleaner_Start_a054ef99-1256-4e44-abb0-3cc82aa635d0
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import com.example.keynews.data.model.NewsArticle

@Dao
interface NewsArticleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<NewsArticle>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: NewsArticle)

    @Query("SELECT * FROM news_article WHERE sourceId = :sourceId ORDER BY publishDateUtc DESC")
    suspend fun getArticlesBySource(sourceId: Long): List<NewsArticle>

    @Query("SELECT * FROM news_article ORDER BY publishDateUtc DESC")
    suspend fun getAllArticles(): List<NewsArticle>

    @Update
    suspend fun update(article: NewsArticle): Int

    @Query("UPDATE news_article SET isRead = :readStatus WHERE link = :articleLink")
    suspend fun markRead(articleLink: String, readStatus: Boolean): Int

    @Delete
    suspend fun delete(article: NewsArticle): Int // Delete by entity

    @Query("DELETE FROM news_article WHERE link = :articleLink")
    suspend fun deleteByLink(articleLink: String): Int

    @Query("SELECT * FROM news_article WHERE sourceId IN (:sourceIds) ORDER BY publishDateUtc DESC")
    suspend fun getArticlesBySourceIds(sourceIds: List<Long>): List<NewsArticle>
    
    @Query("SELECT * FROM news_article WHERE sourceId IN (:sourceIds) AND isRead = 0 ORDER BY publishDateUtc DESC")
    suspend fun getUnreadArticlesBySourceIds(sourceIds: List<Long>): List<NewsArticle>
    
    @Query("SELECT * FROM news_article WHERE link = :articleLink LIMIT 1")
    suspend fun getArticleByLink(articleLink: String): NewsArticle?
    
    /**
     * Gets the links of all articles that have been marked as read
     */
    @Query("SELECT link FROM news_article WHERE isRead = 1")
    suspend fun getReadArticleLinks(): List<String>
    
    /**
     * Marks an article as read by its link
     */
    @Query("UPDATE news_article SET isRead = 1 WHERE link = :link")
    suspend fun markArticleReadByLink(link: String): Int
    
    /**
     * Marks all articles as unread
     */
    @Query("UPDATE news_article SET isRead = 0")
    suspend fun markAllArticlesUnread(): Int
    
    /**
     * Marks all articles as read
     */
    @Query("UPDATE news_article SET isRead = 1")
    suspend fun markAllArticlesRead(): Int
    
    /**
     * Marks all articles from specific source IDs as read
     */
    @Query("UPDATE news_article SET isRead = 1 WHERE sourceId IN (:sourceIds)")
    suspend fun markAllArticlesReadBySourceIds(sourceIds: List<Long>): Int
    
    /**
     * Marks all articles from specific source IDs as unread
     */
    @Query("UPDATE news_article SET isRead = 0 WHERE sourceId IN (:sourceIds)")
    suspend fun markAllArticlesUnreadBySourceIds(sourceIds: List<Long>): Int
    
    /**
     * Marks all articles from specific source IDs published before a certain time as read
     */
    @Query("UPDATE news_article SET isRead = 1 WHERE sourceId IN (:sourceIds) AND publishDateUtc < :timestampUtc")
    suspend fun markArticlesBeforeTimeReadBySourceIds(sourceIds: List<Long>, timestampUtc: Long): Int
    
    /**
     * Marks all articles from specific source IDs published before a certain time as unread
     */
    @Query("UPDATE news_article SET isRead = 0 WHERE sourceId IN (:sourceIds) AND publishDateUtc < :timestampUtc")
    suspend fun markArticlesBeforeTimeUnreadBySourceIds(sourceIds: List<Long>, timestampUtc: Long): Int
    
    /**
     * Marks an article as read later by its link
     */
    @Query("UPDATE news_article SET isReadLater = :readLaterStatus, readLaterFeedId = :feedId WHERE link = :link")
    suspend fun markReadLater(link: String, readLaterStatus: Boolean, feedId: Long?): Int
    
    /**
     * Gets all articles marked as read later
     */
    @Query("SELECT * FROM news_article WHERE isReadLater = 1 ORDER BY publishDateUtc DESC")
    suspend fun getReadLaterArticles(): List<NewsArticle>
    
    /**
     * Gets all unread articles marked as read later
     */
    @Query("SELECT * FROM news_article WHERE isReadLater = 1 AND isRead = 0 ORDER BY publishDateUtc DESC")
    suspend fun getUnreadReadLaterArticles(): List<NewsArticle>
    
    /**
     * Gets read later articles by originating feed ID
     */
    @Query("SELECT * FROM news_article WHERE isReadLater = 1 AND readLaterFeedId = :feedId ORDER BY publishDateUtc DESC")
    suspend fun getReadLaterArticlesByFeed(feedId: Long): List<NewsArticle>
    
    /**
     * Gets read later articles by source ID
     */
    @Query("SELECT * FROM news_article WHERE isReadLater = 1 AND sourceId = :sourceId ORDER BY publishDateUtc DESC")
    suspend fun getReadLaterArticlesBySource(sourceId: Long): List<NewsArticle>
    
    /**
     * Gets unread read later articles by source ID
     */
    @Query("SELECT * FROM news_article WHERE isReadLater = 1 AND isRead = 0 AND sourceId = :sourceId ORDER BY publishDateUtc DESC")
    suspend fun getUnreadReadLaterArticlesBySource(sourceId: Long): List<NewsArticle>
}
// CodeCleaner_End_a054ef99-1256-4e44-abb0-3cc82aa635d0
