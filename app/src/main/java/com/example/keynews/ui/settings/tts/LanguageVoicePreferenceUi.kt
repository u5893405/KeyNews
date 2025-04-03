package com.example.keynews.ui.settings.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import com.example.keynews.R
import com.example.keynews.data.model.LanguageVoicePreference
import com.example.keynews.data.model.TtsPreferenceManager
import java.util.Locale

/**
 * Manages the UI for language-voice preferences.
 */
class LanguageVoicePreferenceUi(
    private val context: Context,
    private val containerView: LinearLayout,
    availableLanguages: Map<String, Locale>,
    availableVoices: Map<String, Voice>,
    private var currentTtsEngine: String,
    private val tts: TextToSpeech?
) {
    private val TAG = "LangVoicePrefUI"
    
    // Map references for available languages and voices for the current engine
    private val languages = availableLanguages.toMutableMap()
    private val voices = availableVoices.toMutableMap()
    
    init {
        // Ensure we start with an empty container
        containerView.removeAllViews()
        Log.d(TAG, "Created new instance with engine: '$currentTtsEngine'")
    }
    
    /**
     * Update the current engine name
     */
    fun updateCurrentEngine(engineName: String) {
        Log.d(TAG, "Engine change: from '$currentTtsEngine' to '$engineName'")
        this.currentTtsEngine = engineName
        // Clear the container synchronously
        containerView.removeAllViews()
        Log.d(TAG, "Cleared preference rows for engine change")
        // Reload preferences for the new engine (this will add rows only if saved preferences exist)
        loadPreferences()
    }
    
    /**
     * Update the available voices and languages
     */
    fun updateAvailableVoicesAndLanguages(newLanguages: Map<String, Locale>, newVoices: Map<String, Voice>) {
        Log.d(TAG, "⚙ Updating available languages (${newLanguages.size}) and voices (${newVoices.size})")
        
        // Update our local copies
        languages.clear()
        languages.putAll(newLanguages)
        
        voices.clear()
        voices.putAll(newVoices)
    }
    
    /**
     * Load saved preferences for the current engine
     */
    fun loadPreferences() {
        Log.d(TAG, "📋 LOADING PREFERENCES FOR ENGINE: '$currentTtsEngine'")
        
        // CRITICAL: Always ensure container is empty first
        containerView.removeAllViews()
        Log.d(TAG, "📋 Cleared all existing rows first")
        
        // Get preferences ONLY for this specific engine
        val enginePrefs = TtsPreferenceManager.getPreferencesForEngine(context, currentTtsEngine)
        
        if (enginePrefs.isEmpty()) {
            Log.d(TAG, "📋 NO SAVED PREFERENCES for engine: '$currentTtsEngine', container will remain empty")
            return
        }
        
        Log.d(TAG, "📋 FOUND ${enginePrefs.size} SAVED PREFERENCES FOR ENGINE: '$currentTtsEngine'")
        
        // Add rows for each preference for this specific engine
        enginePrefs.forEach { pref ->
            Log.d(TAG, "📋 Adding row for: ${pref.displayName}")
            addRow(pref)
        }
    }
    
    /**
     * Add a new language-voice preference row to the UI
     */
    fun addRow(existingPreference: LanguageVoicePreference? = null) {
        if (tts == null) return
        
        Log.d(TAG, "➕ Adding new preference row for engine: '$currentTtsEngine'")
        
        // Inflate the row layout
        val inflater = LayoutInflater.from(context)
        val rowView = inflater.inflate(R.layout.item_language_voice, containerView, false)
        
        // Get references to the views
        val languageSpinner = rowView.findViewById<Spinner>(R.id.spinnerLanguage)
        val voiceSpinner = rowView.findViewById<Spinner>(R.id.spinnerVoice)
        val removeButton = rowView.findViewById<ImageButton>(R.id.btnRemove)
        
        // Set up language spinner
        val languageNames = languages.keys.toList().sorted()
        val languageAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languageNames)
        languageSpinner.adapter = languageAdapter
        
        // Set initial language selection if provided
        var initialLanguage: Locale? = null
        if (existingPreference != null) {
            val langCode = existingPreference.languageCode
            initialLanguage = languages.values.firstOrNull { it.toLanguageTag() == langCode } 
                ?: Locale.forLanguageTag(langCode)
            
            // Find the display name for this language
            val displayName = languages.entries.firstOrNull { it.value.toLanguageTag() == langCode }?.key
                ?: initialLanguage?.displayName ?: "Unknown"
            
            val index = languageNames.indexOf(displayName)
            if (index >= 0) {
                languageSpinner.setSelection(index)
            }
        }
        
        // Set up voice spinner based on selected language
        fun updateVoiceSpinner(selectedLocale: Locale?) {
            if (selectedLocale == null) return
            
            // Filter voices for the selected language
            val filteredVoices = voices.filter { voice ->
                voice.value.locale.language == selectedLocale.language
            }
            
            // Set up the spinner
            val voiceNames = filteredVoices.keys.toList().sorted()
            val voiceAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, voiceNames)
            voiceSpinner.adapter = voiceAdapter
            
            // Set initial voice selection if provided
            if (existingPreference != null) {
                val voiceName = existingPreference.voiceName
                val displayName = filteredVoices.entries.firstOrNull { it.value.name == voiceName }?.key
                
                if (displayName != null) {
                    val index = voiceNames.indexOf(displayName)
                    if (index >= 0) {
                        voiceSpinner.setSelection(index)
                    }
                }
            }
        }
        
        // Initial voice spinner setup
        updateVoiceSpinner(initialLanguage ?: languages.values.firstOrNull())
        
        // Language selection listener
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val languageName = languageNames[position]
                val locale = languages[languageName]
                updateVoiceSpinner(locale)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Voice selection listener - no action needed (save done separately)
        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {}
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Remove button listener - simply removes from UI
        removeButton.setOnClickListener {
            containerView.removeView(rowView)
            Log.d(TAG, "➖ Removed preference row from UI (not saved yet)")
        }
        
        // Add the row to the container
        containerView.addView(rowView)
    }
    
    /**
     * Save all language-voice preferences currently in the UI
     */
    fun saveAllPreferences() {
        Log.d(TAG, "💾 SAVING PREFERENCES FOR ENGINE: '$currentTtsEngine'")
        
        // Create a new list of preferences for this engine only
        val newPreferences = mutableListOf<LanguageVoicePreference>()
        
        // Loop through all rows in the container and collect preferences
        for (i in 0 until containerView.childCount) {
            val rowView = containerView.getChildAt(i)
            val languageSpinner = rowView.findViewById<Spinner>(R.id.spinnerLanguage)
            val voiceSpinner = rowView.findViewById<Spinner>(R.id.spinnerVoice)
            
            if (languageSpinner.selectedItem != null && voiceSpinner.selectedItem != null) {
                val languageName = languageSpinner.selectedItem.toString()
                val voiceName = voiceSpinner.selectedItem.toString()
                
                val locale = languages[languageName] ?: continue
                val voice = voices[voiceName] ?: continue
                
                // Create the preference
                val preference = LanguageVoicePreference(
                    languageCode = locale.toLanguageTag(),
                    voiceName = voice.name,
                    displayName = "${locale.displayLanguage} (${voice.name})"
                )
                
                Log.d(TAG, "💾 Adding preference to save: ${preference.displayName}")
                newPreferences.add(preference)
            }
        }
        
        // Save preferences for this engine only
        TtsPreferenceManager.saveEnginePreferences(context, currentTtsEngine, newPreferences)
        
        Toast.makeText(context, "Language-voice preferences saved for $currentTtsEngine", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Test a specific language and voice
     */
    fun testVoice(locale: Locale, voice: Voice, text: String) {
        if (tts == null) return
        
        Log.d(TAG, "🔊 Testing voice: ${voice.name} for language: ${locale.displayName}")
        
        try {
            // Store current language and voice
            val currentLocale = tts.language
            val currentVoice = tts.voice
            
            // Set the selected language and voice
            tts.language = locale
            tts.voice = voice
            
            // Speak the text
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TEST_VOICE_UTTERANCE")
            
            // Restore original language and voice after a short delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tts.language = currentLocale
                tts.voice = currentVoice
            }, 3000) // 3 seconds delay
        } catch (e: Exception) {
            Log.e(TAG, "🔊 Error testing voice: ${e.message}")
        }
    }
    
    /**
     * Get the currently selected language and voice from the first preference row
     * Returns null if no rows exist or selections are invalid
     */
    fun getSelectedLanguageAndVoice(): Pair<Locale, Voice>? {
        if (containerView.childCount == 0) return null
            try {
                val firstRow = containerView.getChildAt(0)
                val languageSpinner = firstRow.findViewById<Spinner>(R.id.spinnerLanguage)
                val voiceSpinner = firstRow.findViewById<Spinner>(R.id.spinnerVoice)
                val languageItem = languageSpinner?.selectedItem
                val voiceItem = voiceSpinner?.selectedItem
                if (languageItem != null && voiceItem != null) {
                    val languageName = languageItem.toString()
                    val voiceName = voiceItem.toString()
                    val locale = languages[languageName]
                    val voice = voices[voiceName]
                    Log.d(TAG, "Selected language: $languageName (locale: $locale), voice: $voiceName ($voice)")
                    // Validate the voice against the current TTS voices list
                    if (locale != null && voice != null && tts?.voices?.contains(voice) == true) {
                        return Pair(locale, voice)
                    } else {
                        Log.d(TAG, "Invalid selection, falling back to default TTS")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting selected language and voice: ${e.message}")
            }
            return null
    }

}
