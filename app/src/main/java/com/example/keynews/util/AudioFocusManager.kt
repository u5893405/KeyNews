package com.example.keynews.util

// CodeCleaner_Start_ce83b95e-d528-4c9f-88d1-2e2e05517cf8
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages audio focus for TTS playback
 */
class AudioFocusManager(private val context: Context) {
    private val TAG = "AudioFocusManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

        // Listener to handle audio focus changes
        private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Lost focus for an extended or short period - pause playback
                    Log.d(TAG, "Audio focus lost")
                    audioFocusCallback?.onAudioFocusLost()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    // Gained focus - resume or start playback
                    Log.d(TAG, "Audio focus gained")
                    audioFocusCallback?.onAudioFocusGained()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Lost focus for a short time but can continue playback at lower volume
                    Log.d(TAG, "Audio focus loss transient can duck")
                    // We don't reduce volume for TTS as it's difficult to hear when ducked
                    audioFocusCallback?.onAudioFocusDucked()
                }
            }
        }

        // Callback interface for the focus manager owner to implement
        interface AudioFocusCallback {
            fun onAudioFocusGained()
            fun onAudioFocusLost()
            fun onAudioFocusDucked()
        }

        // Callback reference
        private var audioFocusCallback: AudioFocusCallback? = null

            // Set the callback
            fun setAudioFocusCallback(callback: AudioFocusCallback) {
                audioFocusCallback = callback
                Log.d(TAG, "Audio focus callback set")
            }

            /**
             * Request audio focus for TTS playback
             * @param interruptionAllowed Whether to fully interrupt other audio (true) or just duck it (false)
             * @return true if focus was granted, false otherwise
             */
            fun requestAudioFocus(interruptionAllowed: Boolean = false): Boolean {
                // Use appropriate focus type based on whether interruption is allowed
                val focusType = if (interruptionAllowed) {
                    // Completely interrupt other playback (for single article and manual sessions)
                    AudioManager.AUDIOFOCUS_GAIN
                } else {
                    // Just reduce volume of other playback (for repeated sessions if forced)
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                }

                Log.d(TAG, "Requesting audio focus with type: ${focusTypeToString(focusType)}, interruption allowed: $interruptionAllowed")

                // Return result of request
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8.0+ (API 26+) uses AudioFocusRequest
                    val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                    audioFocusRequest = AudioFocusRequest.Builder(focusType)
                    .setAudioAttributes(attributes)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(afChangeListener)
                    .build()

                    val requestResult = audioManager.requestAudioFocus(audioFocusRequest!!)
                    Log.d(TAG, "Modern audio focus request result: ${focusResultToString(requestResult)}")
                    requestResult
                } else {
                    // Legacy implementation for older Android versions
                    @Suppress("DEPRECATION")
                    val requestResult = audioManager.requestAudioFocus(
                        afChangeListener,
                        AudioManager.STREAM_MUSIC,
                        focusType
                    )
                    Log.d(TAG, "Legacy audio focus request result: ${focusResultToString(requestResult)}")
                    requestResult
                }

                return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }

            /**
             * Abandon audio focus when TTS playback is finished
             */
            fun abandonAudioFocus() {
                Log.d(TAG, "Abandoning audio focus")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let {
                        audioManager.abandonAudioFocusRequest(it)
                        Log.d(TAG, "Abandoned modern audio focus request")
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(afChangeListener)
                    Log.d(TAG, "Abandoned legacy audio focus")
                }
            }

            /**
             * Check if any audio is currently playing (music, media, etc.)
             * @return true if audio is playing, false otherwise
             */
            fun isAudioPlaying(): Boolean {
                val isPlaying = audioManager.isMusicActive
                Log.d(TAG, "Audio currently playing: $isPlaying")
                return isPlaying
            }

            /**
             * Check if a call is currently active
             * @return true if a call is active, false otherwise
             */
            fun isCallActive(): Boolean {
                // Check if a call is active using the telephony manager
                val mode = audioManager.mode
                val isInCall = when (mode) {
                    AudioManager.MODE_IN_CALL,
                    AudioManager.MODE_IN_COMMUNICATION,
                    AudioManager.MODE_RINGTONE -> true
                    else -> false
                }
                Log.d(TAG, "Call currently active: $isInCall (audio mode: ${audioModeToString(mode)})")
                return isInCall
            }

            /**
             * Checks if either music is playing or a call is active
             * @return true if either condition is true
             */
            fun isAudioOrCallActive(): Boolean {
                val audioPlaying = isAudioPlaying()
                val callActive = isCallActive()
                val result = audioPlaying || callActive
                Log.d(TAG, "Audio or call active: $result (audio: $audioPlaying, call: $callActive)")
                return result
            }

            /**
             * Convert audio focus type to string for logging
             */
            private fun focusTypeToString(focusType: Int): String {
                return when (focusType) {
                    AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
                    else -> "UNKNOWN($focusType)"
                }
            }

            /**
             * Convert audio focus result to string for logging
             */
            private fun focusResultToString(result: Int): String {
                return when (result) {
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "AUDIOFOCUS_REQUEST_GRANTED"
                    AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "AUDIOFOCUS_REQUEST_FAILED"
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "AUDIOFOCUS_REQUEST_DELAYED"
                    else -> "UNKNOWN($result)"
                }
            }

            /**
             * Convert audio mode to string for logging
             */
            private fun audioModeToString(mode: Int): String {
                return when (mode) {
                    AudioManager.MODE_NORMAL -> "MODE_NORMAL"
                    AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
                    AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
                    AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
                    AudioManager.MODE_CALL_SCREENING -> "MODE_CALL_SCREENING"
                    else -> "UNKNOWN($mode)"
                }
            }
}
// CodeCleaner_End_ce83b95e-d528-4c9f-88d1-2e2e05517cf8

