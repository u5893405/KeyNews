package com.example.keynews.service

// CodeCleaner_Start_d26a673c-dab1-42f8-ad2b-8fb8476c88f7
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.data.model.NewsArticle
import com.example.keynews.data.model.TtsPreferenceManager
import com.example.keynews.ui.settings.tts.TtsSettingsHelper
import com.example.keynews.util.AudioFocusManager
import com.example.keynews.util.LanguageDetector
import com.example.keynews.util.MediaNotificationManager
import com.example.keynews.util.TimeUtil
import com.example.keynews.ui.rep_session.RepeatedSessionScheduler
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine as suspendCancellableCoroutine
// CodeCleaner_End_d26a673c-dab1-42f8-ad2b-8fb8476c88f7


/**
 * TtsReadingService is a ForegroundService that reads news articles using TTS. It broadcasts
 * progress updates so the UI can reflect the currently reading article.
 */
class TtsReadingService : Service(), TextToSpeech.OnInitListener {

    private val TAG = "TtsReadingService"

    companion object {
        private const val NOTIF_CHANNEL_ID = "TTS_CHANNEL"
        private const val NOTIF_ID = 12345

        // Extras in the Intent that starts the Service
        const val EXTRA_FEED_ID = "extra_feed_id"
        const val EXTRA_HEADLINES_LIMIT = "extra_headlines_limit"
        const val EXTRA_READ_BODY = "extra_read_body"
        const val EXTRA_SINGLE_ARTICLE_LINK = "extra_single_article_link"
        const val EXTRA_DELAY_BETWEEN_HEADLINES = "extra_delay_between_headlines"
        const val EXTRA_SESSION_NAME = "extra_session_name"
        const val EXTRA_SESSION_TYPE = "extra_session_type"
        const val EXTRA_ARTICLE_AGE_THRESHOLD = "extra_article_age_threshold"
        const val EXTRA_ANNOUNCE_ARTICLE_AGE = "extra_announce_article_age"

        const val EXTRA_READING_MODE = "extra_reading_mode"

        // Session types
        const val SESSION_TYPE_SINGLE = "single"
        const val SESSION_TYPE_MANUAL = "manual"
        const val SESSION_TYPE_REPEATED = "repeated"
        const val SESSION_TYPE_SCHEDULED = "scheduled"

        // Broadcast Actions
        const val ACTION_READING_PROGRESS = "com.example.keynews.ACTION_READING_PROGRESS"
        const val ACTION_READING_DONE = "com.example.keynews.ACTION_READING_DONE"
        const val EXTRA_ARTICLE_LINK = "extra_article_link"
        const val ACTION_FEED_UPDATED = "com.example.keynews.ACTION_FEED_UPDATED"
        
        // Constants for read later feature
        const val EXTRA_FILTER_SOURCE_ID = "filter_source_id"
        const val EXTRA_FILTER_FEED_ID = "filter_feed_id"
        const val READING_TYPE_READ_LATER = "read_later"

        // New action to request reading progress
        const val ACTION_GET_READING_PROGRESS = "com.example.keynews.ACTION_GET_READING_PROGRESS"
        
        // Additional extras
        const val EXTRA_SHOW_UNREAD_ONLY = "show_unread_only"

        // Media control actions
        const val ACTION_STOP_TTS = "com.example.keynews.ACTION_STOP_TTS"
        const val ACTION_TOGGLE_PAUSE_TTS = "com.example.keynews.ACTION_TOGGLE_PAUSE_TTS"
        const val ACTION_FORCE_START_SESSION = "com.example.keynews.ACTION_FORCE_START_SESSION"
        private var wakeLock: PowerManager.WakeLock? = null

        // Global pause state
        @JvmStatic var isPaused = false
    }

    private var tts: TextToSpeech? = null

    // We'll use a job + scope for coroutines
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Store parameters from the start Intent
    private var feedId: Long = -1L
    private var headlinesLimit: Int = 10
    private var readBody: Boolean = false
    private var singleArticleLink: String? = null
    private var delayBetweenHeadlines: Int = 2 // Default to 2 seconds
    private var readingMode: String = "unread" // default for repeated sessions
    private var sessionName: String = "TTS Reading" // Default session name
    private var sessionType: String = SESSION_TYPE_MANUAL // Default session type
    private var articleAgeThresholdMinutes: Int? = null // Optional age threshold for filtering
    private var announceArticleAge: Boolean = false // Whether to announce article age before reading

    // Reading progress tracking
    private var currentArticleIndex: Int = 0
    private var totalArticlesToRead: Int = 0

    // Track whether TTS is initialized and whether we've started reading
    private var isTtsInitialized = false
    private var hasReadingStarted = false
    private var shouldSkipReading = false // Add flag to track if we should skip reading

    // Language detection
    private var languageDetector: LanguageDetector? = null

    // Audio focus and notification management
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var mediaNotificationManager: MediaNotificationManager

    // Pending intent parameters (stored if we need to retry due to audio conflict)
    private var pendingIntentParams: Bundle? = null

    // Current article being read
    private var currentArticle: NewsArticle? = null
    private var isReading = false

    // Media session for notification playback controls
    private lateinit var mediaSession: MediaSessionCompat

    // Flag to indicate if foreground has been started
    private var isForegroundStarted = false

    private var initialIntent: Intent? = null


    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "TtsReadingService.onCreate() - Starting service")

        // Initialize managers
        audioFocusManager = AudioFocusManager(applicationContext)
        mediaNotificationManager = MediaNotificationManager(applicationContext)

        // Set up audio focus callback
        audioFocusManager.setAudioFocusCallback(
                object : AudioFocusManager.AudioFocusCallback {
                    override fun onAudioFocusGained() {
                        // Resume TTS if we were paused
                        Log.d(TAG, "Audio focus gained callback")
                        if (isPaused) {
                            resumeTts()
                        }
                    }

                    override fun onAudioFocusLost() {
                        // Pause TTS if we're speaking
                        Log.d(TAG, "Audio focus lost callback")
                        if (isReading && !isPaused) {
                            pauseTts()
                        }
                    }

                    override fun onAudioFocusDucked() {
                        // Continue at lower volume (TTS automatically adjusts)
                        Log.d(TAG, "Audio focus ducked callback")
                    }
                }
        )

        // Create an initial notification and start foreground service
        val initialNotification = mediaNotificationManager.buildTtsNotification("Preparing TTS...")
        try {
            Log.d(TAG, "Starting foreground service with initial notification")
            startForeground(NOTIF_ID, initialNotification)
            isForegroundStarted = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
        }

        Log.d(TAG, "Initializing TTS engine")

        // Register receiver for progress requests
        val filter = IntentFilter(ACTION_GET_READING_PROGRESS)
        LocalBroadcastManager.getInstance(this).registerReceiver(progressRequestReceiver, filter)

        // Get saved engine setting
        val prefs =
                applicationContext.getSharedPreferences(
                        TtsSettingsHelper.PREFS_NAME,
                        Context.MODE_PRIVATE
                )
        val savedEngine = prefs.getString(TtsSettingsHelper.KEY_TTS_ENGINE, null)

        // Initialize TTS with saved engine if available, otherwise fallback to default
        try {
            if (savedEngine != null) {
                Log.d(TAG, "Using saved TTS engine: $savedEngine")
                tts = TextToSpeech(applicationContext, this, savedEngine)
            } else {
                // Try Google TTS first as default
                Log.d(TAG, "No saved engine, trying Google TTS")
                tts = TextToSpeech(applicationContext, this, "com.google.android.tts")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing specific TTS engine: ${e.message}", e)
            // Fallback to system default TTS engine
            Log.d(TAG, "Falling back to system default TTS engine")
            tts = TextToSpeech(applicationContext, this)
        }

        // Initialize language detector
        languageDetector = LanguageDetector.getInstance()

        // Initialize MediaSession
        mediaSession =
                MediaSessionCompat(this, "KeyNewsTtsMediaSession").apply {
                    setFlags(
                            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                    )

                    // Set callback for media controls
                    setCallback(
                            object : MediaSessionCompat.Callback() {
                                override fun onPlay() {
                                    Log.d(TAG, "MediaSession: onPlay() called")
                                    if (isPaused) resumeTts()
                                }

                                override fun onPause() {
                                    Log.d(TAG, "MediaSession: onPause() called")
                                    if (!isPaused) pauseTts()
                                }

                                override fun onStop() {
                                    Log.d(TAG, "MediaSession: onStop() called")
                                    stopSelf()
                                }
                            }
                    )

                    // Set initial playback state
                    val stateBuilder =
                            PlaybackStateCompat.Builder()
                                    .setActions(
                                            PlaybackStateCompat.ACTION_PLAY or
                                                    PlaybackStateCompat.ACTION_PAUSE or
                                                    PlaybackStateCompat.ACTION_STOP
                                    )
                                    .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f)
                    setPlaybackState(stateBuilder.build())

                    // Activate session
                    isActive = true
                    Log.d(TAG, "MediaSession initialized and activated")
                }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeyNews:TtsReadingService:WakeLock")
        wakeLock?.acquire(10 * 60 * 1000L) // acquire for 10 minutes; adjust as needed
        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        initialIntent = intent
        acquireWakeLock()
        Log.d(TAG, "onStartCommand() - Intent action: ${intent?.action}")
        
        // Reset the skip reading flag when a new command is received
        shouldSkipReading = false

        // Handle media control actions
        when (intent?.action) {
            ACTION_STOP_TTS -> {
                Log.d(TAG, "Received ACTION_STOP_TTS")
                // Update state to make sure we break out of any reading loops
                isReading = false
                isPaused = false
                hasReadingStarted = false

                // Stop TTS engine
                try {
                    tts?.stop()
                    Log.d(TAG, "TTS engine stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping TTS engine: ${e.message}")
                }

                // Release audio focus
                try {
                    audioFocusManager.abandonAudioFocus()
                    Log.d(TAG, "Audio focus abandoned")
                } catch (e: Exception) {
                    Log.e(TAG, "Error abandoning audio focus: ${e.message}")
                }

                // Update media session state
                try {
                    val stateBuilder =
                            PlaybackStateCompat.Builder()
                                    .setActions(PlaybackStateCompat.ACTION_PLAY)
                                    .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f)
                    mediaSession.setPlaybackState(stateBuilder.build())
                    Log.d(TAG, "Media session state updated to STOPPED")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating media session state: ${e.message}")
                }

                // Broadcast that we're done
                try {
                    broadcastDone()
                    Log.d(TAG, "Broadcast ACTION_READING_DONE sent")
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting reading done: ${e.message}")
                }

                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE_TTS -> {
                Log.d(TAG, "Received ACTION_TOGGLE_PAUSE_TTS, current pause state: $isPaused")
                if (isPaused) {
                    resumeTts()
                } else {
                    pauseTts()
                }
                return START_STICKY
            }
            ACTION_FORCE_START_SESSION -> {
                Log.d(TAG, "Received ACTION_FORCE_START_SESSION")
                // Force start the session that was previously stopped due to audio conflict
                pendingIntentParams?.let { bundle ->
                    val savedFeedId = bundle.getLong(EXTRA_FEED_ID, -1L)
                    if (savedFeedId != -1L) {
                        // We have valid parameters, restore them
                        feedId = savedFeedId
                        headlinesLimit = bundle.getInt(EXTRA_HEADLINES_LIMIT, 10)
                        readBody = bundle.getBoolean(EXTRA_READ_BODY, false)
                        singleArticleLink = bundle.getString(EXTRA_SINGLE_ARTICLE_LINK)
                        delayBetweenHeadlines = bundle.getInt(EXTRA_DELAY_BETWEEN_HEADLINES, 2)
                        readingMode = bundle.getString(EXTRA_READING_MODE) ?: "unread"
                        sessionName = bundle.getString(EXTRA_SESSION_NAME) ?: "TTS Reading"
                        sessionType = bundle.getString(EXTRA_SESSION_TYPE) ?: SESSION_TYPE_REPEATED

                        // Force request audio focus and start reading
                        val focusGranted =
                                audioFocusManager.requestAudioFocus(interruptionAllowed = true)
                        Log.d(TAG, "Forced audio focus request result: $focusGranted")
                        if (isTtsInitialized) {
                            startReading()
                        }
                    }
                }
                return START_STICKY
            }
        }

        // Parse extras from the Intent
        intent?.let {
            feedId = it.getLongExtra(EXTRA_FEED_ID, -1L)
            Log.d(
                    TAG,
                    "Feed ID from intent: $feedId, setting default feed? ${feedId == -1L}, session type: ${it.getStringExtra(EXTRA_SESSION_TYPE)}"
            )

            // If feedId is invalid and this is a repeated session, log an error
            if (feedId == -1L && it.getStringExtra(EXTRA_SESSION_TYPE) == SESSION_TYPE_REPEATED) {
                Log.e(
                        TAG,
                        "ERROR: Invalid feed ID (-1) for repeated session. Missing feedId extra."
                )
            }

            // Get headlines limit, either from the intent or safely from preferences
            headlinesLimit = it.getIntExtra(EXTRA_HEADLINES_LIMIT, -1)
            if (headlinesLimit < 0) {
                // Not specified in the intent, get from preferences
                headlinesLimit = safeGetIntPreference("headlines_per_session", 10)
            }
            Log.d(TAG, "Headlines limit: $headlinesLimit")

            readBody = it.getBooleanExtra(EXTRA_READ_BODY, false)
            singleArticleLink = it.getStringExtra(EXTRA_SINGLE_ARTICLE_LINK)
            Log.d(TAG, "Read body: $readBody, Single article link: $singleArticleLink")

            // Get delay between headlines, either from the intent or safely from preferences
            delayBetweenHeadlines = it.getIntExtra(EXTRA_DELAY_BETWEEN_HEADLINES, -1)
            if (delayBetweenHeadlines < 0) {
                // Not specified in the intent, get from preferences
                delayBetweenHeadlines = safeGetIntPreference("delay_between_headlines", 2)
            }
            Log.d(TAG, "Delay between headlines: $delayBetweenHeadlines seconds")

            readingMode = it.getStringExtra(EXTRA_READING_MODE) ?: "unread"
            sessionName = it.getStringExtra(EXTRA_SESSION_NAME) ?: "TTS Reading"
            sessionType = it.getStringExtra(EXTRA_SESSION_TYPE) ?: SESSION_TYPE_MANUAL
            articleAgeThresholdMinutes = if (it.hasExtra(EXTRA_ARTICLE_AGE_THRESHOLD)) {
                it.getIntExtra(EXTRA_ARTICLE_AGE_THRESHOLD, -1).takeIf { it > 0 }
            } else null
            announceArticleAge = it.getBooleanExtra(EXTRA_ANNOUNCE_ARTICLE_AGE, false)
            Log.d(
                    TAG,
                    "Session type: $sessionType, Session name: $sessionName, Reading mode: $readingMode, " +
                    "Age threshold: ${articleAgeThresholdMinutes ?: "none"}, " +
                    "Announce article age: $announceArticleAge"
            )

            // Store parameters in case we need to retry due to audio conflict
            storePendingIntentParams(intent)
        }

        // Ensure we have a foreground notification even if not started in onCreate
        if (!isForegroundStarted) {
            try {
                val notification =
                        mediaNotificationManager.buildTtsNotification(
                                "Preparing TTS for $sessionName"
                        )
                Log.d(TAG, "Starting foreground service (in onStartCommand)")
                startForeground(NOTIF_ID, notification)
                isForegroundStarted = true
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service in onStartCommand: ${e.message}", e)
            }
        } else {
            // Update existing notification
            val notification =
                    mediaNotificationManager.buildTtsNotification("Preparing TTS for $sessionName")
            mediaNotificationManager.updateNotification(NOTIF_ID, notification)
            Log.d(TAG, "Updated existing notification for $sessionName")
        }

        // Check if we need to check for audio conflicts
        val shouldCheckAudioConflicts =
                sessionType == SESSION_TYPE_REPEATED || sessionType == SESSION_TYPE_SCHEDULED

        if (shouldCheckAudioConflicts && audioFocusManager.isAudioOrCallActive()) {
            // For repeated or scheduled sessions, check if audio is playing or call is active
            if (audioFocusManager.isCallActive()) {
                // Never interrupt calls
                Log.d(TAG, "Call is active, showing conflict notification")
                mediaNotificationManager.showAudioConflictNotification(sessionName)
                // Set flag to skip reading and return START_NOT_STICKY
                shouldSkipReading = true
                stopSelf()
                return START_NOT_STICKY
            } else {
                // Audio is playing, show notification but don't interrupt
                Log.d(TAG, "Audio is playing, showing conflict notification")
                mediaNotificationManager.showAudioConflictNotification(sessionName)
                // Set flag to skip reading and return START_NOT_STICKY
                shouldSkipReading = true
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            // For single article or manual session, or if no conflict, request audio focus and
            // proceed
            val interruptionAllowed =
                    sessionType == SESSION_TYPE_SINGLE || sessionType == SESSION_TYPE_MANUAL
            val focusGranted = audioFocusManager.requestAudioFocus(interruptionAllowed)
            Log.d(
                    TAG,
                    "Audio focus request result: $focusGranted (interruption allowed: $interruptionAllowed)"
            )
            if (!focusGranted) {
                Log.e(TAG, "Failed to get audio focus")
                // We'll still try to start reading, but it might not work well
            }
        }

        // If TTS is already initialized, we can start reading now
        if (isTtsInitialized && !hasReadingStarted && !shouldSkipReading) {
            Log.d(TAG, "TTS already initialized, starting reading immediately")
            startReading()
        } else {
            Log.d(
                    TAG,
                    "TTS not yet initialized or already started or should skip reading. " +
                            "isTtsInitialized: $isTtsInitialized, hasReadingStarted: $hasReadingStarted, " +
                            "shouldSkipReading: $shouldSkipReading"
            )
        }

        return START_STICKY
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "TTS initialization status: $status")
        if (status == TextToSpeech.SUCCESS) {
            // Set the language - try the default locale
            val defaultLocale = Locale.getDefault()
            val langResult = tts?.setLanguage(defaultLocale)

            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                            langResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e(TAG, "Language not supported: $defaultLocale, trying US English")
                // Try US English as a fallback
                val usResult = tts?.setLanguage(Locale.US)
                if (usResult == TextToSpeech.LANG_MISSING_DATA ||
                                usResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(TAG, "US English is also not supported. TTS might not work")
                    updateNotification("Error: TTS language not available")
                    stopSelf()
                    return
                }
            }

            // Set a progress listener for better debugging
            tts?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.d(TAG, "TTS started: $utteranceId")
                        }

                        override fun onDone(utteranceId: String?) {
                            Log.d(TAG, "TTS completed: $utteranceId")
                        }

                        override fun onError(utteranceId: String?) {
                            Log.e(TAG, "TTS error: $utteranceId")
                        }
                    }
            )

            // Apply saved TTS settings
            TtsSettingsHelper.applyTtsSettings(tts, applicationContext)

            // Log the available voices
            logAvailableVoices()

            isTtsInitialized = true
            Log.d(TAG, "TTS successfully initialized")

            // If we haven't started reading yet and have the data, and shouldn't skip, start now
            if (!hasReadingStarted &&
                            (feedId != -1L || singleArticleLink != null) &&
                            !shouldSkipReading
            ) {
                Log.d(TAG, "TTS initialized and we have data, starting reading")
                startReading()
            } else if (shouldSkipReading) {
                Log.d(TAG, "Skipping reading due to audio conflict")
                stopSelf()
            } else {
                Log.d(
                        TAG,
                        "TTS initialized but not starting reading - hasReadingStarted: $hasReadingStarted, " +
                                "feedId: $feedId, singleArticleLink: $singleArticleLink"
                )
            }
        } else {
            // TTS init failed
            Log.e(TAG, "TTS initialization failed with status: $status")
            updateNotification("Error: TTS initialization failed")
            stopSelf()
        }
    }

    /** Log available voices for debugging */
    private fun logAvailableVoices() {
        val voices = tts?.voices
        Log.d(TAG, "Available TTS voices: ${voices?.size ?: 0}")
        voices?.forEach { voice ->
            Log.d(TAG, "Voice: ${voice.name}, Locale: ${voice.locale}, Quality: ${voice.quality}")
        }
    }

    private fun startReading() {
        if (hasReadingStarted) {
            Log.d(TAG, "startReading() called but reading has already started, ignoring")
            return
        }

        Log.d(TAG, "Starting reading process")
        hasReadingStarted = true
        isReading = true

        // Update media session state to playing
        val stateBuilder =
                PlaybackStateCompat.Builder()
                        .setActions(
                                PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP
                        )
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
        mediaSession.setPlaybackState(stateBuilder.build())
        Log.d(TAG, "MediaSession state updated to PLAYING")

        // Start with notification showing media controls
        updateMediaNotification()

        serviceScope.launch {
            try {
                Log.d(TAG, "Loading articles for feed $feedId")
                val dataManager = (application as KeyNewsApp).dataManager

                Log.d(
                        TAG,
                        "startReading: Loading articles for feed $feedId, session type: $sessionType, session name: $sessionName"
                )

                // Determine if we should refresh articles
                val shouldRefreshArticles =
                        when {
                            // For single article reading, skip refresh completely
                            singleArticleLink != null -> {
                                Log.d(TAG, "Single article mode - skipping refresh")
                                false
                            }
                            // For manual sessions, only refresh if last refresh was > 5 minutes ago
                            sessionType == SESSION_TYPE_MANUAL -> {
                                val needsRefresh =
                                        com.example.keynews.FeedRefreshTracker.shouldRefresh(
                                                feedId,
                                                5
                                        )
                                Log.d(
                                        TAG,
                                        "Manual session - ${if (needsRefresh) "refreshing" else "skipping refresh"} (last refresh > 5 min ago: $needsRefresh)"
                                )
                                needsRefresh
                            }
                            // For other sessions (repeated/scheduled), always refresh
                            else -> true
                        }

                // Refresh articles for the feed if needed
                if (shouldRefreshArticles) {
                    withContext(Dispatchers.IO) {
                        try {
                            dataManager.refreshArticles(feedId)
                            Log.d(TAG, "Articles refreshed successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error refreshing articles: ${e.message}", e)
                        }
                    }
                }

                // Broadcast update so any UI can refresh
                LocalBroadcastManager.getInstance(this@TtsReadingService)
                        .sendBroadcast(Intent(ACTION_FEED_UPDATED))
                Log.d(TAG, "Broadcast ACTION_FEED_UPDATED sent")

                // Wait for UI update if a callback is registered
                if (com.example.keynews.util.FeedUpdateNotifier.isRegistered()) {
                    Log.d(TAG, "Waiting for feed update notifier")
                    com.example.keynews.util.FeedUpdateNotifier.waitForUpdate()
                }

                val db = (application as KeyNewsApp).database
                val articleDao = db.newsArticleDao()

                val localSingleArticleLink = singleArticleLink
                if (localSingleArticleLink != null && localSingleArticleLink.isNotEmpty()) {
                    Log.d(TAG, "Single article mode for link: $localSingleArticleLink")
                    val article =
                            withContext(Dispatchers.IO) {
                                articleDao.getArticleByLink(localSingleArticleLink)
                            }

                    if (article != null) {
                        // Single article mode - set progress to 1/1
                        currentArticleIndex = 1
                        totalArticlesToRead = 1
                        currentArticle = article

                        // Update notification with media controls
                        updateMediaNotification()

                        readArticlesSequentially(listOf(article))
                        return@launch
                    } else {
                        Log.e(TAG, "Article with link $localSingleArticleLink not found")
                    }
                }

                // Use new filtering method for getting articles
                Log.d(TAG, "Getting articles for feed $feedId with mode $readingMode")
                val allArticles =
                        withContext(Dispatchers.IO) {
                            try {
                                Log.d(
                                        TAG,
                                        "Getting articles for feed ID $feedId with reading mode $readingMode " +
                                        "(unread only: ${readingMode == "unread"}) and age threshold: $articleAgeThresholdMinutes"
                                )
                                Log.d(TAG, "🔍 AI FILTERING: Calling getArticlesForFeed with context for feedId=$feedId")
                                dataManager.getArticlesForFeed(
                                    feedId, 
                                    readingMode == "unread",
                                    articleAgeThresholdMinutes,
                                    applicationContext
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error loading articles: ${e.message}", e)
                                emptyList()
                            }
                        }

                Log.d(TAG, "Got ${allArticles.size} articles, limiting to $headlinesLimit")
                val articlesToRead = allArticles.take(headlinesLimit)

                if (articlesToRead.isEmpty()) {
                    Log.d(TAG, "No articles to read, finishing service")
                    broadcastDone()
                    stopSelf()
                    return@launch
                }

                // Set total for progress tracking
                totalArticlesToRead = articlesToRead.size
                currentArticleIndex = 0
                Log.d(TAG, "Will read $totalArticlesToRead articles")

                // Update notification with media controls
                updateMediaNotification()

                readArticlesSequentially(articlesToRead)
            } catch (e: Exception) {
                Log.e(TAG, "Error in startReading(): ${e.message}", e)
                // Update notification with error info
                updateNotification("Error reading articles: ${e.message}")
                stopSelf()
            }
        }
    }

    /** Helper function to safely read an integer preference */
    private fun safeGetIntPreference(key: String, defaultValue: Int): Int {
        val prefs =
                applicationContext.getSharedPreferences("keynews_settings", Context.MODE_PRIVATE)
        return try {
            prefs.getInt(key, defaultValue)
        } catch (e: ClassCastException) {
            // If there's a type mismatch, try to get it as a float
            try {
                val floatValue = prefs.getFloat(key, defaultValue.toFloat())
                val intValue = floatValue.toInt()
                // Fix the preference for future use
                prefs.edit().putInt(key, intValue).apply()
                Log.d(TAG, "Fixed preference type for $key: $floatValue -> $intValue")
                intValue
            } catch (e2: Exception) {
                Log.e(TAG, "Error reading preference $key: ${e2.message}")
                defaultValue
            }
        }
    }

    /** Store the intent parameters in case we need to retry due to audio conflict */
    private fun storePendingIntentParams(intent: Intent) {
        pendingIntentParams =
                Bundle().apply {
                    putLong(EXTRA_FEED_ID, intent.getLongExtra(EXTRA_FEED_ID, -1L))
                    putInt(EXTRA_HEADLINES_LIMIT, intent.getIntExtra(EXTRA_HEADLINES_LIMIT, 10))
                    putBoolean(EXTRA_READ_BODY, intent.getBooleanExtra(EXTRA_READ_BODY, false))
                    putString(
                            EXTRA_SINGLE_ARTICLE_LINK,
                            intent.getStringExtra(EXTRA_SINGLE_ARTICLE_LINK)
                    )
                    putInt(
                            EXTRA_DELAY_BETWEEN_HEADLINES,
                            intent.getIntExtra(EXTRA_DELAY_BETWEEN_HEADLINES, 2)
                    )
                    putString(
                            EXTRA_READING_MODE,
                            intent.getStringExtra(EXTRA_READING_MODE) ?: "unread"
                    )
                    putString(
                            EXTRA_SESSION_NAME,
                            intent.getStringExtra(EXTRA_SESSION_NAME) ?: "TTS Reading"
                    )
                    putString(
                            EXTRA_SESSION_TYPE,
                            intent.getStringExtra(EXTRA_SESSION_TYPE) ?: SESSION_TYPE_MANUAL
                    )
                    if (intent.hasExtra(EXTRA_ARTICLE_AGE_THRESHOLD)) {
                        putInt(EXTRA_ARTICLE_AGE_THRESHOLD, intent.getIntExtra(EXTRA_ARTICLE_AGE_THRESHOLD, -1))
                    }
                    putBoolean(EXTRA_ANNOUNCE_ARTICLE_AGE, intent.getBooleanExtra(EXTRA_ANNOUNCE_ARTICLE_AGE, false))
                }
        Log.d(TAG, "Stored pending intent parameters")
    }

    /** Pause TTS playback */
    private fun pauseTts() {
        if (!isPaused && tts != null) {
            Log.d(TAG, "Pausing TTS playback")
            tts?.stop()
            isPaused = true

            // Update media session state
            val stateBuilder =
                    PlaybackStateCompat.Builder()
                            .setActions(
                                    PlaybackStateCompat.ACTION_PLAY or
                                            PlaybackStateCompat.ACTION_STOP
                            )
                            .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
            mediaSession.setPlaybackState(stateBuilder.build())
            Log.d(TAG, "MediaSession state updated to PAUSED")

            // Update notification with pause state
            updateMediaNotification()
        }
    }

    /** Resume TTS playback */
    private fun resumeTts() {
        if (isPaused && currentArticle != null) {
            Log.d(TAG, "Resuming TTS playback")
            isPaused = false
            // Re-request audio focus
            audioFocusManager.requestAudioFocus()

            // Update media session state
            val stateBuilder =
                    PlaybackStateCompat.Builder()
                            .setActions(
                                    PlaybackStateCompat.ACTION_PAUSE or
                                            PlaybackStateCompat.ACTION_STOP
                            )
                            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
            mediaSession.setPlaybackState(stateBuilder.build())
            Log.d(TAG, "MediaSession state updated to PLAYING")

            // Restart speaking the current article
            serviceScope.launch {
                try {
                    val textToSpeak = buildString {
                        append(currentArticle!!.title)
                        if (readBody && !currentArticle!!.description.isNullOrBlank()) {
                            append(". ")
                            append(currentArticle!!.description)
                        }
                    }
                    Log.d(TAG, "Resuming speaking text: ${textToSpeak.take(50)}...")
                    configureVoiceForText(textToSpeak)
                    speakText(textToSpeak)

                    // Continue with remaining articles if this was part of a sequence
                    if (currentArticleIndex > 0 && currentArticleIndex < totalArticlesToRead) {
                        Log.d(TAG, "Continuing with remaining articles after current one")
                        val dataManager = (application as KeyNewsApp).dataManager
                        val allArticles =
                                withContext(Dispatchers.IO) {
                                    try {
                                        if (singleArticleLink != null &&
                                                        singleArticleLink!!.isNotEmpty()
                                        ) {
                                            // For single article, no need to continue
                                            emptyList()
                                        } else {
                                            Log.d(TAG, "🔍 AI FILTERING: Calling getArticlesForFeed with context for remaining articles of feedId=$feedId")
                                            dataManager.getArticlesForFeed(
                                                    feedId,
                                                    readingMode == "unread",
                                                    null,
                                                    applicationContext
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                                TAG,
                                                "Error loading remaining articles: ${e.message}",
                                                e
                                        )
                                        emptyList()
                                    }
                                }

                        // Get remaining articles (skipping already read ones)
                        val remainingArticles =
                                allArticles
                                        .take(headlinesLimit)
                                        .drop(
                                                currentArticleIndex
                                        ) // Skip articles we've already read

                        if (remainingArticles.isNotEmpty()) {
                            Log.d(TAG, "Found ${remainingArticles.size} remaining articles to read")
                            // Pass current index as offset to maintain correct count
                            readArticlesSequentially(remainingArticles, currentArticleIndex)
                        } else {
                            Log.d(TAG, "No remaining articles to read")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during resumeTts: ${e.message}", e)
                }
            }
            // Update notification with play state
            updateMediaNotification()
        } else {
            Log.d(
                    TAG,
                    "Cannot resume - isPaused: $isPaused, currentArticle: ${currentArticle != null}"
            )
        }
    }

    /** Update the media notification */
    private fun updateMediaNotification() {
        val articleTitle = currentArticle?.title ?: "No article"
        Log.d(
                TAG,
                "Updating media notification with article: '$articleTitle' ($currentArticleIndex/$totalArticlesToRead)"
        )

        try {
            val notification =
                    mediaNotificationManager.buildMediaControlNotification(
                            title = "$sessionName ($currentArticleIndex/$totalArticlesToRead)",
                            articleTitle = articleTitle,
                            sessionToken = mediaSession.sessionToken
                    )
            mediaNotificationManager.updateNotification(NOTIF_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating media notification: ${e.message}", e)
        }
    }

    /**
     * Reads each article in sequence using TTS, marks it read, and broadcasts progress.
     * @param articles List of articles to read
     * @param startIndexOffset Optional offset for article indexing (for continuing sequences)
     */
    private suspend fun readArticlesSequentially(
        articles: List<NewsArticle>,
        startIndexOffset: Int = 0
    ) {
        Log.d(TAG, "Starting readArticlesSequentially with ${articles.size} articles, offset: $startIndexOffset")

        withContext(Dispatchers.Main) {
            for ((index, article) in articles.withIndex()) {
                if (!isReading) {
                    Log.d(TAG, "isReading is false, breaking out of the reading loop")
                    break
                }

                currentArticleIndex = startIndexOffset + index + 1
                currentArticle = article
                Log.d(TAG, "Reading article $currentArticleIndex/$totalArticlesToRead: ${article.title}")
                broadcastProgress(article, currentArticleIndex, totalArticlesToRead)

                // Update the notification first to show what we're about to read
                updateMediaNotification()
                
                // If enabled, announce the article age first
                if (announceArticleAge && sessionType == SESSION_TYPE_REPEATED) {
                    val ageText = TimeUtil.getRelativeTimeString(article.publishDateUtc)
                    Log.d(TAG, "Announcing article age: $ageText")
                    
                    // Speak the age announcement
                    configureVoiceForText(ageText)
                    if (!isPaused) {
                        speakText(ageText)
                        
                        // Pause for 0.5 seconds between announcement and headline
                        delay(500)
                    }
                }
                
                // Now prepare the headline (and body if enabled)
                val textToSpeak = buildString {
                    append(article.title)
                    if (readBody && !article.description.isNullOrBlank()) {
                        append(". ")
                        append(article.description)
                    }
                }

                // Speak each article if not paused
                configureVoiceForText(textToSpeak)
                if (!isPaused) {
                    Log.d(TAG, "Speaking text: ${textToSpeak.take(50)}...")
                    speakText(textToSpeak)
                } else {
                    Log.d(TAG, "TTS is paused, not speaking")
                }

                // Mark each as read
                try {
                    markArticleRead(article)
                    Log.d(TAG, "Marked article as read: ${article.link}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error marking article as read: ${e.message}", e)
                }

                // Let UI know
                LocalBroadcastManager.getInstance(this@TtsReadingService)
                .sendBroadcast(Intent(ACTION_FEED_UPDATED))

                // Optional delay between headlines
                if (index < articles.size - 1 && delayBetweenHeadlines > 0 && !isPaused) {
                    Log.d(TAG, "Pausing for $delayBetweenHeadlines seconds before next article")
                    val notification = mediaNotificationManager.buildTtsNotification(
                        "Pausing for ${delayBetweenHeadlines} seconds..."
                    )
                    mediaNotificationManager.updateNotification(NOTIF_ID, notification)

                    delay(delayBetweenHeadlines * 1000L)
                }
            }

            // When done reading everything:
            Log.d(TAG, "Finished reading all articles, broadcasting done")
            broadcastDone()

            // Reset counters
            currentArticleIndex = 0
            totalArticlesToRead = 0
            currentArticle = null

            // Release audio focus, stop being a foreground service
            audioFocusManager.abandonAudioFocus()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping foreground service: ${e.message}", e)
            }

            Log.d(TAG, "Stopping service")

            // If this was a repeated session, schedule the next run
            if (sessionType == SESSION_TYPE_REPEATED) {
                val repeatedSessionId = initialIntent?.getLongExtra(
                    RepeatedSessionReceiver.EXTRA_SESSION_ID,
                    -1L
                ) ?: -1L

                if (repeatedSessionId != -1L) {
                    try {
                        Log.d(TAG, "Rescheduling next alarms for repeated session $repeatedSessionId")
                        RepeatedSessionScheduler.rescheduleAlarmsForSession(
                            this@TtsReadingService,
                            repeatedSessionId
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error rescheduling after reading: ${e.message}", e)
                    }

                    // *** Add this broadcast to tell the UI to refresh countdown. ***
                    LocalBroadcastManager.getInstance(this@TtsReadingService).sendBroadcast(
                        Intent("com.example.keynews.ACTION_SESSION_UPDATED")
                    )
                }
            }

            // Finally, shut down the service
            stopSelf()
        }
    }

    /**
     * Detects the language of the text and configures TTS to use the appropriate voice
     * @return true if language was detected and appropriate voice was set
     */
    private suspend fun configureVoiceForText(text: String): Boolean {
        if (text.isBlank() || languageDetector == null) {
            Log.d(TAG, "Cannot configure voice - text is blank or language detector is null")
            return false
        }

        try {
            // Get current engine name
            val engineName = tts?.defaultEngine ?: return false

            // Detect the language of the text
            val detectedLangCode = languageDetector?.detectLanguage(text) ?: return false

            Log.d(TAG, "Detected language: $detectedLangCode for text: ${text.take(50)}...")

            // Look up user preferences for this language and engine
            val preference =
                    TtsPreferenceManager.getBestVoiceForLanguage(
                            applicationContext,
                            engineName,
                            detectedLangCode
                    )

            if (preference != null) {
                // User has a specific voice preference for this language
                Log.d(
                        TAG,
                        "Found voice preference: ${preference.voiceName} for language: ${preference.languageCode}"
                )

                // Find the voice object
                val voices = tts?.voices ?: emptySet()
                // val voice = voices.find { it.name == preference.voiceName }
                val voice = voices.find { it.name.equals(preference.voiceName, ignoreCase = true) }

                if (voice != null) {
                    // Set the voice
                    Log.d(TAG, "Setting voice to: ${voice.name} for language: ${voice.locale}")
                    tts?.voice = voice
                    return true
                } else {
                    // Voice not found, fallback to language only
                    val locale = Locale.forLanguageTag(preference.languageCode)
                    Log.d(TAG, "Voice not found, falling back to locale: $locale")
                    val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_MISSING_DATA
                    return result != TextToSpeech.LANG_MISSING_DATA &&
                            result != TextToSpeech.LANG_NOT_SUPPORTED
                }
            } else {
                // No specific preference, just set the language
                val locale = Locale.forLanguageTag(detectedLangCode)
                Log.d(TAG, "No voice preference, setting locale: $locale")
                val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_MISSING_DATA
                return result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring voice for text: ${e.message}", e)
            return false
        }
    }

    /** Speaks text synchronously (blocks until done). */
    private suspend fun speakText(text: String) =
            suspendCancellableCoroutine<Unit> { cont ->
                if (tts == null) {
                    Log.e(TAG, "TTS is null, cannot speak text")
                    cont.resume(Unit) {}
                    return@suspendCancellableCoroutine
                }

                // Use the newer UtteranceProgressListener for API 21+
                val utteranceId = "TTS_UTTERANCE_ID_${System.currentTimeMillis()}"

                tts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                Log.d(TAG, "Started speaking: $utteranceId")
                            }

                            override fun onDone(utteranceId: String?) {
                                Log.d(TAG, "Finished speaking: $utteranceId")
                                if (!cont.isCompleted) {
                                    cont.resume(Unit) {}
                                }
                            }

                            override fun onError(utteranceId: String?) {
                                Log.e(TAG, "Error speaking: $utteranceId")
                                if (!cont.isCompleted) {
                                    cont.resume(Unit) {}
                                }
                            }
                        }
                )

                // Create params bundle
                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

                // Queue the text
                val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                Log.d(TAG, "TTS speak result: $result for text length: ${text.length}")

                // If speaking fails immediately, resume the coroutine
                if (result == TextToSpeech.ERROR) {
                    Log.e(TAG, "TTS speak failed immediately")
                    if (!cont.isCompleted) {
                        cont.resume(Unit) {}
                    }
                }
            }

    private suspend fun markArticleRead(article: NewsArticle) {
        withContext(Dispatchers.IO) {
            try {
                (application as KeyNewsApp).database.newsArticleDao().markRead(article.link, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error marking article read: ${e.message}", e)
                throw e
            }
        }
    }

    private fun broadcastProgress(article: NewsArticle, current: Int = 0, total: Int = 0) {
        val intent =
                Intent(ACTION_READING_PROGRESS).apply {
                    putExtra(EXTRA_ARTICLE_LINK, article.link)
                    putExtra("current_index", current)
                    putExtra("total_articles", total)
                }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "Broadcast progress: $current/$total for article: ${article.link}")
    }

    private fun broadcastDone() {
        val intent = Intent(ACTION_READING_DONE)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "Broadcast reading done")
    }

    /** Receiver for progress requests */
    private val progressRequestReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == ACTION_GET_READING_PROGRESS) {
                        Log.d(TAG, "Received request for reading progress")
                        // If we're reading and have progress info, broadcast it
                        if (currentArticleIndex > 0 && totalArticlesToRead > 0) {
                            val progressIntent =
                                    Intent(ACTION_READING_PROGRESS).apply {
                                        putExtra("current_index", currentArticleIndex)
                                        putExtra("total_articles", totalArticlesToRead)
                                        putExtra(EXTRA_ARTICLE_LINK, currentArticle?.link)
                                    }
                            LocalBroadcastManager.getInstance(context).sendBroadcast(progressIntent)
                            Log.d(
                                    TAG,
                                    "Responded with progress: $currentArticleIndex/$totalArticlesToRead"
                            )
                        } else {
                            Log.d(TAG, "No progress to report")
                        }
                    }
                }
            }

    /** Build or update the foreground notification (legacy method, kept for compatibility) */
    private fun updateNotification(info: String) {
        Log.d(TAG, "Updating notification with info: $info")
        val notification = mediaNotificationManager.buildTtsNotification(info)
        mediaNotificationManager.updateNotification(NOTIF_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TtsReadingService onDestroy called")
        try {
            // Stop reading
            isReading = false

            // Release audio focus
            audioFocusManager.abandonAudioFocus()

            // Clean up TTS
            tts?.stop()
            tts?.shutdown()
            languageDetector?.close()

            // Reset pause state
            isPaused = false

            // Release media session
            try {
                mediaSession.isActive = false
                mediaSession.release()
                Log.d(TAG, "MediaSession released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaSession: ${e.message}")
            }

            // Unregister receivers
            LocalBroadcastManager.getInstance(this).unregisterReceiver(progressRequestReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS: ${e.message}", e)
        }
        serviceJob.cancel()
    }
    

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}

