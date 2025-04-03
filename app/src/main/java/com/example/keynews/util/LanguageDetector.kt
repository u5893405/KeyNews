package com.example.keynews.util

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.android.gms.tasks.Tasks
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Utility class to detect languages in text
 */
class LanguageDetector private constructor() {
    private val languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()
    
    companion object {
        private const val TAG = "LanguageDetector"
        private var instance: LanguageDetector? = null
        
        fun getInstance(): LanguageDetector {
            if (instance == null) {
                instance = LanguageDetector()
            }
            return instance!!
        }
        
        // Map of language codes to their display names
        val LANGUAGE_DISPLAY_NAMES = mapOf(
            "en" to "English",
            "fr" to "French",
            "de" to "German",
            "es" to "Spanish",
            "it" to "Italian",
            "pt" to "Portuguese",
            "ru" to "Russian",
            "zh" to "Chinese",
            "ja" to "Japanese",
            "ko" to "Korean",
            "ar" to "Arabic",
            "hi" to "Hindi",
            "cs" to "Czech",
            "da" to "Danish",
            "nl" to "Dutch",
            "fi" to "Finnish",
            "el" to "Greek",
            "he" to "Hebrew",
            "hu" to "Hungarian",
            "id" to "Indonesian",
            "no" to "Norwegian",
            "pl" to "Polish",
            "ro" to "Romanian",
            "sv" to "Swedish",
            "th" to "Thai",
            "tr" to "Turkish",
            "uk" to "Ukrainian",
            "vi" to "Vietnamese",
            // Add more as needed
        )
    }
    
    /**
     * Detect language for the given text
     * @return language code (like "en", "fr", etc.) or null if detection failed
     */
    suspend fun detectLanguage(text: String): String? {
        return try {
            // Use Tasks.await with a timeout instead of await extension function
            val result = Tasks.await(
                languageIdentifier.identifyLanguage(text),
                10, // Timeout in seconds
                TimeUnit.SECONDS
            )
            
            if (result == "und") { // "undefined"
                Log.d(TAG, "Language detection failed or text too short")
                null
            } else {
                Log.d(TAG, "Detected language: $result")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting language: ${e.message}")
            null
        }
    }
    
    /**
     * Detect possible languages for the given text with confidence scores
     * @return list of language codes with confidence scores
     */
    suspend fun detectPossibleLanguages(text: String): List<Pair<String, Float>> {
        return try {
            // Use Tasks.await with a timeout
            val results = Tasks.await(
                languageIdentifier.identifyPossibleLanguages(text),
                10, // Timeout in seconds
                TimeUnit.SECONDS
            )
            
            // Convert results to a list of pairs
            results.map { languageResult ->
                Pair(languageResult.languageTag, languageResult.confidence)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting possible languages: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get a display name for a language code
     */
    fun getLanguageDisplayName(languageCode: String): String {
        // Split to get the primary language code (e.g., "en" from "en-US")
        val primaryCode = languageCode.split("-")[0]
        
        // Try to get the display name from our map
        val displayName = LANGUAGE_DISPLAY_NAMES[primaryCode]
        if (displayName != null) {
            return displayName
        }
        
        // Fall back to Java's Locale system
        return try {
            val locale = Locale.forLanguageTag(languageCode)
            locale.displayLanguage
        } catch (e: Exception) {
            languageCode // Just return the code if all else fails
        }
    }
    
    /**
     * Release resources
     */
    fun close() {
        languageIdentifier.close()
        instance = null
    }
}
