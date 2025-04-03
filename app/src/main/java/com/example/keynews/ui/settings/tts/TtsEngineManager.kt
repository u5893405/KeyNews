package com.example.keynews.ui.settings.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import com.example.keynews.databinding.FragmentSettingsBinding
import java.util.Locale

/**
 * Manages TTS engines in the settings UI.
 */
class TtsEngineManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : TextToSpeech.OnInitListener {
    private val TAG = "TtsEngineManager"
    
    // TTS engine state
    private var tts: TextToSpeech? = null
    private val ttsEngines = mutableListOf<TextToSpeech.EngineInfo>()
    private var currentTtsEngine = ""
    private var isTtsInitialized = false
    private var isChangingEngine = false
    private var isProcessingInit = false // Flag to prevent recursive initialization
    
    // Maps for language and voice selection
    private val availableLanguages = mutableMapOf<String, Locale>() // Display name to Locale
    private val availableVoices = mutableMapOf<String, Voice>()     // Display name to Voice
    
    // Language-voice preference UI manager
    private lateinit var languageVoicePreferenceUi: LanguageVoicePreferenceUi
    
    // Callback object from the fragment
    private var initCallback: TextToSpeech.OnInitListener? = null
    
    // Fragment binding
    private lateinit var binding: FragmentSettingsBinding
    private var userSelectedEngine: String? = null
    
    /**
     * Required implementation of TextToSpeech.OnInitListener interface
     */
    override fun onInit(status: Int) {
        Log.d(TAG, "TextToSpeech.OnInitListener.onInit callback with status: $status")
        onTtsInit(status)
    }
    
    /**
     * Update the current engine name
     */
    private fun updateCurrentEngine(engineName: String) {
        Log.d(TAG, "⏸ ENGINE CHANGE: from '$currentTtsEngine' to '$engineName'")
        currentTtsEngine = engineName
        
        // Notify the language-voice UI manager of the engine change
        if (::languageVoicePreferenceUi.isInitialized) {
            Log.d(TAG, "⏸ ENGINE CHANGE: Notifying language-voice UI manager")
            languageVoicePreferenceUi.updateCurrentEngine(currentTtsEngine)
        }
    }
    
    /**
     * Initialize TTS engine
     */
    fun initializeTts(callback: TextToSpeech.OnInitListener): TextToSpeech {
        Log.d(TAG, "Initializing TTS engine")
        this.initCallback = callback
        
        // Check if TTS is already initialized - if so, shut it down first
        if (tts != null) {
            Log.d(TAG, "Shutting down existing TTS instance before reinitializing")
            tts?.shutdown()
            tts = null
        }
        
        // Create new TTS instance
        tts = TextToSpeech(context, callback)
        return tts!!
    }
    
    /**
     * Set up TTS settings UI components
     */
    fun setupTtsSettings(binding: FragmentSettingsBinding, prefs: android.content.SharedPreferences) {
        this.binding = binding
        
        // Setup speed slider
        binding.seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speechRate = TtsSettingsHelper.progressToSpeechRate(progress)
                    binding.tvSpeedValue.text = String.format("%.1f", speechRate)
                    
                    if (isTtsInitialized) {
                        tts?.setSpeechRate(speechRate)
                        // Save the setting
                        prefs.edit().putFloat(TtsSettingsHelper.KEY_TTS_SPEECH_RATE, speechRate).apply()
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Setup pitch slider
        binding.seekBarPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val pitch = TtsSettingsHelper.progressToPitch(progress)
                    binding.tvPitchValue.text = String.format("%.1f", pitch)
                    
                    if (isTtsInitialized) {
                        tts?.setPitch(pitch)
                        // Save the setting
                        prefs.edit().putFloat(TtsSettingsHelper.KEY_TTS_PITCH, pitch).apply()
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    /**
     * Forward onTtsInit to handle TTS initialization
     * This is called from the fragment's onInit callback
     */
    fun onTtsInit(status: Int) {
        Log.d(TAG, "TTS initialization status: $status, isChangingEngine: $isChangingEngine")
        
        // Check if we're already processing initialization to prevent recursion
        if (isProcessingInit) {
            Log.d(TAG, "Already processing initialization, returning to avoid recursion")
            return
        }
        
        isProcessingInit = true
        
        try {
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                
                // Clear the container first to ensure no rows are displayed initially
                if (::binding.isInitialized) {
                    binding.languageVoiceContainer.removeAllViews()
                }
                
                // Update the current engine name
                val prefs = context.getSharedPreferences(TtsSettingsHelper.PREFS_NAME, Context.MODE_PRIVATE)
                val savedEngine = prefs.getString(TtsSettingsHelper.KEY_TTS_ENGINE, "") ?: ""
                updateCurrentEngine(if (userSelectedEngine?.isNotEmpty() == true) userSelectedEngine!! else savedEngine)
                
                // Set up the UI components
                if (::binding.isInitialized) {
                    Log.d(TAG, "Setting up TTS engine spinner")
                    
                    // Load available engines if empty
                    if (ttsEngines.isEmpty()) {
                        ttsEngines.addAll(tts?.engines ?: emptyList())
                    }
                    
                    // Step 1: Initialize languages and voices if needed (expensive operation)
                    if (availableLanguages.isEmpty() || availableVoices.isEmpty()) {
                        loadAvailableLanguagesAndVoices()
                    }
                    
                    // Step 2: Set up the UI manager
                    if (!::languageVoicePreferenceUi.isInitialized) {
                        languageVoicePreferenceUi = LanguageVoicePreferenceUi(
                            context,
                            binding.languageVoiceContainer,
                            availableLanguages,
                            availableVoices,
                            currentTtsEngine,
                            tts
                        )
                    } else {
                        // Update UI manager with latest languages and voices
                        languageVoicePreferenceUi.updateAvailableVoicesAndLanguages(availableLanguages, availableVoices)
                    }
                    
                    // Step 3: Set up the spinner without callbacks to avoid triggering more events
                    setupEngineSpinnerNoCallbacks()
                    
                    // Step 4: Apply settings
                    Log.d(TAG, "Applying TTS settings")
                    applySettings()
                    
                    // Step 5: Load language-voice preferences specific to this engine
                    if (::languageVoicePreferenceUi.isInitialized) {
                        languageVoicePreferenceUi.loadPreferences()
                    }
                    
                    // Step 6: Set up the spinner callbacks now that everything else is initialized
                    binding.spinnerTtsEngine.post {
                        setupTtsEngineSpinner()
                    }
                }
            } else {
                Toast.makeText(context, "Failed to initialize TTS engine", Toast.LENGTH_SHORT).show()
            }
        } finally {
            isProcessingInit = false
        }
    }
    
    /**
     * Load available languages and voices for the current TTS engine
     */
    private fun loadAvailableLanguagesAndVoices() {
        Log.d(TAG, "⚙ Loading languages and voices for engine ${tts?.defaultEngine}")
        
        // Clear existing data
        availableLanguages.clear()
        availableVoices.clear()
        
        // Get available languages
        val locales = tts?.availableLanguages ?: emptySet()
        for (locale in locales) {
            val displayName = locale.displayName
            availableLanguages[displayName] = locale
        }
        
        // If no languages were found, add device default
        if (availableLanguages.isEmpty()) {
            val defaultLocale = Locale.getDefault()
            availableLanguages[defaultLocale.displayName] = defaultLocale
        }
        
        // Get available voices
        val voices = tts?.voices ?: emptySet()
        for (voice in voices) {
            val displayName = "${voice.name} (${voice.locale.displayLanguage})"
            availableVoices[displayName] = voice
        }
        
        // Log voice information
        Log.d(TAG, "⚙ Available languages: ${availableLanguages.size}")
        Log.d(TAG, "⚙ Available voices: ${availableVoices.size}")
        
        // Update the UI if language-voice preference UI is initialized
        if (::languageVoicePreferenceUi.isInitialized) {
            languageVoicePreferenceUi.updateAvailableVoicesAndLanguages(availableLanguages, availableVoices)
        }
    }
    
    /**
     * Setup the TTS engine spinner without adding callbacks
     */
    private fun setupEngineSpinnerNoCallbacks() {
        if (!::binding.isInitialized || ttsEngines.isEmpty()) return
        
        Log.d(TAG, "Setting up TTS engine spinner (no callbacks)")
        
        val engineNames = ttsEngines.map { it.label }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, engineNames)
        binding.spinnerTtsEngine.adapter = adapter
        
        // Get saved engine setting
        val prefs = context.getSharedPreferences(TtsSettingsHelper.PREFS_NAME, Context.MODE_PRIVATE)
        val savedEngine = prefs.getString(TtsSettingsHelper.KEY_TTS_ENGINE, null)
        
        // Find the index of the saved engine
        if (savedEngine != null) {
            val engineIndex = ttsEngines.indexOfFirst { it.name == savedEngine }
            if (engineIndex >= 0) {
                binding.spinnerTtsEngine.setSelection(engineIndex, false)
                updateCurrentEngine(savedEngine)
            }
        }
    }
    
    /**
     * Setup the TTS engine spinner with callbacks
     */
    private fun setupTtsEngineSpinner() {
        if (!::binding.isInitialized || ttsEngines.isEmpty()) return
        
        Log.d(TAG, "Setting up TTS engine spinner with callbacks")
        
        // Set up the callbacks separately - we already set up the spinner in setupEngineSpinnerNoCallbacks
        binding.spinnerTtsEngine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Skip if we're already processing initialization or changing engines
                if (isProcessingInit || isChangingEngine) {
                    Log.d(TAG, "Skipping onItemSelected while processing init or changing engines")
                    return
                }
                
                // Make sure position is valid
                if (position < 0 || position >= ttsEngines.size) {
                    Log.d(TAG, "Invalid position $position for engine selection")
                    return
                }
                
                val selectedEngine = ttsEngines[position].name
                userSelectedEngine = selectedEngine
                
                // Even if it's the same engine, we'll reinitialize to ensure proper loading of voices
                Log.d(TAG, "⏸ ENGINE CHANGE: Selected engine: $selectedEngine")
                
                // Set flag that we're changing engines to prevent recursive calls
                isChangingEngine = true
                
                try {
                    // Save the engine preference first
                    val prefs = context.getSharedPreferences(TtsSettingsHelper.PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(TtsSettingsHelper.KEY_TTS_ENGINE, selectedEngine).apply()
                    
                    // CRITICAL: Always clear the container first
                    binding.languageVoiceContainer.removeAllViews()
                    
                    // Create a new TTS instance with the selected engine
                    val oldTts = tts
                    tts = TextToSpeech(context, TextToSpeech.OnInitListener { status ->
                        Log.d(TAG, "⏸ ENGINE CHANGE: Initialization callback with status: $status")
                        if (status == TextToSpeech.SUCCESS) {
                            try {
                                // Update the engine name
                                //updateCurrentEngine(tts?.defaultEngine ?: "")
                                updateCurrentEngine(selectedEngine)
                                Log.d(TAG, "⏸ ENGINE CHANGE: Successfully initialized: $currentTtsEngine")
                                
                                // Load available languages and voices for this engine
                                loadAvailableLanguagesAndVoices()
                                
                                // Apply TTS settings (pitch, rate, etc.)
                                applySettings()
                                
                                // Load any saved preferences for this engine
                                if (::languageVoicePreferenceUi.isInitialized) {
                                    languageVoicePreferenceUi.loadPreferences()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "⏸ ENGINE CHANGE: Error during initialization: ${e.message}")
                            }
                        } else {
                            Log.e(TAG, "⏸ ENGINE CHANGE: Failed to initialize engine")
                            Toast.makeText(context, "Failed to initialize new TTS engine", Toast.LENGTH_SHORT).show()
                        }
                        
                        // Reset the flag
                        isChangingEngine = false
                    }, selectedEngine)
                    
                    // Shutdown the old TTS engine
                    oldTts?.shutdown()
                    
                    // Show a toast indicating the change
                    Toast.makeText(
                        context,
                        "Switching to ${ttsEngines[position].label}",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "⏸ ENGINE CHANGE: Error: ${e.message}")
                    Toast.makeText(context, "Error switching TTS engine", Toast.LENGTH_SHORT).show()
                    isChangingEngine = false
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    /**
     * Apply saved TTS settings
     */
    private fun applySettings() {
        if (!::binding.isInitialized) return
        
        Log.d(TAG, "Applying TTS settings")
        
        val prefs = context.getSharedPreferences(TtsSettingsHelper.PREFS_NAME, Context.MODE_PRIVATE)
        
        // Apply speech rate
        val speechRate = prefs.getFloat(TtsSettingsHelper.KEY_TTS_SPEECH_RATE, TtsSettingsHelper.DEFAULT_SPEECH_RATE)
        tts?.setSpeechRate(speechRate)
        val speedProgress = TtsSettingsHelper.speechRateToProgress(speechRate)
        binding.seekBarSpeed.progress = speedProgress
        binding.tvSpeedValue.text = String.format("%.1f", speechRate)
        
        // Apply pitch
        val pitch = prefs.getFloat(TtsSettingsHelper.KEY_TTS_PITCH, TtsSettingsHelper.DEFAULT_PITCH)
        tts?.setPitch(pitch)
        val pitchProgress = TtsSettingsHelper.pitchToProgress(pitch)
        binding.seekBarPitch.progress = pitchProgress
        binding.tvPitchValue.text = String.format("%.1f", pitch)
    }
    
    /**
     * Add a new language-voice preference
     */
    fun addLanguageVoicePreference() {
        if (isTtsInitialized && ::languageVoicePreferenceUi.isInitialized) {
            languageVoicePreferenceUi.addRow()
        }
    }
    
    /**
     * Save language-voice preferences
     */
    fun saveLanguageVoicePreferences() {
        if (isTtsInitialized && ::languageVoicePreferenceUi.isInitialized) {
            languageVoicePreferenceUi.saveAllPreferences()
        }
    }
    
    /**
     * Test TTS with current settings
     */
    fun testTts(text: String) {
        if (isTtsInitialized && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TEST_UTTERANCE")
        }
    }

    /**
     * Test TTS with a specific language and voice
     */
    fun testTtsWithSelectedVoice(text: String, languageName: String, voiceName: String) {
        if (isTtsInitialized && tts != null && ::languageVoicePreferenceUi.isInitialized) {
            try {
                // Find the locale and voice objects
                val locale = availableLanguages[languageName]
                val voice = availableVoices[voiceName]
                
                if (locale != null && voice != null) {
                    languageVoicePreferenceUi.testVoice(locale, voice, text)
                } else {
                    Log.d(TAG, "🔊 Could not find voice or language: $languageName, $voiceName")
                    // Fallback to default
                    testTts(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "🔊 Error testing voice: ${e.message}")
                testTts(text)
            }
        }
    }
    
    /**
     * Test TTS with the currently selected language and voice from the UI
     */
    fun testTtsWithCurrentSelection(text: String) {
        if (isTtsInitialized && ::languageVoicePreferenceUi.isInitialized) {
            try {
                val selection = languageVoicePreferenceUi.getSelectedLanguageAndVoice()
                if (selection != null) {
                    val (locale, voice) = selection
                    languageVoicePreferenceUi.testVoice(locale, voice, text)
                } else {
                    testTts(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error testing with current selection: ${e.message}")
                testTts(text)
            }
        } else {
            testTts(text)
        }
    }

    
    /**
     * Check if TTS is initialized
     */
    fun isInitialized(): Boolean = isTtsInitialized
    
    /**
     * Get current TTS engine
     */
    fun getCurrentEngine(): String = currentTtsEngine
    
    /**
     * Shutdown TTS
     */
    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
