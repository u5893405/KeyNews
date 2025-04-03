package com.example.keynews.data.model

import android.util.Log
import java.util.Locale

/**
 * Represents a language-voice preference for a specific TTS engine
 */
data class LanguageVoicePreference(
    val languageCode: String,        // Language code (e.g., "en-US", "fr-FR")
    val voiceName: String,           // Voice name/identifier
    val displayName: String = ""     // Display name for UI
)

/**
 * Container for all TTS preferences for a specific engine
 */
data class TtsEnginePreferences(
    val engineName: String,                          // TTS engine package name
    val languageVoicePreferences: List<LanguageVoicePreference> = emptyList()
)

/**
 * Container for all TTS preferences across different engines
 */
data class TtsPreferences(
    val enginePreferences: Map<String, TtsEnginePreferences> = emptyMap()
)

/**
 * Helper class to manage TTS preferences
 */
object TtsPreferenceManager {
    private const val PREFS_NAME = "keynews_settings"
    private const val KEY_TTS_PREFERENCES = "tts_lang_voice_preferences"
    private const val DEBUG_PREFIX = "TTS_PREF_MANAGER"
    
    /**
     * Save TTS preferences to SharedPreferences
     */
    fun savePreferences(context: android.content.Context, prefs: TtsPreferences) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val json = gson.toJson(prefs)
        sharedPrefs.edit().putString(KEY_TTS_PREFERENCES, json).apply()
        
        // Log which engines have preferences
        val engineNames = prefs.enginePreferences.keys.joinToString(", ")
        Log.d(DEBUG_PREFIX, "SAVED PREFERENCES for engines: $engineNames")
        
        // Log count of preferences for each engine
        prefs.enginePreferences.forEach { (engine, enginePrefs) ->
            Log.d(DEBUG_PREFIX, "Engine '$engine' now has ${enginePrefs.languageVoicePreferences.size} saved preferences")
        }
    }
    
    /**
     * Save preferences for a specific engine
     */
    fun saveEnginePreferences(
        context: android.content.Context,
        engineName: String,
        preferences: List<LanguageVoicePreference>
    ) {
        Log.d(DEBUG_PREFIX, "SAVING ${preferences.size} PREFERENCES FOR ENGINE: '$engineName'")
        
        // Load current preferences for all engines
        val allPrefs = loadPreferences(context)
        
        // Create a completely new map to avoid any reference issues
        val updatedEnginePrefs = mutableMapOf<String, TtsEnginePreferences>()
        
        // Copy preferences for all other engines
        for ((existingEngine, enginePrefs) in allPrefs.enginePreferences) {
            if (existingEngine != engineName) {
                updatedEnginePrefs[existingEngine] = enginePrefs
                Log.d(DEBUG_PREFIX, "Preserved ${enginePrefs.languageVoicePreferences.size} preferences for engine: '$existingEngine'")
            }
        }
        
        // Create a new preference object for the current engine
        val newEnginePrefs = TtsEnginePreferences(
            engineName = engineName,
            languageVoicePreferences = preferences
        )
        
        // Add the updated current engine preferences
        updatedEnginePrefs[engineName] = newEnginePrefs
        
        // Create a completely new TtsPreferences object with the updated map
        val newAllPrefs = TtsPreferences(updatedEnginePrefs)
        
        // Save all preferences to storage
        savePreferences(context, newAllPrefs)
        
        Log.d(DEBUG_PREFIX, "SUCCESSFULLY SAVED ${preferences.size} PREFERENCES FOR ENGINE: '$engineName'")
    }
    
    /**
     * Load TTS preferences from SharedPreferences
     */
    fun loadPreferences(context: android.content.Context): TtsPreferences {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val json = sharedPrefs.getString(KEY_TTS_PREFERENCES, null)
        
        val result = if (json != null) {
            try {
                gson.fromJson(json, TtsPreferences::class.java)
            } catch (e: Exception) {
                Log.e(DEBUG_PREFIX, "Error parsing preferences: ${e.message}")
                TtsPreferences()
            }
        } else {
            Log.d(DEBUG_PREFIX, "No preferences found in storage, using empty preferences")
            TtsPreferences()
        }
        
        // Log which engines have preferences
        if (result.enginePreferences.isNotEmpty()) {
            val engineNames = result.enginePreferences.keys.joinToString(", ")
            Log.d(DEBUG_PREFIX, "LOADED preferences for engines: $engineNames")
            
            // Log count of preferences for each engine
            result.enginePreferences.forEach { (engine, enginePrefs) ->
                Log.d(DEBUG_PREFIX, "Engine '$engine' has ${enginePrefs.languageVoicePreferences.size} preferences")
            }
        } else {
            Log.d(DEBUG_PREFIX, "No saved preferences found for any engine")
        }
        
        return result
    }
    
    /**
     * Get the best voice for a given language based on the saved preferences
     */
    fun getBestVoiceForLanguage(
        context: android.content.Context, 
        engineName: String, 
        languageCode: String
    ): LanguageVoicePreference? {
        val prefs = loadPreferences(context)
        val enginePrefs = prefs.enginePreferences[engineName] ?: return null
        
        // Try to find exact language match first
        val exactMatch = enginePrefs.languageVoicePreferences.firstOrNull { 
            it.languageCode.equals(languageCode, ignoreCase = true) 
        }
        
        if (exactMatch != null) return exactMatch
        
        // Try to find a match with just the primary language (e.g., "en" for "en-US")
        val primaryLangCode = languageCode.split("-")[0]
        return enginePrefs.languageVoicePreferences.firstOrNull { 
            it.languageCode.split("-")[0].equals(primaryLangCode, ignoreCase = true)
        }
    }
    
    /**
     * Get preferences for a specific TTS engine
     */
    fun getPreferencesForEngine(
        context: android.content.Context,
        engineName: String
    ): List<LanguageVoicePreference> {
        val prefs = loadPreferences(context)
        val enginePrefs = prefs.enginePreferences[engineName]
        
        if (enginePrefs == null) {
            Log.d(DEBUG_PREFIX, "NO SAVED PREFERENCES found for engine: '$engineName'")
            return emptyList()
        }
        
        Log.d(DEBUG_PREFIX, "FOUND ${enginePrefs.languageVoicePreferences.size} preferences for engine: '$engineName'")
        return enginePrefs.languageVoicePreferences
    }
    
    /**
     * Clear all preferences for an engine or all engines
     */
    fun clearPreferences(context: android.content.Context, engineName: String? = null) {
        if (engineName == null) {
            // Clear all preferences
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            sharedPrefs.edit().remove(KEY_TTS_PREFERENCES).apply()
            Log.d(DEBUG_PREFIX, "CLEARED ALL preferences for all engines")
        } else {
            // Clear preferences for specific engine
            val allPrefs = loadPreferences(context)
            val updatedMap = allPrefs.enginePreferences.toMutableMap()
            updatedMap.remove(engineName)
            savePreferences(context, TtsPreferences(updatedMap))
            Log.d(DEBUG_PREFIX, "CLEARED ALL preferences for engine: '$engineName'")
        }
    }
}