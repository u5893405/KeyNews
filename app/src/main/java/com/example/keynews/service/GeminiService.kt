package com.example.keynews.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.keynews.data.model.NewsArticle
import com.example.keynews.data.model.AiFilterResult
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope

class GeminiService(private val context: Context) {
    // Database access
    private val database: com.example.keynews.data.db.AppDatabase = com.example.keynews.KeyNewsApp.getDatabase(context)
    
    init {
        Log.d(TAG, "🔍 AI FILTERING: GeminiService instance created, static cache size: ${aiFilterResultsCache.size}")
        // Load cache from database if empty
        if (aiFilterResultsCache.isEmpty()) {
            kotlinx.coroutines.GlobalScope.launch {
                loadCacheFromDatabase()
            }
        }
    }
    
    // Interface for progress callbacks
    interface ProgressListener {
        fun onFilteringStarted(totalArticles: Int)
        fun onProgressUpdate(processed: Int, total: Int, currentBatch: Int, totalBatches: Int)
        fun onFilteringCompleted()
    }
    
    // Progress listener for callbacks
    private var progressListener: ProgressListener? = null
    
    /**
     * Set a progress listener to receive updates
     */
    fun setProgressListener(listener: ProgressListener?) {
        progressListener = listener
    }
    /**
     * Helper class to track article indices during batch processing
     */
    private data class ArticleWithIndex(val article: NewsArticle, val originalIndex: Int)
    
    companion object {
        private const val TAG = "GeminiService"
        private const val API_KEY_PREF = "gemini_api_key"
        private const val PREFERENCES_NAME = "keynews_settings"
        private const val MODEL_NAME = "gemini-2.0-flash-lite"
        private const val MAX_CHARS_PER_REQUEST = 5000
        // Used for estimating prompt overhead size
        private const val PROMPT_TEMPLATE_OVERHEAD = 300
        // Default age for cache cleanup (7 days)
        private const val DEFAULT_CACHE_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L
        
        // Static in-memory cache shared across all instances of GeminiService
        // This is backed by Room database for persistence but kept in memory for performance
        private val aiFilterResultsCache = mutableMapOf<String, Boolean>()
    }

    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    
    /**
     * Clears the current AI filter results cache
     */
    fun clearCache() {
        Log.d(TAG, "🔍 AI FILTERING: Clearing static cache (size before: ${aiFilterResultsCache.size})")
        aiFilterResultsCache.clear()
        
        // Also clear database cache
        kotlinx.coroutines.GlobalScope.launch {
            database.aiFilterResultDao().deleteAll()
            Log.d(TAG, "🔍 AI FILTERING: Database cache cleared")
        }
    }
    
    /**
     * Load cache from database
     */
    private suspend fun loadCacheFromDatabase() {
        try {
            val startTime = System.currentTimeMillis()
            val results = database.aiFilterResultDao().getAll()
            
            // Populate in-memory cache
            synchronized(aiFilterResultsCache) {
                results.forEach { result ->
                    aiFilterResultsCache[result.articleLink] = result.passesFilter
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "🔍 AI FILTERING: Loaded ${results.size} cache entries from database in ${duration}ms")
            
            // Clean up old entries
            cleanupOldCacheEntries()
        } catch (e: Exception) {
            Log.e(TAG, "🔍 AI FILTERING: Error loading cache from database", e)
        }
    }
    
    /**
     * Save cache entries to database
     */
    private suspend fun saveCacheToDatabase(entries: Map<String, Boolean>) {
        try {
            val startTime = System.currentTimeMillis()
            
            // Convert to entity objects
            val entityList = entries.map { (link, passesFilter) ->
                com.example.keynews.data.model.AiFilterResult(
                    articleLink = link,
                    passesFilter = passesFilter,
                    filterTimestamp = System.currentTimeMillis()
                )
            }
            
            // Insert into database
            database.aiFilterResultDao().insertAll(entityList)
            
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "🔍 AI FILTERING: Saved ${entityList.size} cache entries to database in ${duration}ms")
        } catch (e: Exception) {
            Log.e(TAG, "🔍 AI FILTERING: Error saving cache to database", e)
        }
    }
    
    /**
     * Remove old cache entries
     */
    private suspend fun cleanupOldCacheEntries() {
        try {
            val cutoffTime = System.currentTimeMillis() - DEFAULT_CACHE_MAX_AGE_MS
            val deletedCount = database.aiFilterResultDao().deleteOlderThan(cutoffTime)
            if (deletedCount > 0) {
                Log.d(TAG, "🔍 AI FILTERING: Cleaned up $deletedCount old cache entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔍 AI FILTERING: Error cleaning up old cache entries", e)
        }
    }
    
    /**
     * Get debug info about the cache
     */
    fun getCacheDebugInfo(): String {
        return "Cache size: ${aiFilterResultsCache.size}, " +
               "Pass ratio: ${aiFilterResultsCache.values.count { it } * 100.0 / aiFilterResultsCache.size.coerceAtLeast(1)}%"
    }
    
    /**
     * Get API key from SharedPreferences
     */
    fun getApiKey(): String? {
        val apiKey = preferences.getString(API_KEY_PREF, null)
        Log.d(TAG, "🔍 AI FILTERING: API key ${if (apiKey.isNullOrBlank()) "not found" else "found (length: ${apiKey.length})"} in preferences")
        return apiKey
    }
    
    /**
     * Save API key to SharedPreferences
     */
    fun saveApiKey(apiKey: String) {
        Log.d(TAG, "🔍 AI FILTERING: Saving API key of length ${apiKey.length} to preferences")
        preferences.edit().putString(API_KEY_PREF, apiKey).apply()
    }
    
    /**
     * Check if the API key is set
     */
    fun isApiKeySet(): Boolean {
        val hasKey = !getApiKey().isNullOrBlank()
        Log.d(TAG, "🔍 AI FILTERING: API key is ${if (hasKey) "set" else "NOT set"}")
        return hasKey
    }
    
    /**
     * Filter a list of articles using the Gemini API
     * 
     * @param articles List of articles to filter
     * @param whitelistRule The whitelist rule text or null if none
     * @param blacklistRule The blacklist rule text or null if none
     * @return List of filtered articles
     */
    suspend fun filterArticles(
        articles: List<NewsArticle>,
        whitelistRule: String?,
        blacklistRule: String?
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 AI FILTERING: Starting to filter ${articles.size} articles")
        Log.d(TAG, "🔍 AI FILTERING: Whitelist rule: ${whitelistRule ?: "none"}")
        Log.d(TAG, "🔍 AI FILTERING: Blacklist rule: ${blacklistRule ?: "none"}")
        
        if (articles.isEmpty()) {
            Log.d(TAG, "🔍 AI FILTERING: No articles to filter, returning empty list")
            return@withContext articles
        }
        
        if (whitelistRule.isNullOrBlank() && blacklistRule.isNullOrBlank()) {
            Log.d(TAG, "🔍 AI FILTERING: No AI rules provided, returning all articles")
            return@withContext articles
        }
        
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "🔍 AI FILTERING: No API key set for Gemini, skipping AI filtering")
            return@withContext articles
        }
        
        try {
            // Notify that filtering is starting
            progressListener?.onFilteringStarted(articles.size)
            
            // First check the cache for existing results
            Log.d(TAG, "🔍 AI FILTERING: Checking static cache (size: ${aiFilterResultsCache.size}) for ${articles.size} articles")
            var cacheHits = 0
            
            // Create a map to track which articles need processing
            val articlesNeedingProcessing = mutableListOf<NewsArticle>()
            val cacheResults = mutableMapOf<String, Boolean>()
            
            articles.forEach { article ->
                val cacheKey = article.link
                if (aiFilterResultsCache.containsKey(cacheKey)) {
                    cacheHits++
                    cacheResults[cacheKey] = aiFilterResultsCache[cacheKey] == true
                } else {
                    articlesNeedingProcessing.add(article)
                }
            }
            Log.d(TAG, "🔍 AI FILTERING: Found $cacheHits cache hits, ${articlesNeedingProcessing.size} articles need processing")
            
            // Update progress with cache hits
            progressListener?.onProgressUpdate(cacheHits, articles.size, 0, 0)
            
            // If all articles were in the cache, return the filtered results
            if (articlesNeedingProcessing.isEmpty()) {
                val cachedFilteredArticles = articles.filter { article -> 
                    cacheResults[article.link] == true 
                }
                Log.d(TAG, "🔍 AI FILTERING: All articles were in cache, returning ${cachedFilteredArticles.size} articles")
                
                // Notify that filtering is complete
                progressListener?.onFilteringCompleted()
                
                return@withContext cachedFilteredArticles
            }
            
            // Split articles into batches based on character count
            val batches = splitArticlesByCharacterCount(articlesNeedingProcessing)
            Log.d(TAG, "🔍 AI FILTERING: Split ${articlesNeedingProcessing.size} articles into ${batches.size} batches based on character count")
            
            // Initialize rate limiter
            val rateLimiter = LlmRateLimiter()
            
            // Process each batch with rate limiting
            val allResults = mutableMapOf<Int, Boolean>()
            
            // Track progress variables
            var processedArticles = cacheHits
            
            // Instead of processing all batches concurrently, we'll process them sequentially
            // to better manage rate limiting and progress tracking
            val results = mutableMapOf<Int, Boolean>()
            
            for ((batchIndex, batch) in batches.withIndex()) {
                val batchResults = processBatch(batchIndex, batch, batches.size, apiKey, whitelistRule, blacklistRule, rateLimiter)
                results.putAll(batchResults)
                
                // Update progress after each batch
                processedArticles += batch.size
                progressListener?.onProgressUpdate(processedArticles, articles.size, batchIndex + 1, batches.size)
            }
            
            // Map results back to articles and update cache
            val filteredArticles = mutableListOf<NewsArticle>()
            val newCacheEntries = mutableMapOf<String, Boolean>()
            
            articlesNeedingProcessing.forEachIndexed { index, article ->
                val result = results.getOrDefault(index, true) // Default to including if result missing
                aiFilterResultsCache[article.link] = result
                newCacheEntries[article.link] = result
                
                if (result) {
                    filteredArticles.add(article)
                    Log.d(TAG, "🔍 AI FILTERING: Including article: ${article.title}")
                } else {
                    Log.d(TAG, "🔍 AI FILTERING: Excluding article: ${article.title}")
                }
            }
            
            Log.d(TAG, "🔍 AI FILTERING: Static cache updated, new size: ${aiFilterResultsCache.size}")
            
            // Save new entries to database (if any)
            if (newCacheEntries.isNotEmpty()) {
                GlobalScope.launch(Dispatchers.IO) {
                    saveCacheToDatabase(newCacheEntries)
                }
            }

            
            // Add articles that were already in the cache and passed the filter
            val cachedFilteredArticles = articles.filter { article -> 
                article !in articlesNeedingProcessing && cacheResults[article.link] == true
            }
            filteredArticles.addAll(cachedFilteredArticles)
            
            Log.d(TAG, "🔍 AI FILTERING: Filtering complete. Returning ${filteredArticles.size} of ${articles.size} articles")
            
            // Notify that filtering is complete
            progressListener?.onFilteringCompleted()
            
            return@withContext filteredArticles
        } catch (e: Exception) {
            Log.e(TAG, "🔍 AI FILTERING: Error filtering articles with Gemini: ${e.message}", e)
            
            // Notify that filtering is complete even on error
            progressListener?.onFilteringCompleted()
            
            // On error, return the original list
            return@withContext articles
        }
    }
    
    /**
     * Build prompt for Gemini API
     */
    private fun buildPrompt(
        articlesWithIndex: List<ArticleWithIndex>,
        whitelistRule: String?,
        blacklistRule: String?
    ): String {
        val ruleDescription = if (!whitelistRule.isNullOrBlank()) {
            whitelistRule
        } else if (!blacklistRule.isNullOrBlank()) {
            blacklistRule
        } else {
            return "" // No rules provided
        }
        
        val isWhitelist = !whitelistRule.isNullOrBlank()
        
        val sb = StringBuilder()
        sb.append("Analyze following pieces of text (divided by \" symbols and marked with numbers) separately (NOT altogether) and write short conclusion if it's fits the description:\n")
        sb.append("\"$ruleDescription\".\n")
        sb.append("Write answers ONLY in format:\n")
        sb.append("\"1-no.\n2-yes.\n3-no.\"\n")
        sb.append("(choosing \"no\" or \"yes\" depending on each piece of text)\n")
        sb.append("Write nothing else.\n\n")
        
        articlesWithIndex.forEachIndexed { batchIndex, articleWithIndex ->
            val article = articleWithIndex.article
            sb.append("${batchIndex + 1}. \"${article.title} ${article.description ?: ""}\"\n\n")
        }
        
        return sb.toString()
    }
    
    /**
     * Splits articles into batches based on character count
     * This ensures no request exceeds the maximum size limit
     */
    private fun splitArticlesByCharacterCount(
        articles: List<NewsArticle>,
        maxCharsPerBatch: Int = MAX_CHARS_PER_REQUEST
    ): List<List<ArticleWithIndex>> {
        val batches = mutableListOf<List<ArticleWithIndex>>()
        val currentBatch = mutableListOf<ArticleWithIndex>()
        var currentBatchSize = PROMPT_TEMPLATE_OVERHEAD // Start with template overhead
        
        articles.forEachIndexed { index, article ->
            val articleContent = "${index + 1}. \"${article.title} ${article.description ?: ""}\"\n\n"
            val articleSize = articleContent.length
            
            // If adding this article would exceed limit and batch isn't empty, finalize current batch
            if (currentBatchSize + articleSize > maxCharsPerBatch && currentBatch.isNotEmpty()) {
                batches.add(currentBatch.toList())
                currentBatch.clear()
                currentBatchSize = PROMPT_TEMPLATE_OVERHEAD // Reset with template overhead
            }
            
            // Add article to current batch
            currentBatch.add(ArticleWithIndex(article, index))
            currentBatchSize += articleSize
        }
        
        // Add the final batch if not empty
        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch.toList())
        }
        
        return batches
    }
    
    /**
     * Parse the response from the Gemini API
     */
    private fun parseResponse(response: String, articlesWithIndex: List<ArticleWithIndex>): Map<Int, Boolean> {
        Log.d(TAG, "🔍 AI FILTERING: Parsing response: '$response'")
        val results = mutableMapOf<Int, Boolean>()
        
        // Parse lines like "1-yes." or "2-no."
        val regex = Regex("(\\d+)-(yes|no)", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(response)
        
        for (match in matches) {
            val batchIndex = match.groupValues[1].toIntOrNull()?.minus(1) ?: continue
            if (batchIndex < 0 || batchIndex >= articlesWithIndex.size) continue
            
            val result = match.groupValues[2].equals("yes", ignoreCase = true)
            val originalIndex = articlesWithIndex[batchIndex].originalIndex
            
            results[originalIndex] = result
            Log.d(TAG, "🔍 AI FILTERING: Parsed result for item ${batchIndex+1} (original ${originalIndex+1}): $result (${match.groupValues[2]})")
        }
        
        // Ensure we have a result for each article in this batch
        for (articleWithIndex in articlesWithIndex) {
            val originalIndex = articleWithIndex.originalIndex
            if (!results.containsKey(originalIndex)) {
                results[originalIndex] = true // Default to true (include) if no result
                Log.d(TAG, "🔍 AI FILTERING: No result found for index ${originalIndex+1}, defaulting to true")
            }
        }
        
        return results
    }
    
    /**
     * Process a single batch of articles with rate limiting
     */
    private suspend fun processBatch(
        batchIndex: Int,
        batch: List<ArticleWithIndex>,
        totalBatches: Int,
        apiKey: String,
        whitelistRule: String?,
        blacklistRule: String?,
        rateLimiter: LlmRateLimiter
    ): Map<Int, Boolean> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 AI FILTERING: Processing batch ${batchIndex+1}/${totalBatches} with ${batch.size} articles")
        
        // Apply rate limiting before making the API call
        rateLimiter.enqueueRequest()
        
        try {
            // Create the prompt for this batch
            val prompt = buildPrompt(batch, whitelistRule, blacklistRule)
            Log.d(TAG, "🔍 AI FILTERING: Built prompt for batch ${batchIndex+1}, ${prompt.length} chars")
            
            // Call the Gemini API
            val generativeModel = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey
            )
            
            Log.d(TAG, "🔍 AI FILTERING: Calling Gemini API for batch ${batchIndex+1}")
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: ""
            Log.d(TAG, "🔍 AI FILTERING: Received response from Gemini:\n$responseText")
            
            // Parse the response
            val results = parseResponse(responseText, batch)
            Log.d(TAG, "🔍 AI FILTERING: Parsed results for batch ${batchIndex+1}")
            
            results
        } catch (e: Exception) {
            Log.e(TAG, "🔍 AI FILTERING: Error processing batch ${batchIndex+1}: ${e.message}", e)
            // On error, default to including all articles in this batch
            batch.associate { it.originalIndex to true }
        }
    }
}
