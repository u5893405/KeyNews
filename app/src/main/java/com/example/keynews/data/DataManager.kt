package com.example.keynews.data

// CodeCleaner_Start_c9398b26-9f7c-4727-83e8-cfaacbc5280b
import android.util.Log
import com.example.keynews.data.db.AppDatabase
import com.example.keynews.data.model.AiRule
import com.example.keynews.data.model.KeywordItem
import com.example.keynews.data.model.KeywordRule
import com.example.keynews.data.model.NewsArticle
import com.example.keynews.data.model.NewsSource
import com.example.keynews.service.GeminiService
import com.example.keynews.ui.fetching.RssFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataManager(private val db: AppDatabase) {
    // Gemini service for AI filtering
    private var geminiService: GeminiService? = null
    
    companion object {
        private const val TAG = "DataManager"
    }

    val database: AppDatabase
    get() = db

    suspend fun saveNewsSource(source: NewsSource) = withContext(Dispatchers.IO) {
        db.newsSourceDao().insert(source)
    }

    suspend fun getAllSources() = withContext(Dispatchers.IO) {
        db.newsSourceDao().getAllSources()
    }

    suspend fun saveArticles(articles: List<NewsArticle>) = withContext(Dispatchers.IO) {
        db.newsArticleDao().insertAll(articles)
    }

    /**
     * Refreshes articles for the specified feed.
     * Retrieves the RSS URLs associated with the feed, fetches new articles using RssFetcher,
     * and inserts them into the database.
     */
    suspend fun refreshArticles(feedId: Long) = withContext(Dispatchers.IO) {
        val readingFeedDao = db.readingFeedDao()
        val newsSourceDao = db.newsSourceDao()

        val sourceRefs = readingFeedDao.getSourceIdsForFeed(feedId)
        if (sourceRefs.isEmpty()) {
            return@withContext
        }

        val sourceIds = sourceRefs.map { it.sourceId }
        val sources = newsSourceDao.getSourcesByIds(sourceIds)

        val sourceUrlMap = mutableMapOf<String, Long>()
        sources.forEach { source ->
            if (source.rssUrl.isNotEmpty()) {
                sourceUrlMap[source.rssUrl] = source.id
            }
        }

        if (sourceUrlMap.isNotEmpty()) {
            val rssFetcher = RssFetcher()
            val newArticles = rssFetcher.fetchAllRssFeeds(sourceUrlMap)

            if (newArticles.isNotEmpty()) {
                val articleDao = db.newsArticleDao()
                // For each fetched article, check if it already exists. If so, preserve isRead.
                val articlesToInsert = newArticles.map { newArticle ->
                    val existingArticle = articleDao.getArticleByLink(newArticle.link)
                    if (existingArticle != null) {
                        newArticle.copy(isRead = existingArticle.isRead)
                    } else {
                        newArticle
                    }
                }
                articleDao.insertAll(articlesToInsert)
                
                // Update last refresh timestamp for this feed
                com.example.keynews.FeedRefreshTracker.setLastRefreshTime(feedId)
            }
        }
    }
    
    /**
     * Gets the GeminiService, initializing it if needed
     */
    fun getGeminiService(context: android.content.Context): GeminiService {
        if (geminiService == null) {
            Log.d(TAG, "🔍 AI FILTERING: Creating new GeminiService instance")
            geminiService = GeminiService(context)
            Log.d(TAG, "🔍 AI FILTERING: New GeminiService created, cache info: ${geminiService!!.getCacheDebugInfo()}")
        } else {
            Log.d(TAG, "🔍 AI FILTERING: Using existing GeminiService instance, cache info: ${geminiService!!.getCacheDebugInfo()}")
        }
        return geminiService!!
    }
    
    /**
     * Gets articles for the specified feed, applying any keyword filtering rules.
     * Also returns a map of article links to matched whitelist keywords for highlighting.
     * 
     * @param feedId The ID of the reading feed
     * @param unreadOnly Whether to return only unread articles
     * @param ageThresholdMinutes Optional threshold to filter articles by age in minutes
     * @param context Android context for AI filtering
     * @return Filtered list of articles
     */
    suspend fun getArticlesForFeed(feedId: Long, unreadOnly: Boolean, ageThresholdMinutes: Int? = null, context: android.content.Context? = null): List<NewsArticle> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getArticlesForFeed called with feedId: $feedId, unreadOnly: $unreadOnly, context: ${context != null}")
        val readingFeedDao = db.readingFeedDao()
        val articleDao = db.newsArticleDao()
        val keywordRuleDao = db.keywordRuleDao()
        
        // Get the source IDs for this feed
        val sourceRefs = readingFeedDao.getSourceIdsForFeed(feedId)
        if (sourceRefs.isEmpty()) {
            Log.d(TAG, "No sources found for feed $feedId")
            return@withContext emptyList<NewsArticle>()
        }
        
        val sourceIds = sourceRefs.map { it.sourceId }
        Log.d(TAG, "Found ${sourceIds.size} sources for feed $feedId")
        
        // Get all articles for these sources
        val articles = if (unreadOnly) {
            articleDao.getUnreadArticlesBySourceIds(sourceIds)
        } else {
            articleDao.getArticlesBySourceIds(sourceIds)
        }
        Log.d(TAG, "Retrieved ${articles.size} articles for feed $feedId (unreadOnly: $unreadOnly)")
        
        // Get keyword rules for this feed
        val keywordRules = readingFeedDao.getKeywordRulesForFeed(feedId)
        Log.d(TAG, "Found ${keywordRules.size} keyword rules for feed $feedId")
        
        if (keywordRules.isEmpty()) {
            Log.d(TAG, "No keyword rules found, skipping keyword filtering")
            
            // Apply age threshold filtering directly if needed
            val ageFilteredArticles = applyAgeFiltering(articles, ageThresholdMinutes)
            
            // Apply AI filtering if context is provided
            return@withContext if (context != null) {
                Log.d(TAG, "🔍 AI FILTERING: Context provided, checking for AI rules")
                applyAiFiltering(ageFilteredArticles, feedId, context)
            } else {
                Log.d(TAG, "🔍 AI FILTERING: No context provided, skipping AI filtering")
                ageFilteredArticles
            }
        }
        
        // Separate whitelist and blacklist rules
        val whitelistRules = keywordRules.filter { it.isWhitelist }
        val blacklistRules = keywordRules.filter { !it.isWhitelist }
        Log.d(TAG, "Keyword rules: ${whitelistRules.size} whitelist, ${blacklistRules.size} blacklist")
        
        // Get keywords for each rule with their options
        val whitelistKeywordItems = whitelistRules.flatMap { rule ->
            keywordRuleDao.getKeywordsForRule(rule.id)
        }
        
        val blacklistKeywordItems = blacklistRules.flatMap { rule ->
            keywordRuleDao.getKeywordsForRule(rule.id)
        }
        Log.d(TAG, "Keywords: ${whitelistKeywordItems.size} whitelist, ${blacklistKeywordItems.size} blacklist")
        
        // Storage for matched keywords per article
        val matchedKeywordsMap = mutableMapOf<String, MutableList<String>>()
        
        // Apply keyword filtering
        val filteredArticles = articles.filter { article ->
            val title = article.title
            val description = article.description ?: ""
            val content = "$title $description"
            
            // If there are whitelist rules, article must match at least one whitelist keyword
            val passesWhitelist = if (whitelistKeywordItems.isNotEmpty()) {
                // Track which whitelist keywords match this article
                val matchedKeywords = mutableListOf<String>()
                
                whitelistKeywordItems.any { keywordItem -> 
                    // Check if keyword matches based on its configuration
                    val matches = matchesKeyword(content, keywordItem)
                    // If matched, add to the list for highlighting
                    if (matches) {
                        matchedKeywords.add(keywordItem.keyword)
                    }
                    matches
                }
                
                // If any matches were found, store them for highlighting
                if (matchedKeywords.isNotEmpty()) {
                    matchedKeywordsMap[article.link] = matchedKeywords.toMutableList()
                    true
                } else {
                    false
                }
            } else {
                true // No whitelist rules, so all articles pass
            }
            
            // Article must not match any blacklist keyword
            val passesBlacklist = blacklistKeywordItems.none { keywordItem -> 
                matchesKeyword(content, keywordItem)
            }
            
            // Whitelist has priority, so it can override blacklist
            passesWhitelist && passesBlacklist
        }
        
        // Store the matched keywords for later use
        matchedKeywordsCache.clear()
        matchedKeywordsCache.putAll(matchedKeywordsMap)
        Log.d(TAG, "After keyword filtering: ${filteredArticles.size} of ${articles.size} articles remain")
        
        // Apply age threshold filtering if specified
        val ageFilteredArticles = applyAgeFiltering(filteredArticles, ageThresholdMinutes)
        
        // Apply AI filtering if context is provided
        return@withContext if (context != null) {
            Log.d(TAG, "🔍 AI FILTERING: Context provided, checking for AI rules")
            applyAiFiltering(ageFilteredArticles, feedId, context)
        } else {
            Log.d(TAG, "🔍 AI FILTERING: No context provided, skipping AI filtering")
            ageFilteredArticles
        }
    }
    
    /**
     * Apply age filtering to a list of articles
     */
    private fun applyAgeFiltering(articles: List<NewsArticle>, ageThresholdMinutes: Int?): List<NewsArticle> {
        if (ageThresholdMinutes == null || ageThresholdMinutes <= 0) {
            return articles
        }
        
        Log.d(TAG, "Filtering by age threshold: $ageThresholdMinutes minutes")
        val currentTimeMillis = System.currentTimeMillis()
        val thresholdMillis = ageThresholdMinutes * 60 * 1000L
        
        val filteredArticles = articles.filter { article ->
            val articleAge = currentTimeMillis - article.publishDateUtc
            val isRecent = articleAge <= thresholdMillis
            if (!isRecent) {
                Log.d(TAG, "Filtered out article due to age: ${article.title}")
            }
            isRecent
        }
        
        Log.d(TAG, "After age filtering: ${filteredArticles.size} of ${articles.size} articles remain")
        return filteredArticles
    }
    
    /**
     * Apply AI filtering to a list of articles
     */
    private suspend fun applyAiFiltering(articles: List<NewsArticle>, feedId: Long, context: android.content.Context): List<NewsArticle> {
        // Get AI rules for this feed
        val readingFeedDao = db.readingFeedDao()
        val whitelistAiRule = readingFeedDao.getWhitelistAiRuleForFeed(feedId)
        val blacklistAiRule = readingFeedDao.getBlacklistAiRuleForFeed(feedId)
        
        Log.d(TAG, "🔍 AI FILTERING: Retrieved AI rules for feed $feedId - whitelist: ${whitelistAiRule?.name ?: "none"}, blacklist: ${blacklistAiRule?.name ?: "none"}")
        
        if (whitelistAiRule == null && blacklistAiRule == null) {
            Log.d(TAG, "🔍 AI FILTERING: No AI rules found for feed $feedId, skipping AI filtering")
            return articles
        }
        
        val geminiService = getGeminiService(context)
        if (!geminiService.isApiKeySet()) {
            Log.w(TAG, "🔍 AI FILTERING: Gemini API key not set, skipping AI filtering")
            return articles
        }
        
        Log.d(TAG, "🔍 AI FILTERING: Starting AI filtering for ${articles.size} articles, cache info: ${geminiService.getCacheDebugInfo()}")
        
        Log.d(TAG, "🔍 AI FILTERING: Calling GeminiService.filterArticles with ${articles.size} articles")
        Log.d(TAG, "🔍 AI FILTERING: Whitelist rule: ${whitelistAiRule?.ruleText ?: "none"}")
        Log.d(TAG, "🔍 AI FILTERING: Blacklist rule: ${blacklistAiRule?.ruleText ?: "none"}")
        
        val startTime = System.currentTimeMillis()
        
        val aiFilteredArticles = geminiService.filterArticles(
            articles,
            whitelistAiRule?.ruleText,
            blacklistAiRule?.ruleText
        )
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        Log.d(TAG, "🔍 AI FILTERING: Completed in ${duration}ms. ${aiFilteredArticles.size} of ${articles.size} articles passed AI filtering")
        Log.d(TAG, "🔍 AI FILTERING: Final cache info after filtering: ${geminiService.getCacheDebugInfo()}")
        
        return aiFilteredArticles
    }
    
    /**
     * Checks if the content matches the keyword based on its configuration.
     * Supports case sensitivity, full word matching, and wildcards.
     */
    private fun matchesKeyword(content: String, keywordItem: KeywordItem): Boolean {
        val keyword = keywordItem.keyword
        val contentToCheck = if (keywordItem.isCaseSensitive) content else content.lowercase()
        val keywordToCheck = if (keywordItem.isCaseSensitive) keyword else keyword.lowercase()
        
        // Check if keyword contains wildcard
        if (keywordToCheck.contains('*', false)) {
            // If it's a full word match, we need to check word boundaries
            if (keywordItem.isFullWordMatch) {
                // Split into words and check each word
                val words = contentToCheck.split(Regex("\\s+|\\p{Punct}"))
                return words.any { word ->
                    matchesWildcardPattern(word, keywordToCheck)
                }
            } else {
                // Check if any part of the content matches the wildcard pattern
                return matchesWildcardPattern(contentToCheck, keywordToCheck)
            }
        } else {
            // No wildcard, regular keyword
            if (keywordItem.isFullWordMatch) {
                // Must match a full word
                val pattern = Regex("\\b${Regex.escape(keywordToCheck)}\\b")
                return pattern.containsMatchIn(contentToCheck)
            } else {
                // Simple contains check
                return contentToCheck.contains(keywordToCheck, ignoreCase = false)
            }
        }
    }
    
    /**
     * Checks if text matches a wildcard pattern.
     * Pattern can only have one wildcard (*) which matches any characters.  
     */
    private fun matchesWildcardPattern(text: String, pattern: String): Boolean {
        if (!pattern.contains('*', false)) {
            return text == pattern
        }
        
        // Split the pattern at the wildcard
        val parts = pattern.split('*', limit = 2)
        val prefix = parts[0]
        val suffix = parts[1]
        
        // Empty pattern or just a wildcard matches anything
        if (prefix.isEmpty() && suffix.isEmpty()) {
            return true
        }
        
        // Check if text starts with prefix and ends with suffix
        if (prefix.isEmpty()) {
            return text.endsWith(suffix)
        } else if (suffix.isEmpty()) {
            return text.startsWith(prefix)
        } else {
            return text.startsWith(prefix) && text.endsWith(suffix) && 
                text.length >= prefix.length + suffix.length
        }
    }
    
    // Cache of matched keywords for highlighting
    private val matchedKeywordsCache = mutableMapOf<String, List<String>>()
    
    /**
     * Gets the most recently matched keywords for articles.
     * This is used for UI highlighting.
     */
    fun getMatchedKeywords(): Map<String, List<String>> {
        return matchedKeywordsCache
    }
}
// CodeCleaner_End_c9398b26-9f7c-4727-83e8-cfaacbc5280b
