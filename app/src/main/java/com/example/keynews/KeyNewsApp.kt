package com.example.keynews

// CodeCleaner_Start_ca11a270-ef64-4e7b-96eb-ea7b2feba01d
import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.keynews.data.db.AppDatabase
import com.example.keynews.data.DataManager
import kotlinx.coroutines.launch
import com.example.keynews.data.model.ReadingFeed

// Track last refresh timestamp
class FeedRefreshTracker {
    companion object {
        private val lastRefreshTimes = mutableMapOf<Long, Long>()
        
        fun getLastRefreshTime(feedId: Long): Long {
            return lastRefreshTimes[feedId] ?: 0L
        }
        
        fun setLastRefreshTime(feedId: Long, time: Long = System.currentTimeMillis()) {
            lastRefreshTimes[feedId] = time
        }
        
        // Check if refresh is needed based on elapsed time
        fun shouldRefresh(feedId: Long, intervalMinutes: Int): Boolean {
            val lastRefresh = getLastRefreshTime(feedId)
            val currentTime = System.currentTimeMillis()
            val elapsedMinutes = (currentTime - lastRefresh) / (1000 * 60)
            return elapsedMinutes >= intervalMinutes
        }
    }
}

class KeyNewsApp : Application() {

    lateinit var database: AppDatabase
        private set
        
    companion object {
        /**
         * Get the database instance from any context
         */
        fun getDatabase(context: android.content.Context): AppDatabase {
            return (context.applicationContext as KeyNewsApp).database
        }
    }

    lateinit var dataManager: DataManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Migration from version 9 to 10: Add isReadLater and readLaterFeedId columns to news_article table
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add isReadLater column with default value of 0 (false)
                database.execSQL("ALTER TABLE news_article ADD COLUMN isReadLater INTEGER NOT NULL DEFAULT 0")
                // Add readLaterFeedId column as nullable
                database.execSQL("ALTER TABLE news_article ADD COLUMN readLaterFeedId INTEGER DEFAULT NULL")
            }
        }

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "keynews_db"
        )
        .addMigrations(MIGRATION_9_10)
        .build()

        dataManager = DataManager(database)

        // Initialize default feed if none exists
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val feedDao = dataManager.database.readingFeedDao()
            val existingFeeds = feedDao.getAllFeeds()
            if (existingFeeds.isEmpty()) {
                val defaultFeed = ReadingFeed(name = "Default feed", isDefault = true)
                feedDao.insertReadingFeed(defaultFeed)
            }
        }
    }
}
// CodeCleaner_End_ca11a270-ef64-4e7b-96eb-ea7b2feba01d

