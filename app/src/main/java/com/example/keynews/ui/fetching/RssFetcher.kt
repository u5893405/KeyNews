package com.example.keynews.ui.fetching

// CodeCleaner_Start_92ffd0b5-00ff-468d-a4a3-58c8da4e3044
import android.util.Log
import com.example.keynews.data.model.NewsArticle
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.parser.Parser as JsoupParser
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader

class RssFetcher {
    private val feedUrls = mutableListOf<String>()
    private val TAG = "RssFetcher"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),  // RFC 822
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),       // ISO 8601
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH)    // ISO 8601 with millis
    )
    
    // List to track failed feeds
    private val failedFeeds = mutableListOf<String>()
    
    fun setFeedUrls(urls: List<String>) {
        feedUrls.clear()
        feedUrls.addAll(urls)
    }
    
    private suspend fun getSource(url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("HTTP request failed with status ${response.code}")
                }
                
                val body = response.body?.string() ?: ""
                
                // Check for BOM and remove if present
                val cleanBody = if (body.startsWith("\uFEFF")) {
                    body.substring(1)
                } else {
                    body
                }.trim()
                
                // Ensure body starts with an XML tag
                if (!cleanBody.startsWith('<')) {
                    Log.e(TAG, "Content does not start with an opening XML tag: $cleanBody")
                    throw Exception("Content does not start with an opening XML tag")
                }
                
                cleanBody
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching RSS: ${e.message}", e)
                throw e
            }
        }
    }
    
    suspend fun fetchAllRssFeeds(sourceUrlMap: Map<String, Long>): List<NewsArticle> {
        val allArticles = mutableListOf<NewsArticle>()
        failedFeeds.clear()
        
        for ((url, sourceId) in sourceUrlMap) {
            try {
                val xmlSource = getSource(url)
                
                // Parse with Rome
                val input = SyndFeedInput()
                val feed = input.build(XmlReader(ByteArrayInputStream(xmlSource.toByteArray(StandardCharsets.UTF_8))))
                
                Log.d(TAG, "Processing RSS feed from: $url with ${feed.entries.size} items")
                
                feed.entries.forEach { entry ->
                    val pubDate = entry.publishedDate?.time ?: System.currentTimeMillis()
                    val description = entry.description?.value ?: ""
                    val cleanDescription = cleanHtmlContent(description)
                    
                    val newsArticle = NewsArticle(
                        link = entry.link ?: UUID.randomUUID().toString(),
                        sourceId = sourceId,
                        title = entry.title ?: "No title",
                        description = cleanDescription,
                        publishDateUtc = pubDate,
                        isRead = false
                    )
                    allArticles.add(newsArticle)
                }
            } catch (e: Exception) {
                failedFeeds.add(url)
                Log.e(TAG, "Error processing $url: ${e.message}", e)
            }
        }
        
        return allArticles
    }
    
    private fun parseDate(dateString: String?): Long {
        if (dateString.isNullOrEmpty()) {
            return System.currentTimeMillis()
        }
        
        for (format in dateFormats) {
            try {
                return format.parse(dateString)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        // If all parsing attempts fail, return current time
        return System.currentTimeMillis()
    }
    
    private fun cleanHtmlContent(htmlString: String): String {
        if (htmlString.isEmpty()) return ""
        
        val document = Jsoup.parse(htmlString, "", JsoupParser.xmlParser())
        var text = document.text()
        text = text.replace("■", ".")
        return text
    }
    
    companion object {
        // Static method for backward compatibility
        suspend fun parseRss(rssUrl: String, sourceId: Long): List<NewsArticle> {
            val fetcher = RssFetcher()
            return fetcher.fetchAllRssFeeds(mapOf(rssUrl to sourceId))
        }
    }
}
// CodeCleaner_End_92ffd0b5-00ff-468d-a4a3-58c8da4e3044

