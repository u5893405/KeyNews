package com.example.keynews.util

// CodeCleaner_Start_0e33a79a-004b-4f52-b84f-fac5418fc79d
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.keynews.data.DataManager
import com.example.keynews.data.model.*
import com.example.keynews.ui.settings.SettingsFragment
import com.example.keynews.ui.settings.tts.TtsSettingsHelper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Date
// CodeCleaner_End_0e33a79a-004b-4f52-b84f-fac5418fc79d


/**
 * Manages backup and restore operations for the app's data
 */
// CodeCleaner_Start_572318bf-2a05-4ed7-b74e-7e7925c316b4
class BackupManager(
// CodeCleaner_End_572318bf-2a05-4ed7-b74e-7e7925c316b4

// CodeCleaner_Start_8bbb3e3c-9dde-4892-ab6f-85609cfeb8d0
    private val context: Context,
    private val dataManager: DataManager
) {
    companion object {
        private const val TAG = "BackupManager"
        
        // Categories of data that can be backed up
        const val CATEGORY_SOURCES = "sources"
        const val CATEGORY_READING_FEEDS = "reading_feeds"
        const val CATEGORY_KEYWORD_RULES = "keyword_rules"
        const val CATEGORY_REPEATED_SESSIONS = "repeated_sessions"
        const val CATEGORY_SETTINGS = "settings"
        const val CATEGORY_READ_MARKS = "read_marks"
        const val CATEGORY_AI_FILTER_CACHE = "ai_filter_cache"
        const val CATEGORY_AI_RULES = "ai_rules"
        
        // All available categories
        val ALL_CATEGORIES = listOf(
            CATEGORY_SOURCES,
            CATEGORY_READING_FEEDS,
            CATEGORY_KEYWORD_RULES,
            CATEGORY_REPEATED_SESSIONS,
            CATEGORY_SETTINGS,
            CATEGORY_READ_MARKS,
            CATEGORY_AI_FILTER_CACHE,
            CATEGORY_AI_RULES
        )
    }

    /**
     * Parse a string containing a number by trying to handle different formats
     */
    private fun parseNumber(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }
    
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val prefs = context.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
// CodeCleaner_End_8bbb3e3c-9dde-4892-ab6f-85609cfeb8d0

    
    /**
     * Creates a backup of the selected categories and writes it to the provided URI
     */
// CodeCleaner_Start_4e267ffb-37b2-42f0-abd5-b6753a19de1e
    suspend fun createBackup(
        outputUri: Uri,
        selectedCategories: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupJson = JSONObject()
            
            // Add metadata
            val metadata = JSONObject().apply {
                put("appName", "KeyNews")
                put("backupDate", Date().time)
                put("backupVersion", 1)
                put("categories", selectedCategories)
            }
            backupJson.put("metadata", metadata)
            
            // Add each selected category
            if (CATEGORY_SOURCES in selectedCategories) {
                val sources = dataManager.getAllSources()
                backupJson.put(CATEGORY_SOURCES, JSONTokener(gson.toJson(sources)).nextValue())
            }
            
            if (CATEGORY_READING_FEEDS in selectedCategories) {
                val readingFeedData = exportReadingFeeds()
                backupJson.put(CATEGORY_READING_FEEDS, JSONTokener(gson.toJson(readingFeedData)).nextValue())
            }
            
            if (CATEGORY_KEYWORD_RULES in selectedCategories) {
                val keywordRulesData = exportKeywordRules()
                backupJson.put(CATEGORY_KEYWORD_RULES, JSONTokener(gson.toJson(keywordRulesData)).nextValue())
            }
            
            if (CATEGORY_REPEATED_SESSIONS in selectedCategories) {
                val repeatedSessionsData = exportRepeatedSessions()
                backupJson.put(CATEGORY_REPEATED_SESSIONS, JSONTokener(gson.toJson(repeatedSessionsData)).nextValue())
            }
            
            if (CATEGORY_SETTINGS in selectedCategories) {
                val settingsData = exportSettings()
                backupJson.put(CATEGORY_SETTINGS, JSONTokener(gson.toJson(settingsData)).nextValue())
            }
            
            if (CATEGORY_READ_MARKS in selectedCategories) {
                val readMarksData = exportReadMarks()
                backupJson.put(CATEGORY_READ_MARKS, JSONTokener(gson.toJson(readMarksData)).nextValue())
            }
            
            if (CATEGORY_AI_FILTER_CACHE in selectedCategories) {
                val aiFilterCacheData = exportAiFilterCache()
                backupJson.put(CATEGORY_AI_FILTER_CACHE, JSONTokener(gson.toJson(aiFilterCacheData)).nextValue())
            }
            
            if (CATEGORY_AI_RULES in selectedCategories) {
                val aiRulesData = exportAiRules()
                backupJson.put(CATEGORY_AI_RULES, JSONTokener(gson.toJson(aiRulesData)).nextValue())
            }
            
            // Write to file
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                outputStream.write(backupJson.toString(2).toByteArray())
            } ?: throw Exception("Could not open output stream")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating backup", e)
            Result.failure(e)
        }
    }
// CodeCleaner_End_4e267ffb-37b2-42f0-abd5-b6753a19de1e

    
    /**
     * Parses a backup file and returns a map of categories to their data
     */
// CodeCleaner_Start_46855655-e01f-4444-b0fe-912236dd0495
    suspend fun parseBackupFile(uri: Uri): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            // Read the backup file
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Could not open input stream"))
            
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            
            val backupJson = JSONObject(jsonString)
            
            // Validate metadata
            val metadata = backupJson.optJSONObject("metadata")
                ?: return@withContext Result.failure(Exception("Invalid backup file: missing metadata"))
            
            if (metadata.optString("appName") != "KeyNews") {
                return@withContext Result.failure(Exception("Invalid backup file: not a KeyNews backup"))
            }
            
            // Get categories from the backup
            val categoryData = mutableMapOf<String, Any>()
            
            if (backupJson.has(CATEGORY_SOURCES)) {
                try {
                    val sourcesJson = backupJson.getJSONArray(CATEGORY_SOURCES).toString()
                    val sourcesList = gson.fromJson(sourcesJson, Array<NewsSource>::class.java).toList()
                    categoryData[CATEGORY_SOURCES] = sourcesList
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing sources", e)
                }
            }
            
            if (backupJson.has(CATEGORY_READING_FEEDS)) {
                try {
                    val feedsJson = backupJson.getJSONObject(CATEGORY_READING_FEEDS).toString()
                    val feedsData = gson.fromJson(feedsJson, ReadingFeedsBackupData::class.java)
                    categoryData[CATEGORY_READING_FEEDS] = feedsData
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing reading feeds", e)
                }
            }
            
            if (backupJson.has(CATEGORY_KEYWORD_RULES)) {
                try {
                    val rulesJson = backupJson.getJSONObject(CATEGORY_KEYWORD_RULES).toString()
                    val rulesData = gson.fromJson(rulesJson, KeywordRulesBackupData::class.java)
                    categoryData[CATEGORY_KEYWORD_RULES] = rulesData
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing keyword rules", e)
                }
            }
            
            if (backupJson.has(CATEGORY_REPEATED_SESSIONS)) {
                try {
                    val sessionsJson = backupJson.getJSONObject(CATEGORY_REPEATED_SESSIONS).toString()
                    val sessionsData = gson.fromJson(sessionsJson, RepeatedSessionsBackupData::class.java)
                    categoryData[CATEGORY_REPEATED_SESSIONS] = sessionsData
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing repeated sessions", e)
                }
            }
            
            if (backupJson.has(CATEGORY_SETTINGS)) {
                try {
                    val settingsJson = backupJson.getJSONObject(CATEGORY_SETTINGS).toString()
                    val settingsData = gson.fromJson(settingsJson, AppSettingsBackupData::class.java)
                    categoryData[CATEGORY_SETTINGS] = settingsData
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing settings", e)
                }
            }
            
            if (backupJson.has(CATEGORY_READ_MARKS)) {
                try {
                    val readMarksJson = backupJson.getJSONObject(CATEGORY_READ_MARKS).toString()
                    val readMarksData = gson.fromJson(readMarksJson, ReadMarksBackupData::class.java)
                    categoryData[CATEGORY_READ_MARKS] = readMarksData
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing read marks", e)
                }
            }
            
            if (backupJson.has(CATEGORY_AI_FILTER_CACHE)) {
                try {
                    val cacheJson = backupJson.getJSONObject(CATEGORY_AI_FILTER_CACHE).toString()
                    val cacheData = gson.fromJson(cacheJson, AiFilterCacheBackupData::class.java)
                    categoryData[CATEGORY_AI_FILTER_CACHE] = cacheData
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing AI filter cache", e)
                }
            }
            
            if (backupJson.has(CATEGORY_AI_RULES)) {
                try {
                    val rulesJson = backupJson.getJSONObject(CATEGORY_AI_RULES).toString()
                    val rulesData = gson.fromJson(rulesJson, AiRulesBackupData::class.java)
                    categoryData[CATEGORY_AI_RULES] = rulesData
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing AI rules", e)
                }
            }
            
            Result.success(categoryData)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing backup file", e)
            Result.failure(e)
        }
    }
// CodeCleaner_End_46855655-e01f-4444-b0fe-912236dd0495

    
    /**
     * Restores selected categories from the provided backup data
     */
// CodeCleaner_Start_ff5a5234-2a05-49f7-8d44-f14c3cab17e2
    suspend fun restoreBackup(
        backupData: Map<String, Any>,
        selectedCategories: List<String>,
        overwriteExisting: Map<String, Boolean>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Restore data in specific order to handle dependencies correctly
            if (CATEGORY_SOURCES in selectedCategories && backupData.containsKey(CATEGORY_SOURCES)) {
                val sources = backupData[CATEGORY_SOURCES] as List<NewsSource>
                restoreSources(sources, overwriteExisting[CATEGORY_SOURCES] == true)
            }
            
            if (CATEGORY_KEYWORD_RULES in selectedCategories && backupData.containsKey(CATEGORY_KEYWORD_RULES)) {
                val keywordRulesData = backupData[CATEGORY_KEYWORD_RULES] as KeywordRulesBackupData
                restoreKeywordRules(keywordRulesData, overwriteExisting[CATEGORY_KEYWORD_RULES] == true)
            }
            
            if (CATEGORY_READING_FEEDS in selectedCategories && backupData.containsKey(CATEGORY_READING_FEEDS)) {
                val feedsData = backupData[CATEGORY_READING_FEEDS] as ReadingFeedsBackupData
                restoreReadingFeeds(feedsData, overwriteExisting[CATEGORY_READING_FEEDS] == true)
            }
            
            if (CATEGORY_REPEATED_SESSIONS in selectedCategories && backupData.containsKey(CATEGORY_REPEATED_SESSIONS)) {
                val sessionsData = backupData[CATEGORY_REPEATED_SESSIONS] as RepeatedSessionsBackupData
                restoreRepeatedSessions(sessionsData, overwriteExisting[CATEGORY_REPEATED_SESSIONS] == true)
            }
            
            if (CATEGORY_SETTINGS in selectedCategories && backupData.containsKey(CATEGORY_SETTINGS)) {
                val settingsData = backupData[CATEGORY_SETTINGS] as AppSettingsBackupData
                restoreSettings(settingsData, overwriteExisting[CATEGORY_SETTINGS] == true)
            }
            
            if (CATEGORY_READ_MARKS in selectedCategories && backupData.containsKey(CATEGORY_READ_MARKS)) {
                val readMarksData = backupData[CATEGORY_READ_MARKS] as ReadMarksBackupData
                restoreReadMarks(readMarksData, overwriteExisting[CATEGORY_READ_MARKS] == true)
            }
            
            if (CATEGORY_AI_FILTER_CACHE in selectedCategories && backupData.containsKey(CATEGORY_AI_FILTER_CACHE)) {
                val aiFilterCacheData = backupData[CATEGORY_AI_FILTER_CACHE] as AiFilterCacheBackupData
                restoreAiFilterCache(aiFilterCacheData, overwriteExisting[CATEGORY_AI_FILTER_CACHE] == true)
            }
            
            if (CATEGORY_AI_RULES in selectedCategories && backupData.containsKey(CATEGORY_AI_RULES)) {
                val aiRulesData = backupData[CATEGORY_AI_RULES] as AiRulesBackupData
                restoreAiRules(aiRulesData, overwriteExisting[CATEGORY_AI_RULES] == true)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring backup", e)
            Result.failure(e)
        }
    }
// CodeCleaner_End_ff5a5234-2a05-49f7-8d44-f14c3cab17e2

    
    // Private helper methods for exporting specific data types
    
// CodeCleaner_Start_e8942f95-ac61-4229-afb4-5738d8888e2b
    private suspend fun exportReadingFeeds(): ReadingFeedsBackupData = withContext(Dispatchers.IO) {
        val feedDao = dataManager.database.readingFeedDao()
        val feeds = feedDao.getAllFeeds()
        
        val feedSourceRefs = mutableListOf<ReadingFeedSourceCrossRef>()
        val feedKeywordRuleRefs = mutableListOf<ReadingFeedKeywordRuleCrossRef>()
        val feedAiRuleRefs = mutableListOf<ReadingFeedAiRuleCrossRef>()
        
        feeds.forEach { feed ->
            feedSourceRefs.addAll(feedDao.getSourceRefsForFeed(feed.id))
            feedKeywordRuleRefs.addAll(feedDao.getKeywordRuleRefsForFeed(feed.id))
            feedAiRuleRefs.addAll(feedDao.getAiRuleRefsForFeed(feed.id))
        }
        
        ReadingFeedsBackupData(
            feeds = feeds,
            feedSourceRefs = feedSourceRefs,
            feedKeywordRuleRefs = feedKeywordRuleRefs,
            feedAiRuleRefs = feedAiRuleRefs
        )
    }
// CodeCleaner_End_e8942f95-ac61-4229-afb4-5738d8888e2b

    
// CodeCleaner_Start_95b4f8c8-d873-41ba-8923-3bb5238db4d6
    private suspend fun exportKeywordRules(): KeywordRulesBackupData = withContext(Dispatchers.IO) {
        val keywordRuleDao = dataManager.database.keywordRuleDao()
        val rules: List<KeywordRule> = keywordRuleDao.getAllRules()
        
        val keywords = mutableListOf<KeywordItem>()
        rules.forEach { rule ->
            val ruleKeywords: List<KeywordItem> = keywordRuleDao.getKeywordsForRule(rule.id)
            keywords.addAll(ruleKeywords)
        }
        
        KeywordRulesBackupData(
            rules = rules,
            keywords = keywords
        )
    }
// CodeCleaner_End_95b4f8c8-d873-41ba-8923-3bb5238db4d6

    
// CodeCleaner_Start_1e9998eb-952d-4b27-b397-a25340100f36
    private suspend fun exportRepeatedSessions(): RepeatedSessionsBackupData = withContext(Dispatchers.IO) {
        val repeatedSessionDao = dataManager.database.repeatedSessionDao()
        val sessionsWithRules = repeatedSessionDao.getAllSessionsWithRules()
        
        val sessions = mutableListOf<RepeatedSession>()
        val rules = mutableListOf<RepeatedSessionRule>()
        
        sessionsWithRules.forEach { sessionWithRules ->
            sessions.add(sessionWithRules.session)
            rules.addAll(sessionWithRules.rules)
        }
        
        RepeatedSessionsBackupData(
            sessions = sessions,
            rules = rules
        )
    }
// CodeCleaner_End_1e9998eb-952d-4b27-b397-a25340100f36

    
// CodeCleaner_Start_b8bc2dfc-64b3-42b1-84f6-32e8ea3880a4
    private suspend fun exportSettings(): AppSettingsBackupData = withContext(Dispatchers.IO) {
        // Get all preference values
        val allPrefs = mutableMapOf<String, Any?>()
        prefs.all.forEach { (key, value) ->
            // Skip TTS preferences as they're handled specially
            if (key != "tts_lang_voice_preferences") {
                allPrefs[key] = value
            }
        }
        
        // Get TTS preferences
        val ttsPreferences = TtsPreferenceManager.loadPreferences(context)
        
        AppSettingsBackupData(
            sharedPreferences = allPrefs,
            ttsPreferences = ttsPreferences
        )
    }
// CodeCleaner_End_b8bc2dfc-64b3-42b1-84f6-32e8ea3880a4

    
// CodeCleaner_Start_8dfa3bd7-4734-44e3-b47f-bfc67f6f77aa
    private suspend fun exportReadMarks(): ReadMarksBackupData = withContext(Dispatchers.IO) {
        val articleDao = dataManager.database.newsArticleDao()
        val readArticles = articleDao.getReadArticleLinks()
        
        ReadMarksBackupData(
            readArticleLinks = readArticles
        )
    }
    
    /**
     * Export AI filter cache data for backup
     */
    private suspend fun exportAiFilterCache(): AiFilterCacheBackupData = withContext(Dispatchers.IO) {
        val aiFilterResultDao = dataManager.database.aiFilterResultDao()
        val allFilterResults = aiFilterResultDao.getAll()
        
        Log.d(TAG, "Exporting ${allFilterResults.size} AI filter cache entries")
        
        AiFilterCacheBackupData(
            filterResults = allFilterResults
        )
    }
    
    /**
     * Export AI rules data for backup
     */
    private suspend fun exportAiRules(): AiRulesBackupData = withContext(Dispatchers.IO) {
        val aiRuleDao = dataManager.database.aiRuleDao()
        val allRules = aiRuleDao.getAllRules()
        
        Log.d(TAG, "Exporting ${allRules.size} AI rules")
        
        AiRulesBackupData(
            rules = allRules
        )
    }
// CodeCleaner_End_8dfa3bd7-4734-44e3-b47f-bfc67f6f77aa

    
    // Private helper methods for restoring specific data types
    
// CodeCleaner_Start_cfc6528d-be98-4390-8682-7f6d1dfc3678
    private suspend fun restoreSources(
        sources: List<NewsSource>,
        overwriteExisting: Boolean
    ) = withContext(Dispatchers.IO) {
        val sourceDao = dataManager.database.newsSourceDao()
        
        if (overwriteExisting) {
            // Delete all existing sources and insert new ones
            sourceDao.deleteAllSources()
            sources.forEach { source ->
                dataManager.saveNewsSource(source.copy(id = 0)) // Reset ID for insertion
            }
        } else {
            // Preserve existing sources, just add new ones
            val existingSources = sourceDao.getAllSources()
            val existingUrls = existingSources.map { it.rssUrl }
            
            sources.forEach { source ->
                if (source.rssUrl !in existingUrls) {
                    dataManager.saveNewsSource(source.copy(id = 0)) // Reset ID for insertion
                }
            }
        }
    }
// CodeCleaner_End_cfc6528d-be98-4390-8682-7f6d1dfc3678

    
    /**
     * Creates a map of source URLs to their IDs in the current database
     */
// CodeCleaner_Start_7e787158-62bf-49ea-a216-77790c5774dc
    private suspend fun getSourceUrlToIdMap(): Map<String, Long> = withContext(Dispatchers.IO) {
        val sourceDao = dataManager.database.newsSourceDao()
        val existingSources = sourceDao.getAllSources()
        
        // Create a map of URL to ID
        existingSources.associate { it.rssUrl to it.id }
    }
// CodeCleaner_End_7e787158-62bf-49ea-a216-77790c5774dc

    
    /**
     * Creates a map of rule names to their IDs in the current database
     */
// CodeCleaner_Start_09ad7a79-8fa0-45c7-9343-1554b6848f6c
    private suspend fun getRuleNameToIdMap(): Map<String, Long> = withContext(Dispatchers.IO) {
        val ruleDao = dataManager.database.keywordRuleDao()
        val existingRules = ruleDao.getAllRules()
        
        // Create a map of rule name to ID
        existingRules.associate { it.name to it.id }
    }
// CodeCleaner_End_09ad7a79-8fa0-45c7-9343-1554b6848f6c

    
    /**
     * Creates a map of feed names to their IDs in the current database
     */
// CodeCleaner_Start_72e16d98-ebce-4fd3-b516-25685f5da42e
    private suspend fun getFeedNameToIdMap(): Map<String, Long> = withContext(Dispatchers.IO) {
        val feedDao = dataManager.database.readingFeedDao()
        val existingFeeds = feedDao.getAllFeeds()
        
        // Create a map of feed name to ID
        existingFeeds.associate { it.name to it.id }
    }
// CodeCleaner_End_72e16d98-ebce-4fd3-b516-25685f5da42e

    
// CodeCleaner_Start_56657f37-8c23-4399-9ee7-b4cba3d9b0ac
    private suspend fun restoreKeywordRules(
        keywordRulesData: KeywordRulesBackupData,
        overwriteExisting: Boolean
    ) = withContext(Dispatchers.IO) {
        val keywordRuleDao = dataManager.database.keywordRuleDao()
        
        if (overwriteExisting) {
            // Delete all existing keyword rules and insert new ones
            keywordRuleDao.deleteAllRules()
            
            // Create a mapping of old rule IDs to new rule IDs
            val ruleIdMap = mutableMapOf<Long, Long>()
            
            // Insert rules first and keep track of new IDs
            keywordRulesData.rules.forEach { rule ->
                val oldId = rule.id
                val newId = keywordRuleDao.insertRule(rule.copy(id = 0))
                ruleIdMap[oldId] = newId
                Log.d(TAG, "Rule ID mapping: $oldId -> $newId")
            }
            
            // Insert keywords with updated rule IDs
            keywordRulesData.keywords.forEach { keyword ->
                val newRuleId = ruleIdMap[keyword.ruleId]
                if (newRuleId != null) {
                    keywordRuleDao.insertKeyword(keyword.copy(id = 0, ruleId = newRuleId))
                } else {
                    Log.w(TAG, "Could not find mapping for rule ID: ${keyword.ruleId}")
                }
            }
        } else {
            // Preserve existing rules, just add new ones
            // Since rules don't have a unique constraint other than ID, we'll check by name
            val existingRules = keywordRuleDao.getAllRules()
            val existingRuleNames = existingRules.map { it.name }
            
            // Create a mapping of old rule IDs to new rule IDs for the rules we add
            val ruleIdMap = mutableMapOf<Long, Long>()
            
            // Create a mapping of rule names to their existing IDs
            val ruleNameToIdMap = existingRules.associate { it.name to it.id }
            
            // First, add existing rule IDs to the map (for rules that won't be added)
            keywordRulesData.rules.forEach { rule ->
                if (rule.name in existingRuleNames) {
                    // Map the old rule ID to the existing rule ID with the same name
                    val existingId = ruleNameToIdMap[rule.name]
                    if (existingId != null) {
                        ruleIdMap[rule.id] = existingId
                        Log.d(TAG, "Existing rule mapping: ${rule.id} -> $existingId (${rule.name})")
                    }
                }
            }
            
            // Insert rules that don't exist by name
            keywordRulesData.rules.forEach { rule ->
                if (rule.name !in existingRuleNames) {
                    val oldId = rule.id
                    val newId = keywordRuleDao.insertRule(rule.copy(id = 0))
                    ruleIdMap[oldId] = newId
                    Log.d(TAG, "New rule mapping: $oldId -> $newId (${rule.name})")
                }
            }
            
            // Insert keywords for the new rules with updated rule IDs
            keywordRulesData.keywords.forEach { keyword ->
                val newRuleId = ruleIdMap[keyword.ruleId]
                if (newRuleId != null) {
                    keywordRuleDao.insertKeyword(keyword.copy(id = 0, ruleId = newRuleId))
                } else {
                    Log.w(TAG, "Could not find mapping for rule ID: ${keyword.ruleId}")
                }
            }
        }
    }
// CodeCleaner_End_56657f37-8c23-4399-9ee7-b4cba3d9b0ac

    
// CodeCleaner_Start_c84f7fe2-f265-45e5-b90e-94da750b6774
    private suspend fun restoreReadingFeeds(
        feedsData: ReadingFeedsBackupData,
        overwriteExisting: Boolean
    ) = withContext(Dispatchers.IO) {
        val feedDao = dataManager.database.readingFeedDao()
        val sourceDao = dataManager.database.newsSourceDao()
        
        if (overwriteExisting) {
            // Delete all existing feeds and insert new ones
            feedDao.deleteAllFeeds()
            
            // Create a mapping of old feed IDs to new feed IDs
            val feedIdMap = mutableMapOf<Long, Long>()
            
            // Insert feeds first and keep track of new IDs
            feedsData.feeds.forEach { feed ->
                val oldId = feed.id
                val newId = feedDao.insertReadingFeed(feed.copy(id = 0))
                feedIdMap[oldId] = newId
                Log.d(TAG, "Feed ID mapping: $oldId -> $newId")
            }
            
            // Get existing sources by URL for proper reference mapping
            val sourceUrlToIdMap = getSourceUrlToIdMap()
            val backupSources = sourceDao.getAllSources()
            val backupSourceIdToUrlMap = backupSources.associate { it.id to it.rssUrl }
            
            // Insert feed-source cross-references with updated feed IDs and correctly mapped source IDs
            feedsData.feedSourceRefs.forEach { ref ->
                val newFeedId = feedIdMap[ref.feedId]
                if (newFeedId != null) {
                    // Get the source URL from the backup source ID
                    val sourceUrl = backupSourceIdToUrlMap[ref.sourceId]
                    if (sourceUrl != null) {
                        // Find the correct source ID in the current database
                        val currentSourceId = sourceUrlToIdMap[sourceUrl]
                        if (currentSourceId != null) {
                            feedDao.insertFeedSourceCrossRef(
                                ReadingFeedSourceCrossRef(newFeedId, currentSourceId)
                            )
                            Log.d(TAG, "Inserted feed-source ref: feed=$newFeedId, source=$currentSourceId (URL: $sourceUrl)")
                        } else {
                            Log.w(TAG, "Could not find current source ID for URL: $sourceUrl")
                        }
                    } else {
                        Log.w(TAG, "Could not find URL for source ID: ${ref.sourceId}")
                    }
                } else {
                    Log.w(TAG, "Could not find mapping for feed ID: ${ref.feedId}")
                }
            }
            
            // Get existing keyword rules by name for proper reference mapping
            val keywordRuleDao = dataManager.database.keywordRuleDao()
            val backupKeywordRules = keywordRuleDao.getAllRules()
            val backupRuleIdToNameMap = backupKeywordRules.associate { it.id to it.name }
            val ruleNameToIdMap = backupKeywordRules.associate { it.name to it.id }
            
            // Insert feed-AI rule cross-references for both new and existing feeds
            if (feedsData.feedAiRuleRefs != null && feedsData.feedAiRuleRefs.isNotEmpty()) {
                feedsData.feedAiRuleRefs.forEach { ref ->
                    val targetFeedId = feedIdMap[ref.feedId]
                    if (targetFeedId != null) {
                        // Get the current AI rule dao instance and mappings
                        val aiRuleDao = dataManager.database.aiRuleDao()
                        val backupAiRules = aiRuleDao.getAllRules()
                        val backupAiRuleIdToNameMap = backupAiRules.associate { it.id to it.name }
                        val aiRuleNameToIdMap = backupAiRules.associate { it.name to it.id }
                        
                        // Get the rule name from the backup rule ID using already defined mappings
                        val ruleName = backupAiRuleIdToNameMap[ref.ruleId]
                        if (ruleName != null) {
                            // Find the correct rule ID in the current database
                            val currentRuleId = aiRuleNameToIdMap[ruleName]
                            if (currentRuleId != null) {
                                // Check if this feed-AI rule association already exists
                                val existingRefs = feedDao.getAiRuleRefsForFeed(targetFeedId)
                                val alreadyExists = existingRefs.any { it.ruleId == currentRuleId && it.isWhitelist == ref.isWhitelist }
                                
                                if (!alreadyExists) {
                                    feedDao.insertFeedAiRuleCrossRef(
                                        ReadingFeedAiRuleCrossRef(targetFeedId, currentRuleId, ref.isWhitelist)
                                    )
                                    Log.d(TAG, "Inserted feed-AI rule ref: feed=$targetFeedId, rule=$currentRuleId (Name: $ruleName)")
                                } else {
                                    Log.d(TAG, "Feed-AI rule ref already exists: feed=$targetFeedId, rule=$currentRuleId")
                                }
                            } else {
                                Log.w(TAG, "Could not find current AI rule ID for name: $ruleName")
                            }
                        } else {
                            Log.w(TAG, "Could not find name for AI rule ID: ${ref.ruleId}")
                        }
                    } else {
                        Log.w(TAG, "Could not find mapping for feed ID: ${ref.feedId}")
                    }
                }
            }
            
            // Insert feed-AI rule cross-references with updated feed IDs and correctly mapped rule IDs
            if (feedsData.feedAiRuleRefs != null && feedsData.feedAiRuleRefs.isNotEmpty()) {
                feedsData.feedAiRuleRefs.forEach { ref ->
                    val newFeedId = feedIdMap[ref.feedId]
                    if (newFeedId != null) {
                        // Get the rule name from the backup rule ID
                        val aiRuleDao = dataManager.database.aiRuleDao()
                        val rule = aiRuleDao.getRuleById(ref.ruleId)
                        val ruleName = rule?.name
                        if (ruleName != null) {
                            // Find the correct rule ID in the current database
                            val matchingRule = aiRuleDao.getAllRules().firstOrNull { it.name == ruleName }
                            val currentRuleId = matchingRule?.id
                            if (currentRuleId != null) {
                                feedDao.insertFeedAiRuleCrossRef(
                                    ReadingFeedAiRuleCrossRef(newFeedId, currentRuleId, ref.isWhitelist)
                                )
                                Log.d(TAG, "Inserted feed-AI rule ref: feed=$newFeedId, rule=$currentRuleId (Name: $ruleName)")
                            } else {
                                Log.w(TAG, "Could not find current AI rule ID for name: $ruleName")
                            }
                        } else {
                            Log.w(TAG, "Could not find name for AI rule ID: ${ref.ruleId}")
                        }
                    } else {
                        Log.w(TAG, "Could not find mapping for feed ID: ${ref.feedId}")
                    }
                }
            }
            
            // Insert feed-keyword rule cross-references with updated feed IDs and correctly mapped rule IDs
            feedsData.feedKeywordRuleRefs.forEach { ref ->
                val newFeedId = feedIdMap[ref.feedId]
                if (newFeedId != null) {
                    // Get the rule name from the backup rule ID
                    val ruleName = backupRuleIdToNameMap[ref.ruleId]
                    if (ruleName != null) {
                        // Find the correct rule ID in the current database
                        val currentRuleId = ruleNameToIdMap[ruleName]
                        if (currentRuleId != null) {
                            feedDao.insertFeedKeywordRuleCrossRef(
                                ReadingFeedKeywordRuleCrossRef(newFeedId, currentRuleId)
                            )
                            Log.d(TAG, "Inserted feed-rule ref: feed=$newFeedId, rule=$currentRuleId (Name: $ruleName)")
                        } else {
                            Log.w(TAG, "Could not find current rule ID for name: $ruleName")
                        }
                    } else {
                        Log.w(TAG, "Could not find name for rule ID: ${ref.ruleId}")
                    }
                } else {
                    Log.w(TAG, "Could not find mapping for feed ID: ${ref.feedId}")
                }
            }
        } else {
            // Preserve existing feeds, just add new ones
            val existingFeeds = feedDao.getAllFeeds()
            val existingFeedNames = existingFeeds.map { it.name }
            
            // Create a mapping of feed names to their existing IDs
            val feedNameToIdMap = existingFeeds.associate { it.name to it.id }
            
            // Create a mapping of old feed IDs to new/existing feed IDs
            val feedIdMap = mutableMapOf<Long, Long>()
            
            // First, map existing feed IDs
            feedsData.feeds.forEach { feed ->
                if (feed.name in existingFeedNames) {
                    // Map old feed ID to existing feed ID with the same name
                    val existingId = feedNameToIdMap[feed.name]
                    if (existingId != null) {
                        feedIdMap[feed.id] = existingId
                        Log.d(TAG, "Existing feed mapping: ${feed.id} -> $existingId (${feed.name})")
                    }
                }
            }
            
            // Insert feeds that don't exist by name
            feedsData.feeds.forEach { feed ->
                if (feed.name !in existingFeedNames) {
                    val oldId = feed.id
                    val newId = feedDao.insertReadingFeed(feed.copy(id = 0))
                    feedIdMap[oldId] = newId
                    Log.d(TAG, "New feed mapping: $oldId -> $newId (${feed.name})")
                }
            }
            
            // Get existing sources by URL for proper reference mapping
            val sourceUrlToIdMap = getSourceUrlToIdMap()
            val backupSources = sourceDao.getAllSources()
            val backupSourceIdToUrlMap = backupSources.associate { it.id to it.rssUrl }
            
            // Insert feed-source cross-references for both new and existing feeds
            feedsData.feedSourceRefs.forEach { ref ->
                val targetFeedId = feedIdMap[ref.feedId]
                if (targetFeedId != null) {
                    // Get the source URL from the backup source ID
                    val sourceUrl = backupSourceIdToUrlMap[ref.sourceId]
                    if (sourceUrl != null) {
                        // Find the correct source ID in the current database
                        val currentSourceId = sourceUrlToIdMap[sourceUrl]
                        if (currentSourceId != null) {
                            // Check if this feed-source association already exists
                            val existingRefs = feedDao.getSourceRefsForFeed(targetFeedId)
                            val alreadyExists = existingRefs.any { it.sourceId == currentSourceId }
                            
                            if (!alreadyExists) {
                                feedDao.insertFeedSourceCrossRef(
                                    ReadingFeedSourceCrossRef(targetFeedId, currentSourceId)
                                )
                                Log.d(TAG, "Inserted feed-source ref: feed=$targetFeedId, source=$currentSourceId (URL: $sourceUrl)")
                            } else {
                                Log.d(TAG, "Feed-source ref already exists: feed=$targetFeedId, source=$currentSourceId")
                            }
                        } else {
                            Log.w(TAG, "Could not find current source ID for URL: $sourceUrl")
                        }
                    } else {
                        Log.w(TAG, "Could not find URL for source ID: ${ref.sourceId}")
                    }
                } else {
                    Log.w(TAG, "Could not find mapping for feed ID: ${ref.feedId}")
                }
            }
            
            // Get existing keyword rules by name for proper reference mapping
            val ruleNameToIdMap = getRuleNameToIdMap()
            val keywordRuleDao = dataManager.database.keywordRuleDao()
            val backupRules = keywordRuleDao.getAllRules()
            val backupRuleIdToNameMap = backupRules.associate { it.id to it.name }
            
            // Insert feed-keyword rule cross-references for both new and existing feeds
            feedsData.feedKeywordRuleRefs.forEach { ref ->
                val targetFeedId = feedIdMap[ref.feedId]
                if (targetFeedId != null) {
                    // Get the rule name from the backup rule ID
                    val ruleName = backupRuleIdToNameMap[ref.ruleId]
                    if (ruleName != null) {
                        // Find the correct rule ID in the current database
                        val currentRuleId = ruleNameToIdMap[ruleName]
                        if (currentRuleId != null) {
                            // Check if this feed-rule association already exists
                            val existingRefs = feedDao.getKeywordRuleRefsForFeed(targetFeedId)
                            val alreadyExists = existingRefs.any { it.ruleId == currentRuleId }
                            
                            if (!alreadyExists) {
                                feedDao.insertFeedKeywordRuleCrossRef(
                                    ReadingFeedKeywordRuleCrossRef(targetFeedId, currentRuleId)
                                )
                                Log.d(TAG, "Inserted feed-rule ref: feed=$targetFeedId, rule=$currentRuleId (Name: $ruleName)")
                            } else {
                                Log.d(TAG, "Feed-rule ref already exists: feed=$targetFeedId, rule=$currentRuleId")
                            }
                        } else {
                            Log.w(TAG, "Could not find current rule ID for name: $ruleName")
                        }
                    } else {
                        Log.w(TAG, "Could not find name for rule ID: ${ref.ruleId}")
                    }
                } else {
                    Log.w(TAG, "Could not find mapping for feed ID: ${ref.feedId}")
                }
            }
        }
    }
// CodeCleaner_End_c84f7fe2-f265-45e5-b90e-94da750b6774

    
// CodeCleaner_Start_0826d849-4a08-4bb2-8451-f41daa09261f
    private suspend fun restoreRepeatedSessions(
        sessionsData: RepeatedSessionsBackupData,
        overwriteExisting: Boolean
    ) = withContext(Dispatchers.IO) {
        val sessionDao = dataManager.database.repeatedSessionDao()
        val feedDao = dataManager.database.readingFeedDao()
        
        // Get existing feeds by name for proper feed ID mapping
        val feedNameToIdMap = getFeedNameToIdMap()
        val existingFeeds = feedDao.getAllFeeds()
        
        // Create map of feed IDs to names
        val feedIdToNameMap = mutableMapOf<Long, String>()
        existingFeeds.forEach { feed ->
            feedIdToNameMap[feed.id] = feed.name
        }
        
        // Create a mapping of backup feed IDs to feed names
        // Use names as an intermediate step to map across backups
        val backupFeedIdToNameMap = mutableMapOf<Long, String>()
        
        // First populate from the backup data feeds
        if (sessionsData.sessions.isNotEmpty()) {
            // Get all feeds that might be referenced in the sessions
            val allFeeds = feedDao.getAllFeeds()
            allFeeds.forEach { feed ->
                backupFeedIdToNameMap[feed.id] = feed.name
            }
        }
        
        if (overwriteExisting) {
            // Delete all existing sessions and insert new ones
            sessionDao.deleteAllSessions()
            
            // Create a mapping of old session IDs to new session IDs
            val sessionIdMap = mutableMapOf<Long, Long>()
            
            // Insert sessions first with correctly mapped feed IDs and keep track of new session IDs
            sessionsData.sessions.forEach { session ->
                val oldId = session.id ?: 0L
                
                // Find the correct feed ID by using the feed name
                val feedName = backupFeedIdToNameMap[session.feedId]
                val currentFeedId = if (feedName != null) {
                    feedNameToIdMap[feedName]
                } else {
                    // Fallback to the original feed ID if the name can't be found
                    session.feedId
                }
                
                if (currentFeedId != null) {
                    // Create a new session with the correct feed ID
                    val newSession = session.copy(id = null, feedId = currentFeedId)
                    val newId = sessionDao.insertSession(newSession)
                    sessionIdMap[oldId] = newId
                    Log.d(TAG, "Session ID mapping: $oldId -> $newId (feed: ${session.feedId} -> $currentFeedId)")
                } else {
                    Log.w(TAG, "Could not find current feed ID for session ${session.name}")
                }
            }
            
            // Insert session rules with updated session IDs
            sessionsData.rules.forEach { sessionRule ->
                val newSessionId = sessionIdMap[sessionRule.sessionId]
                if (newSessionId != null) {
                    sessionDao.insertRule(sessionRule.copy(id = 0, sessionId = newSessionId))
                    Log.d(TAG, "Inserted rule for session: sessionId=$newSessionId")
                } else {
                    Log.w(TAG, "Could not find mapping for session ID: ${sessionRule.sessionId}")
                }
            }
        } else {
            // Preserve existing sessions, just add new ones
            val existingSessions = sessionDao.getAllSessions()
            val existingSessionNames = existingSessions.map { it.name }
            
            // Create a mapping of old session IDs to new session IDs for the sessions we add
            val sessionIdMap = mutableMapOf<Long, Long>()
            
            // First, map existing session IDs
            val sessionNameToIdMap = existingSessions.associate { it.name to it.id }
            sessionsData.sessions.forEach { session ->
                if (session.name in existingSessionNames) {
                    // Map old session ID to existing session ID with the same name
                    val existingId = sessionNameToIdMap[session.name]
                    if (existingId != null) {
                        sessionIdMap[session.id ?: 0L] = existingId
                        Log.d(TAG, "Existing session mapping: ${session.id} -> $existingId (${session.name})")
                    }
                }
            }
            
            // Insert sessions that don't exist by name
            sessionsData.sessions.forEach { session ->
                if (session.name !in existingSessionNames) {
                    val oldId = session.id ?: 0L
                    
                    // Find the correct feed ID by using the feed name
                    val feedName = backupFeedIdToNameMap[session.feedId]
                    val currentFeedId = if (feedName != null) {
                        feedNameToIdMap[feedName]
                    } else {
                        // Try to find a feed with the same ID
                        val existingFeed = existingFeeds.firstOrNull { it.id == session.feedId }
                        if (existingFeed != null) {
                            existingFeed.id
                        } else {
                            // If we can't find a feed, use the default feed if available
                            existingFeeds.firstOrNull { it.isDefault }?.id ?: session.feedId
                        }
                    }
                    
                    if (currentFeedId != null) {
                        // Create a new session with the correct feed ID
                        val newSession = session.copy(id = null, feedId = currentFeedId)
                        val newId = sessionDao.insertSession(newSession)
                        sessionIdMap[oldId] = newId
                        Log.d(TAG, "New session mapping: $oldId -> $newId (feed: ${session.feedId} -> $currentFeedId)")
                    } else {
                        Log.w(TAG, "Could not find valid feed ID for session ${session.name}")
                    }
                }
            }
            
            // Insert session rules for the new sessions with updated session IDs
            sessionsData.rules.forEach { sessionRule ->
                val newSessionId = sessionIdMap[sessionRule.sessionId]
                if (newSessionId != null) {
                    // Check if this session already has similar rules
                    val existingRules = sessionDao.getRulesForSession(newSessionId)
                    val isSimilarRuleExists = existingRules.any { 
                        it.type == sessionRule.type &&
                        it.intervalMinutes == sessionRule.intervalMinutes &&
                        it.timeOfDay == sessionRule.timeOfDay &&
                        it.daysOfWeek == sessionRule.daysOfWeek
                    }
                    
                    if (!isSimilarRuleExists) {
                        sessionDao.insertRule(sessionRule.copy(id = 0, sessionId = newSessionId))
                        Log.d(TAG, "Inserted rule for session: sessionId=$newSessionId")
                    } else {
                        Log.d(TAG, "Similar rule already exists for session: sessionId=$newSessionId")
                    }
                } else {
                    Log.w(TAG, "Could not find mapping for session ID: ${sessionRule.sessionId}")
                }
            }
        }
    }
// CodeCleaner_End_0826d849-4a08-4bb2-8451-f41daa09261f

    
// CodeCleaner_Start_4545e2ae-577d-4cc3-ba48-a53a45c0cf5a
    private suspend fun restoreSettings(
        settingsData: AppSettingsBackupData,
        overwriteExisting: Boolean
    ) = withContext(Dispatchers.IO) {
        // List of keys that must be integers for the app to function correctly
        val mustBeIntKeys = listOf(
            "article_body_length",
            "headlines_per_session",
            "delay_between_headlines"
        )
        
        if (overwriteExisting) {
            // Clear existing preferences (except tts_lang_voice_preferences which is handled separately)
            val editor = prefs.edit()
            prefs.all.keys.forEach { key ->
                if (key != "tts_lang_voice_preferences") {
                    editor.remove(key)
                }
            }
            editor.apply()
            
            // Restore preferences
            val editor2 = prefs.edit()
            settingsData.sharedPreferences.forEach { (key, value) ->
                when {
                    // Handle special case for keys that must be integers
                    key in mustBeIntKeys && value is Number -> {
                        editor2.putInt(key, value.toInt())
                        Log.d(TAG, "Restored $key as Int: ${value.toInt()} (was ${value::class.java.simpleName})")
                    }
                    // Standard type handling
                    value is Boolean -> editor2.putBoolean(key, value)
                    value is Int -> editor2.putInt(key, value)
                    value is Long -> editor2.putLong(key, value)
                    value is Float -> editor2.putFloat(key, value)
                    value is String -> editor2.putString(key, value)
                    value is Double -> editor2.putFloat(key, value.toFloat())
                    value is Number -> {
                        // Safely handle any other number type by converting to appropriate type
                        // This helps with cross-platform backups where type serialization might differ
                        if (value.toDouble() == value.toDouble().toLong().toDouble()) {
                            // It's a whole number, store as integer
                            editor2.putInt(key, value.toInt())
                            Log.d(TAG, "Restored $key as Int: ${value.toInt()} (was ${value::class.java.simpleName})")
                        } else {
                            // It has decimal points, store as float
                            editor2.putFloat(key, value.toFloat())
                            Log.d(TAG, "Restored $key as Float: ${value.toFloat()} (was ${value::class.java.simpleName})")
                        }
                    }
                }
            }
            editor2.apply()
            
            // Restore TTS preferences
            TtsPreferenceManager.savePreferences(context, settingsData.ttsPreferences)
        } else {
            // Only restore preferences that don't exist
            val editor = prefs.edit()
            settingsData.sharedPreferences.forEach { (key, value) ->
                if (!prefs.contains(key)) {
                    when {
                        // Handle special case for keys that must be integers
                        key in mustBeIntKeys && value is Number -> {
                            editor.putInt(key, value.toInt())
                            Log.d(TAG, "Restored $key as Int: ${value.toInt()} (was ${value::class.java.simpleName})")
                        }
                        // Standard type handling
                        value is Boolean -> editor.putBoolean(key, value)
                        value is Int -> editor.putInt(key, value)
                        value is Long -> editor.putLong(key, value)
                        value is Float -> editor.putFloat(key, value)
                        value is String -> editor.putString(key, value)
                        value is Double -> editor.putFloat(key, value.toFloat())
                        value is Number -> {
                            // Safely handle any other number type
                            if (value.toDouble() == value.toDouble().toLong().toDouble()) {
                                // It's a whole number, store as integer
                                editor.putInt(key, value.toInt())
                                Log.d(TAG, "Restored $key as Int: ${value.toInt()} (was ${value::class.java.simpleName})")
                            } else {
                                // It has decimal points, store as float
                                editor.putFloat(key, value.toFloat())
                                Log.d(TAG, "Restored $key as Float: ${value.toFloat()} (was ${value::class.java.simpleName})")
                            }
                        }
                    }
                }
            }
            editor.apply()
            
            // Merge TTS preferences (existing engines keep their preferences, new engines are added)
            val existingTtsPrefs = TtsPreferenceManager.loadPreferences(context)
            val mergedPrefs = mergeTtsPreferences(existingTtsPrefs, settingsData.ttsPreferences)
            TtsPreferenceManager.savePreferences(context, mergedPrefs)
        }
    }
// CodeCleaner_End_4545e2ae-577d-4cc3-ba48-a53a45c0cf5a

    
// CodeCleaner_Start_c34a02c1-a28e-497f-ae3a-546bcc589db9
    private suspend fun restoreReadMarks(
        readMarksData: ReadMarksBackupData,
        overwriteExisting: Boolean
    ) = withContext(Dispatchers.IO) {
        val articleDao = dataManager.database.newsArticleDao()
        
        if (overwriteExisting) {
            // Mark all articles as unread first
            articleDao.markAllArticlesUnread()
        }
        
        // Mark articles as read
        readMarksData.readArticleLinks.forEach { link ->
            articleDao.markArticleReadByLink(link)
        }
    }
    
    /**
     * Restore AI filter cache from backup
     */
    private suspend fun restoreAiFilterCache(
        aiFilterCacheData: AiFilterCacheBackupData,
        overwriteExisting: Boolean
    ) = withContext(Dispatchers.IO) {
        val aiFilterResultDao = dataManager.database.aiFilterResultDao()
        
        if (overwriteExisting) {
            // Clear existing cache
            aiFilterResultDao.deleteAll()
            
            // Restore all entries from backup
            aiFilterResultDao.insertAll(aiFilterCacheData.filterResults)
            Log.d(TAG, "Restored ${aiFilterCacheData.filterResults.size} AI filter cache entries (replaced existing cache)")
        } else {
            // Only insert entries that don't exist yet
            val existingResults = aiFilterResultDao.getAll()
            val existingLinks = existingResults.map { it.articleLink }.toSet()
            
            val newResults = aiFilterCacheData.filterResults.filter { it.articleLink !in existingLinks }
            if (newResults.isNotEmpty()) {
                aiFilterResultDao.insertAll(newResults)
                Log.d(TAG, "Restored ${newResults.size} new AI filter cache entries (kept existing entries)")
            } else {
                Log.d(TAG, "No new AI filter cache entries to restore")
            }
        }
        
        // Clear the in-memory cache to force reload from database
        try {
            val geminiService = dataManager.getGeminiService(context)
            geminiService.clearCache()
            Log.d(TAG, "Cleared in-memory AI filter cache to force reload from database")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing in-memory AI filter cache: ${e.message}")
        }
    }
    
    /**
     * Restore AI rules from backup
     */
    private suspend fun restoreAiRules(
        aiRulesData: AiRulesBackupData,
        overwriteExisting: Boolean
    ) = withContext(Dispatchers.IO) {
        val aiRuleDao = dataManager.database.aiRuleDao()
        
        if (overwriteExisting) {
            // Get existing rules first to handle cross-references later
            val existingRules = aiRuleDao.getAllRules()
            val existingRuleIds = existingRules.map { it.id }
            
            // Delete all existing rules
            existingRules.forEach { rule ->
                aiRuleDao.deleteRule(rule)
            }
            
            // Create a mapping of old rule IDs to new rule IDs
            val ruleIdMap = mutableMapOf<Long, Long>()
            
            // Insert rules from backup with new IDs
            aiRulesData.rules.forEach { rule ->
                val oldId = rule.id
                val newId = aiRuleDao.insertRule(rule.copy(id = 0L)) // Reset ID for insertion
                ruleIdMap[oldId] = newId
                Log.d(TAG, "Restored AI rule: ${rule.name} (ID: $oldId -> $newId)")
            }
            
            Log.d(TAG, "Restored ${aiRulesData.rules.size} AI rules (replaced existing rules)")
            
            // Update ReadingFeedAiRuleCrossRef entries to use new rule IDs
            updateFeedAiRuleReferences(ruleIdMap, existingRuleIds)
        } else {
            // Only insert rules that don't exist by name
            val existingRules = aiRuleDao.getAllRules()
            val existingRuleNames = existingRules.map { it.name }
            
            // Create a mapping of old rule IDs to new rule IDs
            val ruleIdMap = mutableMapOf<Long, Long>()
            
            // First, add existing rule IDs to the map
            aiRulesData.rules.forEach { rule ->
                if (rule.name in existingRuleNames) {
                    // Map old rule ID to existing rule ID with the same name
                    val existingRule = existingRules.first { it.name == rule.name }
                    ruleIdMap[rule.id] = existingRule.id
                }
            }
            
            // Insert rules that don't exist by name
            var newRulesCount = 0
            aiRulesData.rules.forEach { rule ->
                if (rule.name !in existingRuleNames) {
                    val oldId = rule.id
                    val newId = aiRuleDao.insertRule(rule.copy(id = 0L)) // Reset ID for insertion
                    ruleIdMap[oldId] = newId
                    newRulesCount++
                    Log.d(TAG, "Restored new AI rule: ${rule.name} (ID: $oldId -> $newId)")
                }
            }
            
            Log.d(TAG, "Restored $newRulesCount new AI rules (kept existing rules)")
            
            // We don't need to update cross-references for existing rules,
            // but we do need to update them for new rules
            if (newRulesCount > 0) {
                val existingRuleIds = existingRules.map { it.id }
                updateFeedAiRuleReferences(ruleIdMap, existingRuleIds)
            }
        }
    }
    
    // Helper function to merge TTS preferences
    private fun mergeTtsPreferences(
        existing: TtsPreferences,
        new: TtsPreferences
    ): TtsPreferences {
        val mergedEnginePrefs = existing.enginePreferences.toMutableMap()
        
        // Add or merge engine preferences
        new.enginePreferences.forEach { (engineName, enginePrefs) ->
            if (engineName !in mergedEnginePrefs) {
                // Add new engine preferences
                mergedEnginePrefs[engineName] = enginePrefs
            } else {
                // Merge language preferences for existing engine
                val existingLangPrefs = mergedEnginePrefs[engineName]!!.languageVoicePreferences
                val newLangPrefs = enginePrefs.languageVoicePreferences
                
                // Find language codes that don't exist in the existing preferences
                val existingLangCodes = existingLangPrefs.map { it.languageCode }
                val newUniqueLangPrefs = newLangPrefs.filter { it.languageCode !in existingLangCodes }
                
                // Create merged preferences
                val mergedLangPrefs = existingLangPrefs + newUniqueLangPrefs
                
                mergedEnginePrefs[engineName] = TtsEnginePreferences(
                    engineName = engineName,
                    languageVoicePreferences = mergedLangPrefs
                )
            }
        }
        
        return TtsPreferences(mergedEnginePrefs)
    }
// CodeCleaner_End_c34a02c1-a28e-497f-ae3a-546bcc589db9

    
    /**
     * Updates reading feed AI rule references when rule IDs change
     */
    private suspend fun updateFeedAiRuleReferences(
        ruleIdMap: Map<Long, Long>,
        existingRuleIds: List<Long>
    ) = withContext(Dispatchers.IO) {
        val feedDao = dataManager.database.readingFeedDao()
        val feeds = feedDao.getAllFeeds()
        
        // For each feed, update AI rule references
        feeds.forEach { feed ->
            // Get existing AI rule references for this feed
            val aiRuleRefs = feedDao.getAiRuleRefsForFeed(feed.id)
            
            // Update references that point to rules that have been remapped
            aiRuleRefs.forEach { ref ->
                // Only update references to rules that are in the ruleIdMap
                // and not in existingRuleIds (which means they were deleted and recreated)
                if (ref.ruleId in ruleIdMap.keys && ref.ruleId !in existingRuleIds) {
                    val newRuleId = ruleIdMap[ref.ruleId] ?: return@forEach
                    
                    // Delete the old reference
                    feedDao.deleteFeedAiRuleCrossRefByIds(feed.id, ref.ruleId)
                    
                    // Create a new reference with the updated rule ID
                    feedDao.insertFeedAiRuleCrossRef(
                        ReadingFeedAiRuleCrossRef(feed.id, newRuleId, ref.isWhitelist)
                    )
                    
                    Log.d(TAG, "Updated feed-AI rule reference: feed=${feed.id}, rule=${ref.ruleId} -> $newRuleId")
                }
            }
        }
    }

}

// Data classes for backup data

// CodeCleaner_Start_2132a1cc-4b04-4f15-b663-2543495b87b1
data class ReadingFeedsBackupData(
    val feeds: List<ReadingFeed>,
    val feedSourceRefs: List<ReadingFeedSourceCrossRef>,
    val feedKeywordRuleRefs: List<ReadingFeedKeywordRuleCrossRef>,
    val feedAiRuleRefs: List<ReadingFeedAiRuleCrossRef> = emptyList()
)

data class KeywordRulesBackupData(
    val rules: List<KeywordRule>,
    val keywords: List<KeywordItem>
)

data class RepeatedSessionsBackupData(
    val sessions: List<RepeatedSession>,
    val rules: List<RepeatedSessionRule>
)

data class AppSettingsBackupData(
    val sharedPreferences: Map<String, Any?>,
    val ttsPreferences: TtsPreferences
)

data class ReadMarksBackupData(
    val readArticleLinks: List<String>
)

data class AiFilterCacheBackupData(
    val filterResults: List<AiFilterResult>
)

data class AiRulesBackupData(
    val rules: List<AiRule>
)
// CodeCleaner_End_2132a1cc-4b04-4f15-b663-2543495b87b1
