package com.example.keynews.data.db

// CodeCleaner_Start_5ce7f314-1800-4610-897c-cc41582ad8c6
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.keynews.data.model.*

@Database(
    entities = [
        NewsSource::class,
        NewsArticle::class,
        KeywordRule::class,
        KeywordItem::class,
        ReadingFeed::class,
        ReadingFeedSourceCrossRef::class,
        ReadingFeedKeywordRuleCrossRef::class,
        ScheduledReading::class,
        RepeatedSession::class,
        RepeatedSessionRule::class,
        AiRule::class,
        ReadingFeedAiRuleCrossRef::class,
        AiFilterResult::class
    ],
    version = 10, // Updated for Read Later functionality
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun newsSourceDao(): NewsSourceDao
    abstract fun newsArticleDao(): NewsArticleDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun readingFeedDao(): ReadingFeedDao
    abstract fun scheduledReadingDao(): ScheduledReadingDao
    abstract fun repeatedSessionDao(): RepeatedSessionDao
    abstract fun aiRuleDao(): AiRuleDao
    abstract fun aiFilterResultDao(): AiFilterResultDao
}
// CodeCleaner_End_5ce7f314-1800-4610-897c-cc41582ad8c6
