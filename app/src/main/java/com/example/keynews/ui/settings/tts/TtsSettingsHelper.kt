package com.example.keynews.ui.settings.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.example.keynews.data.model.TtsPreferenceManager
import java.util.Locale

/**
 * Helper class for TTS settings.
 * Contains utility methods for TTS settings management.
 */
class TtsSettingsHelper {
    companion object {
        // Constants for TTS settings
        const val PREFS_NAME = "keynews_settings"
        const val KEY_TTS_ENGINE = "tts_engine"
        const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        const val KEY_TTS_PITCH = "tts_pitch"
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val DEFAULT_PITCH = 1.0f
        
        /**
         * Convert slider progress to speech rate
         * @param progress Slider progress (0-100)
         * @return Speech rate (0.5-2.0)
         */
        fun progressToSpeechRate(progress: Int): Float {
            // Convert 0-100 to 0.5-2.0
            return 0.5f + (1.5f * progress / 100f)
        }
        
        /**
         * Convert speech rate to slider progress
         * @param speechRate Speech rate (0.5-2.0)
         * @return Slider progress (0-100)
         */
        fun speechRateToProgress(speechRate: Float): Int {
            // Convert 0.5-2.0 to 0-100
            return ((speechRate - 0.5f) * 100f / 1.5f).toInt().coerceIn(0, 100)
        }
        
        /**
         * Convert slider progress to pitch
         * @param progress Slider progress (0-100)
         * @return Pitch value (0.5-2.0)
         */
        fun progressToPitch(progress: Int): Float {
            // Convert 0-100 to 0.5-2.0
            return 0.5f + (1.5f * progress / 100f)
        }
        
        /**
         * Convert pitch to slider progress
         * @param pitch Pitch value (0.5-2.0)
         * @return Slider progress (0-100)
         */
        fun pitchToProgress(pitch: Float): Int {
            // Convert 0.5-2.0 to 0-100
            return ((pitch - 0.5f) * 100f / 1.5f).toInt().coerceIn(0, 100)
        }
        
        /**
         * Test TTS with selected language and voice
         * @param tts TextToSpeech instance
         * @param locale Language locale
         * @param voice Voice to use
         */
        fun testTtsVoice(
            context: Context,
            tts: TextToSpeech?, 
            locale: Locale, 
            voice: android.speech.tts.Voice,
            testText: String = "This is a test of the selected voice."
        ) {
            if (tts == null) {
                Toast.makeText(context, "TTS not initialized", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Configure TTS for this test
            tts.language = locale
            tts.voice = voice
            
            // Speak a test phrase
            tts.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "TEST_VOICE")
        }
        
        // Flag to prevent recursive application of settings
        private var isApplyingSettings = false
        
        /**
         * Apply TTS settings (speech rate and pitch)
         * @param tts TextToSpeech instance
         * @param context Context for preferences
         */
        fun applyTtsSettings(tts: TextToSpeech?, context: Context) {
            if (tts == null) return
            
            // Guard against recursive calls
            if (isApplyingSettings) {
                android.util.Log.d("TtsSettingsHelper", "Already applying settings, skipping to avoid recursion")
                return
            }
            
            isApplyingSettings = true
            
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
                // Apply speech rate
                val speechRate = prefs.getFloat(KEY_TTS_SPEECH_RATE, DEFAULT_SPEECH_RATE)
                tts.setSpeechRate(speechRate)
                
                // Apply pitch
                val pitch = prefs.getFloat(KEY_TTS_PITCH, DEFAULT_PITCH)
                tts.setPitch(pitch)
            } finally {
                isApplyingSettings = false
            }
        }
    }
}
