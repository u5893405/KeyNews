package com.example.keynews.ui.articles

// CodeCleaner_Start_e3f51099-7980-4fd2-8005-8dd9d372bbb9
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.KeyNewsApp
import com.example.keynews.data.model.NewsArticle
import com.example.keynews.data.model.RepeatedSessionWithRules
import com.example.keynews.data.model.RuleType
import com.example.keynews.databinding.FragmentArticlesBinding
import com.example.keynews.service.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.widget.RadioButton
import android.widget.RadioGroup
import com.example.keynews.MainActivity
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.keynews.R
import com.example.keynews.service.RepeatedSessionReceiver
import com.example.keynews.service.TtsReadingService
import com.example.keynews.ui.fetching.RssFetcher
import com.example.keynews.ui.rep_session.RepeatedSessionScheduler
import com.example.keynews.data.model.RepeatedSessionRule
import kotlinx.coroutines.delay


class ArticlesFragment : Fragment() {

    private var _binding: FragmentArticlesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ArticlesAdapter
    // Default to showing only unread articles
    private var showUnreadOnly = true
    private var selectedFeedId: Long = 0L
    
    // Info bar related members
    private lateinit var infoBarManager: InfoBarManager
    private var popupWindow: PopupWindow? = null
    
    // Session management
    private lateinit var sessionStartHelper: SessionStartHelper
    
    // Reading progress tracking
    private var currentArticleIndex = 0
    private var totalArticles = 0
    
    // AI Filtering progress manager
    private lateinit var aiFilterProgressManager: AiFilterProgressManager

    private val pendingRuleChanges = mutableMapOf<Long, Boolean>() // ruleId -> isActive
    
    companion object {
        private const val TAG = "ArticlesFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArticlesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize adapter
        adapter = ArticlesAdapter(
            onArticleClick = { article -> readArticleWithTts(article) },
            onCheckRead = { article -> markRead(article) },
            onSourceClick = { article -> openArticleUrl(article.link) },
            onBookmarkClick = { article -> toggleReadLater(article) }
        )
        selectedFeedId = arguments?.getLong("selectedFeedId", 0L) ?: 0L
        binding.rvArticles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvArticles.adapter = adapter
        
        // Set the article body length limit from preferences
        val prefs = requireContext().getSharedPreferences("keynews_settings", Context.MODE_PRIVATE)
        // Safely get the value - try getInt, but fall back if there's a type mismatch
        val bodyLengthLimit = try {
            prefs.getInt("article_body_length", 120)
        } catch (e: ClassCastException) {
            // If there's a type mismatch, try to read it as a different type and convert
            try {
                val value = prefs.getFloat("article_body_length", 120f)
                value.toInt().also {
                    // Fix the preference type for future use
                    prefs.edit().putInt("article_body_length", it).apply()
                    Log.d(TAG, "Fixed article_body_length type: $value -> $it")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Error reading article_body_length: ${e2.message}")
                120 // Default value
            }
        }
        adapter.setArticleBodyLengthLimit(bodyLengthLimit)

        // Initialize info bar manager
        infoBarManager = InfoBarManager(
            requireContext(),
            binding.tvSessionStatus,
            binding.tvNextSessionTimer,
            binding.tvReadingProgress
        )
        
        // Initialize AI filtering progress manager
        aiFilterProgressManager = AiFilterProgressManager(
            requireContext(),
            binding.infoBar,
            binding.tvReadingProgress
        )
        
        // Initialize session starter
        sessionStartHelper = SessionStartHelper(requireContext())
        
        // Set up info bar click listener
        binding.infoBar.setOnClickListener {
            showRepeatedSessionsPopup()
        }

        // Toggle "Unread only" vs "Show all"
        val visibilityButton = binding.btnVisibilityToggle
        
        // Set the initial icon based on current state
        updateVisibilityButtonIcon()
        
        visibilityButton.setOnClickListener { view ->
            // Create popup menu
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.menuInflater.inflate(R.menu.visibility_menu, popupMenu.menu)
            
            // Handle menu item clicks
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_show_unread -> {
                        showUnreadOnly = true
                        updateVisibilityButtonIcon()
                        loadArticles()
                        true
                    }
                    R.id.menu_show_all -> {
                        showUnreadOnly = false
                        updateVisibilityButtonIcon()
                        loadArticles()
                        true
                    }
                    else -> false
                }
            }
            
            // Show the menu
            popupMenu.show()
        }

        binding.stopButton.setOnClickListener {
            val stopIntent = Intent(requireContext(), TtsReadingService::class.java)
            requireContext().stopService(stopIntent)
            // Remove highlighting when stopping manually
            highlightCurrentlyReading(null)
            // Reset reading progress
            infoBarManager.resetReadingProgress()
        }
        binding.refreshButton.setOnClickListener {
            refreshNews()
        }
        binding.btnManualSession.setOnClickListener {
            startManualTtsReading()
        }
        
        binding.btnManageReadStatus.setOnClickListener {
            showManageReadStatusDialog()
        }
        
        binding.fabScrollTop.setOnClickListener {
            binding.rvArticles.smoothScrollToPosition(0)
        }
        
        // Update info bar initially
        updateInfoBar()
    }

    /**
     * Updates the visibility button icon based on the current showUnreadOnly state
     */
    private fun updateVisibilityButtonIcon() {
        val icon = if (showUnreadOnly) {
            R.drawable.visibility_off_24  // Crossed-out eye for unread only
        } else {
            R.drawable.visibility_24      // Eye for show all
        }
        
        // Update icon
        binding.btnVisibilityToggle.setIconResource(icon)
        
        // Update background color
        val background = if (showUnreadOnly) {
            R.drawable.bg_visibility_off  // Orange background for unread only
        } else {
            R.drawable.bg_visibility_on   // Blue background for show all
        }
        binding.btnVisibilityToggle.setBackgroundResource(background)
    }

    override fun onResume() {
        super.onResume()
        com.example.keynews.util.FeedUpdateNotifier.register()
        registerReadingReceiver()
        
        // Update feed name in toolbar
        updateFeedNameInToolbar()
        
        // Refresh article body length setting from preferences
        val prefs = requireContext().getSharedPreferences("keynews_settings", Context.MODE_PRIVATE)
        // Safely get the value - try getInt, but fall back if there's a type mismatch
        val bodyLengthLimit = try {
            prefs.getInt("article_body_length", 120)
        } catch (e: ClassCastException) {
            // If there's a type mismatch, try to read it as a different type and convert
            try {
                val value = prefs.getFloat("article_body_length", 120f)
                value.toInt().also {
                    // Fix the preference type for future use
                    prefs.edit().putInt("article_body_length", it).apply()
                    Log.d(TAG, "Fixed article_body_length type in onResume: $value -> $it")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Error reading article_body_length in onResume: ${e2.message}")
                120 // Default value
            }
        }
        adapter.setArticleBodyLengthLimit(bodyLengthLimit)
        
        loadArticles()
        updateInfoBar()
        
        // Start periodically updating the info bar
        startInfoBarUpdates()
    }

    override fun onPause() {
        super.onPause()
        unregisterReadingReceiver()
        com.example.keynews.util.FeedUpdateNotifier.unregister()
        
        // Clean up info bar resources
        infoBarManager.cleanup()
        
        // Clean up AI progress resources
        aiFilterProgressManager.cleanup()
        
        // Clear progress listener
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        val appContext = context
        if (appContext != null) {
            val geminiService = dataManager.getGeminiService(appContext)
            geminiService.setProgressListener(null)
        }
    }

    private fun registerReadingReceiver() {
        val filter = IntentFilter().apply {
            addAction(TtsReadingService.ACTION_READING_PROGRESS)
            addAction(TtsReadingService.ACTION_READING_DONE)
            addAction(TtsReadingService.ACTION_FEED_UPDATED)
            addAction(RepeatedSessionReceiver.ACTION_SESSION_STARTED)
            addAction("com.example.keynews.ACTION_SESSION_UPDATED")
        }
        LocalBroadcastManager.getInstance(requireContext())
        .registerReceiver(readingProgressReceiver, filter)
    }


    private fun unregisterReadingReceiver() {
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(readingProgressReceiver)
    }

    private fun highlightCurrentlyReading(articleLink: String?) {
        adapter.setCurrentlyReadingLink(articleLink)
    }

    private fun loadArticles() {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            try {
                // If no feed ID was provided, find the default feed
                if (selectedFeedId == 0L) {
                    val defaultFeed = dataManager.database.readingFeedDao().getAllFeeds()
                        .firstOrNull { it.isDefault }
                    selectedFeedId = defaultFeed?.id ?: 0L
                }

                // Get articles with keyword filtering applied
                val articles = if (selectedFeedId > 0) {
                    // Use the new filtering function with context for AI filtering
                    android.util.Log.d(TAG, "🔍 AI FILTERING: Calling getArticlesForFeed with feedId=$selectedFeedId, context=$context")
                    
                    // Set up the progress listener for AI filtering
                    val appContext = context
                    if (appContext != null) {
                        val geminiService = dataManager.getGeminiService(appContext)
                        geminiService.setProgressListener(object : GeminiService.ProgressListener {
                            override fun onFilteringStarted(totalArticles: Int) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    aiFilterProgressManager.showProgress(totalArticles)
                                }
                            }
                            
                            override fun onProgressUpdate(processed: Int, total: Int, currentBatch: Int, totalBatches: Int) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    aiFilterProgressManager.updateProgress(processed, total, currentBatch, totalBatches)
                                }
                            }
                            
                            override fun onFilteringCompleted() {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    aiFilterProgressManager.hideProgress()
                                }
                            }
                        })
                    }
                    
                    dataManager.getArticlesForFeed(selectedFeedId, showUnreadOnly, null, context)
                } else {
                    // Fallback to all articles if no valid feed
                    val allArticles = dataManager.database.newsArticleDao().getAllArticles()
                    if (showUnreadOnly) {
                        allArticles.filter { !it.isRead }
                    } else {
                        allArticles
                    }
                }

                // Get sources for the displayed articles
                val uniqueSourceIds = articles.map { it.sourceId }.distinct()
                val sources = if (uniqueSourceIds.isNotEmpty()) {
                    dataManager.database.newsSourceDao().getSourcesByIds(uniqueSourceIds)
                } else {
                    emptyList()
                }

                // Update the adapter with articles, sources, and matched keywords
                adapter.submitList(articles)
                adapter.updateSources(sources)
                adapter.updateMatchedKeywords(dataManager.getMatchedKeywords())
                com.example.keynews.util.FeedUpdateNotifier.notifyUpdated()
            } catch (e: Exception) {
                Log.e("ArticlesFragment", "Error loading articles: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading articles: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openArticleUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun markRead(article: NewsArticle) {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            // toggling read
            val newState = !article.isRead
            dataManager.database.newsArticleDao().markRead(article.link, newState)
            loadArticles()
        }
    }
    
    /**
     * Toggle read later status for an article
     */
    private fun toggleReadLater(article: NewsArticle) {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            // Get current feed ID for saving with the article
            val feedId = selectedFeedId
            
            // Toggle read later status
            val newState = !article.isReadLater
            dataManager.database.newsArticleDao().markReadLater(article.link, newState, if (newState) feedId else null)
            
            // Show feedback
            withContext(Dispatchers.Main) {
                val message = if (newState) {
                    "Added to Read Later"
                } else {
                    "Removed from Read Later"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                
                // Reload articles to update the UI
                loadArticles()
            }
        }
    }

    private fun refreshNews() {
        // Update progress indicator
        binding.progressBar.visibility = View.VISIBLE
        binding.refreshButton.isEnabled = false

        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            try {
                if (selectedFeedId > 0) {
                    // Use the refreshArticles function for the selected feed
                    dataManager.refreshArticles(selectedFeedId)
                    
                    // Update last refresh timestamp for this feed
                    com.example.keynews.FeedRefreshTracker.setLastRefreshTime(selectedFeedId)
                    
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.refreshButton.isEnabled = true
                        
                        // Update UI with new articles
                        loadArticles()
                        
                        Toast.makeText(context, "Articles updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Gather all sources for fetching
                    val sourceUrlMap = mutableMapOf<String, Long>()
                    
                    // Get all sources
                    val sources = dataManager.database.newsSourceDao().getAllSources()
                    sources.forEach { source ->
                        sourceUrlMap[source.rssUrl] = source.id
                    }
                    
                    if (sourceUrlMap.isNotEmpty()) {
                        // Create our robust fetcher
                        val rssFetcher = RssFetcher()
                        val newArticles = rssFetcher.fetchAllRssFeeds(sourceUrlMap)
                        
                        // Save all articles to database
                        if (newArticles.isNotEmpty()) {
                            dataManager.saveArticles(newArticles)
                        }
                        
                        // Update timestamps for all feeds since we did a global refresh
                        val allFeeds = dataManager.database.readingFeedDao().getAllFeeds()
                        val currentTime = System.currentTimeMillis()
                        allFeeds.forEach { feed ->
                            com.example.keynews.FeedRefreshTracker.setLastRefreshTime(feed.id ?: 0L, currentTime)
                        }
                        
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.refreshButton.isEnabled = true
                            
                            // Update UI with new articles
                            loadArticles()
                            
                            // Show feedback based on results
                            if (newArticles.isNotEmpty()) {
                                Toast.makeText(context, "Fetched ${newArticles.size} articles", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No new articles found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.refreshButton.isEnabled = true
                            Toast.makeText(context, "No RSS sources configured", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ArticlesFragment", "Error refreshing news: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.refreshButton.isEnabled = true
                    Toast.makeText(context, "Error refreshing feeds: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val readingProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                TtsReadingService.ACTION_READING_PROGRESS -> {
                    val link = intent.getStringExtra(TtsReadingService.EXTRA_ARTICLE_LINK)
                    val currentIndex = intent.getIntExtra("current_index", 0)
                    val total = intent.getIntExtra("total_articles", 0)
                    highlightCurrentlyReading(link)
                    
                    // Update reading progress in info bar
                    if (currentIndex > 0 && total > 0) {
                        infoBarManager.updateReadingProgress(currentIndex, total)
                    }
                }
                TtsReadingService.ACTION_READING_DONE -> {
                    highlightCurrentlyReading(null)
                    loadArticles()
                    infoBarManager.resetReadingProgress()
                }
                // When a feed update is broadcast, reload the articles
                TtsReadingService.ACTION_FEED_UPDATED -> {
                    loadArticles()
                }
                // When a repeated session starts, update the info bar
                RepeatedSessionReceiver.ACTION_SESSION_STARTED -> {
                    val sessionId = intent.getLongExtra(RepeatedSessionReceiver.EXTRA_SESSION_ID, -1L)
                    val sessionName = intent.getStringExtra(RepeatedSessionReceiver.EXTRA_SESSION_NAME)
                    if (sessionId != -1L && sessionName != null) {
                        // Update UI to reflect that a session has started
                        updateInfoBar()
                        Toast.makeText(
                            context,
                            "Started session: $sessionName",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                "com.example.keynews.ACTION_SESSION_UPDATED" -> {
                    // *** Refresh InfoBar so user sees the new countdown immediately. ***
                    updateInfoBar()
                }
            }
        }
    }

    private fun startManualTtsReading() {
        val intent = Intent(requireContext(), TtsReadingService::class.java)

        // Pass the current feed ID
        intent.putExtra(TtsReadingService.EXTRA_FEED_ID, selectedFeedId)

        // Get settings from preferences
        val prefs = requireContext().getSharedPreferences("keynews_settings", Context.MODE_PRIVATE)
        
        // Get headline limit from settings (default to 10 if not set)
        val headlinesLimit = prefs.getInt("headlines_per_session", 10)
        intent.putExtra(TtsReadingService.EXTRA_HEADLINES_LIMIT, headlinesLimit)
        
        // Get delay between headlines setting (default to 2 seconds if not set)
        val delayBetweenHeadlines = prefs.getInt("delay_between_headlines", 2)
        intent.putExtra(TtsReadingService.EXTRA_DELAY_BETWEEN_HEADLINES, delayBetweenHeadlines)
        
        // Get read body setting
        val readBody = prefs.getBoolean("read_body", false)
        intent.putExtra(TtsReadingService.EXTRA_READ_BODY, readBody)

        val mode = if (showUnreadOnly) "unread" else "all"
        intent.putExtra(TtsReadingService.EXTRA_READING_MODE, mode)
        
        // Set session type for manual reading
        intent.putExtra(TtsReadingService.EXTRA_SESSION_NAME, "Manual Reading")
        intent.putExtra(TtsReadingService.EXTRA_SESSION_TYPE, TtsReadingService.SESSION_TYPE_MANUAL)
        
        requireContext().startForegroundService(intent)
    }
    
    private fun readArticleWithTts(article: NewsArticle) {
        // Stop any existing service first
        val stopIntent = Intent(requireContext(), TtsReadingService::class.java)
        requireContext().stopService(stopIntent)
        
        // Create intent for TTS service
        val intent = Intent(requireContext(), TtsReadingService::class.java)
        
        // Pass just this one article to read
        intent.putExtra(TtsReadingService.EXTRA_SINGLE_ARTICLE_LINK, article.link)
        intent.putExtra(TtsReadingService.EXTRA_FEED_ID, selectedFeedId)
        
        // Get preference for reading the body
        val prefs = requireContext().getSharedPreferences("keynews_settings", Context.MODE_PRIVATE)
        val readBody = prefs.getBoolean("read_body", false)
        intent.putExtra(TtsReadingService.EXTRA_READ_BODY, readBody)
        
        // Set session type for single article reading
        intent.putExtra(TtsReadingService.EXTRA_SESSION_NAME, "Article Reading")
        intent.putExtra(TtsReadingService.EXTRA_SESSION_TYPE, TtsReadingService.SESSION_TYPE_SINGLE)
        
        // Start the service
        requireContext().startForegroundService(intent)
        
        Toast.makeText(context, "Reading: ${article.title}", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Update the information bar with the latest session info
     */
    private fun updateInfoBar() {
        lifecycleScope.launch {
            try {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                
                // Get all repeated sessions
                val allSessions = dataManager.database.repeatedSessionDao().getRepeatedSessionsWithRules()
                
                // Filter for sessions that have at least one active rule
                val enabledSessions = allSessions.filter { session ->
                    session.rules.any { rule -> rule.isActive }
                }
                
                // Update the info bar manager
                withContext(Dispatchers.Main) {
                    infoBarManager.updateSessionStatus(enabledSessions)
                }
                
            } catch (e: Exception) {
                Log.e("ArticlesFragment", "Error updating info bar: ${e.message}", e)
            }
        }
    }
    
    /**
     * Start periodic updates for the info bar
     */
    private fun startInfoBarUpdates() {
        lifecycleScope.launch {
            while (isResumed) {
                // Update the info bar every minute
                updateInfoBar()
                delay(60000) // 1 minute
            }
        }
    }
    
    /**
     * Show popup for managing repeated sessions
     */
    private fun showRepeatedSessionsPopup() {
        popupWindow?.dismiss()

        val popupView = LayoutInflater.from(requireContext())
        .inflate(R.layout.popup_repeated_sessions, null)

        // Updated text if needed:
        val tvTitle = popupView.findViewById<TextView>(R.id.tvPopupTitle)
        tvTitle.text = "Manage Interval Rules"

        val recyclerView = popupView.findViewById<RecyclerView>(R.id.rvPopupSessions)
        val btnApply = popupView.findViewById<Button>(R.id.btnApply)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // We'll keep a local list of items to modify
        val localItems = mutableListOf<PopupSessionItem>()

        val adapter = PopupSessionAdapter(
            onToggleChanged = { ruleId, enabled ->
                // Just set isEnabled in localItems, we confirm on "Apply"
                val item = localItems.find { it.ruleId == ruleId }
                if (item != null) {
                    item.isEnabled = enabled
                }
            },
            onOptionSelected = { ruleId, option ->
                val item = localItems.find { it.ruleId == ruleId }
                if (item != null) {
                    item.selectedOption = option
                }
            },
            onHoursMinutesSecondsChanged = { ruleId, h, m, s ->
                val item = localItems.find { it.ruleId == ruleId }
                if (item != null) {
                    item.afterHours = h
                    item.afterMinutes = m
                    item.afterSeconds = s
                }
            }
        )
        recyclerView.adapter = adapter

        // Populate
        lifecycleScope.launch {
            val dataManager = (requireActivity().application as KeyNewsApp).dataManager
            val sessions = dataManager.database.repeatedSessionDao().getRepeatedSessionsWithRules()

            // Only interval rules
            val intervalSessions = sessions.mapNotNull { sessionWithRules ->
                val intervalRules = sessionWithRules.rules.filter { it.type == RuleType.INTERVAL }
                if (intervalRules.isEmpty()) null
                    else sessionWithRules.copy(rules = intervalRules)
            }

            intervalSessions.forEach { swr ->
                val sId = swr.session.id ?: return@forEach
                swr.rules.forEach { rule ->
                    // We'll guess if it has a pending one-time start from the AlarmTimeTracker:
                    val pendingAlarm = com.example.keynews.util.AlarmTimeTracker.getNextAlarmTime(requireContext(), rule.id)
                    val pendingStart = (pendingAlarm > 0) // means it's scheduled but not triggered yet

                    localItems.add(
                        PopupSessionItem(
                            sessionId = sId,
                            sessionName = swr.session.name,
                            ruleId = rule.id,
                            ruleDescription = formatRuleDescription(rule),
                                         isEnabled = rule.isActive,
                                         pendingStart = pendingStart,
                                         selectedOption = null
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                adapter.submitList(localItems)
            }
        }

        btnApply.setOnClickListener {
            // Validate
            for (item in localItems) {
                if (item.isEnabled && !item.pendingStart) {
                    // Must have chosen an option
                    if (item.selectedOption == null) {
                        Toast.makeText(requireContext(), "Please choose how to start for rule: ${item.ruleDescription}", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
            }

            // If passes validation, apply changes
            lifecycleScope.launch(Dispatchers.IO) {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                val dao = dataManager.database.repeatedSessionDao()

                for (item in localItems) {
                    val rule = dao.getRuleById(item.ruleId) ?: continue
                    // Update the isActive state
                    rule.isActive = item.isEnabled
                    dao.updateRule(rule)

                    if (!item.isEnabled) {
                        // Cancel any alarm
                        RepeatedSessionScheduler.cancelAlarmForRule(requireContext(), rule.id)
                    } else if (!item.pendingStart) {
                        // Based on which option user selected:
                        when (item.selectedOption) {
                            StartOption.NOW -> {
                                // Immediately start the reading
                                // Then no countdown is displayed until reading finishes
                                RepeatedSessionScheduler.startSessionNow(requireContext(), rule.sessionId)
                                // We'll not store an alarm time => info bar won't show countdown
                            }
                            StartOption.LATER -> {
                                // We do a single-time alarm after H/M/S
                                val h = item.afterHours ?: 0
                                val m = item.afterMinutes ?: 0
                                val s = item.afterSeconds ?: 0
                                // Convert to total minutes or handle partial
                                val totalMillis = (h * 3600L + m * 60L + s) * 1000L
                                if (totalMillis > 0) {
                                    RepeatedSessionScheduler.scheduleOneTimeIntervalStart(
                                        context = requireContext(),
                                                                                          rule = rule,
                                                                                          delayMillis = totalMillis
                                    )
                                }
                            }
                            StartOption.TIME -> {
                                // We'll show a custom date/time picker or usage in separate code:
                                // For now, use a function that sets an alarm for some chosen time
                                // RepeatedSessionScheduler.scheduleOneTimeIntervalAt(context, rule, chosenTimeInMillis)
                                // We'll assume you have your own method to handle it.
                                // Not shown here to keep snippet short
                            }
                            else -> {}
                        }
                    } // end if enabled & not pending
                }

                withContext(Dispatchers.Main) {
                    updateInfoBar()
                    popupWindow?.dismiss()
                }
            }
        }

        popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 10f
            showAtLocation(binding.infoBar, Gravity.CENTER, 0, 0)
        }
    }


    private fun formatRuleDescription(rule: RepeatedSessionRule): String {
        return when (rule.type) {
            RuleType.INTERVAL -> {
                val interval = rule.intervalMinutes ?: 0
                when {
                    interval < 60 -> "Every $interval minutes"
                    interval % 60 == 0 -> "Every ${interval / 60} hours"
                    else -> "Every ${interval / 60}h ${interval % 60}m"
                }
            }
            RuleType.SCHEDULE -> {
                val timeOfDay = rule.timeOfDay ?: "unknown time"
                val daysOfWeek = rule.daysOfWeek?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?.joinToString(", ") { dayNumberToShortName(it) } ?: "unknown days"

                "At $timeOfDay on $daysOfWeek"
            }
            else -> "Unknown rule type"
        }
    }
    
    /**
     * Create a popup item from a session
     */
    private fun createPopupItemFromSession(session: RepeatedSessionWithRules): List<PopupSessionItem> {
        // Filter to only include interval rules
        val intervalRules = session.rules.filter { it.type == RuleType.INTERVAL }

        if (intervalRules.isEmpty()) {
            return emptyList()
        }

        // Create a PopupSessionItem for each interval rule
        return intervalRules.map { rule ->
            val ruleDesc = when (rule.type) {
                RuleType.INTERVAL -> {
                    val interval = rule.intervalMinutes ?: 0
                    when {
                        interval < 60 -> "Every $interval minutes"
                        interval % 60 == 0 -> "Every ${interval / 60} hours"
                        else -> "Every ${interval / 60}h ${interval % 60}m"
                    }
                }
                else -> "" // Should never happen since we filtered for interval rules
            }

            PopupSessionItem(
                sessionId = session.session.id!!,
                sessionName = session.session.name,
                ruleDescription = ruleDesc,
                isEnabled = rule.isActive ?: false, // Assuming you added isActive field
                ruleId = rule.id
            )
        }
    }
    
    /**
     * Convert day number to short name
     */
    private fun dayNumberToShortName(dayNumber: Int): String {
        return when (dayNumber) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
            else -> "?"
        }
    }
    
    /**
     * Toggle a session's enabled status
     */
    private fun toggleSessionEnabled(sessionId: Long, isEnabled: Boolean) {
        lifecycleScope.launch {
            try {
                val context = requireContext()
                
                if (isEnabled) {
                    // Enable all alarms - commented out until RepeatedSessionScheduler is available
                    // RepeatedSessionScheduler.rescheduleAlarmsForSession(context, sessionId)
                    Toast.makeText(context, "Enabled session $sessionId", Toast.LENGTH_SHORT).show()
                } else {
                    // Cancel all alarms - commented out until RepeatedSessionScheduler is available
                    // RepeatedSessionScheduler.cancelAlarmsForSession(context, sessionId)
                    Toast.makeText(context, "Disabled session $sessionId", Toast.LENGTH_SHORT).show()
                }
                
                // Update the info bar
                updateInfoBar()
                
            } catch (e: Exception) {
                Log.e("ArticlesFragment", "Error toggling session: ${e.message}", e)
            }
        }
    }
    
    /**
     * Updates the feed name in toolbar
     */
    private fun updateFeedNameInToolbar() {
        if (activity is MainActivity) {
            (activity as MainActivity).updateFeedNameInToolbar(selectedFeedId)
        }
    }
    
    /**
     * Shows dialog to manage read status of articles
     */
    private fun showManageReadStatusDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_manage_read_status, null)
            
        // Get references to dialog views
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupReadStatus)
        val radioMarkAllRead = dialogView.findViewById<RadioButton>(R.id.radioMarkAllRead)
        val radioMarkBeforeRead = dialogView.findViewById<RadioButton>(R.id.radioMarkBeforeRead)
        val radioMarkAllUnread = dialogView.findViewById<RadioButton>(R.id.radioMarkAllUnread)
        val radioMarkBeforeUnread = dialogView.findViewById<RadioButton>(R.id.radioMarkBeforeUnread)
        
        val layoutTimeInputsRead = dialogView.findViewById<LinearLayout>(R.id.layoutTimeInputsRead)
        val layoutTimeInputsUnread = dialogView.findViewById<LinearLayout>(R.id.layoutTimeInputsUnread)
        
        val etDaysRead = dialogView.findViewById<EditText>(R.id.etDaysRead)
        val etHoursRead = dialogView.findViewById<EditText>(R.id.etHoursRead)
        val etMinutesRead = dialogView.findViewById<EditText>(R.id.etMinutesRead)
        
        val etDaysUnread = dialogView.findViewById<EditText>(R.id.etDaysUnread)
        val etHoursUnread = dialogView.findViewById<EditText>(R.id.etHoursUnread)
        val etMinutesUnread = dialogView.findViewById<EditText>(R.id.etMinutesUnread)
        
        val btnApply = dialogView.findViewById<Button>(R.id.btnApplyReadStatus)
        
        // Initially hide time input fields
        layoutTimeInputsRead.visibility = View.GONE
        layoutTimeInputsUnread.visibility = View.GONE
        
        // Show/hide time input fields based on radio selection
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioMarkBeforeRead -> {
                    layoutTimeInputsRead.visibility = View.VISIBLE
                    layoutTimeInputsUnread.visibility = View.GONE
                }
                R.id.radioMarkBeforeUnread -> {
                    layoutTimeInputsRead.visibility = View.GONE
                    layoutTimeInputsUnread.visibility = View.VISIBLE
                }
                else -> {
                    layoutTimeInputsRead.visibility = View.GONE
                    layoutTimeInputsUnread.visibility = View.GONE
                }
            }
        }
        
        // Create and show dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
            
        // Handle apply button click
        btnApply.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            
            if (selectedId == -1) {
                Toast.makeText(requireContext(), "Please select an option", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedId == R.id.radioMarkAllRead) {
                markAllArticlesRead()
                dialog.dismiss()
            } else if (selectedId == R.id.radioMarkBeforeRead) {
                val daysValue = etDaysRead.text.toString().toIntOrNull() ?: 0
                val hoursValue = etHoursRead.text.toString().toIntOrNull() ?: 0
                val minutesValue = etMinutesRead.text.toString().toIntOrNull() ?: 0
                
                if (daysValue == 0 && hoursValue == 0 && minutesValue == 0) {
                    Toast.makeText(requireContext(), "Please enter a valid time period", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                markArticlesBeforeTimeRead(daysValue, hoursValue, minutesValue)
                dialog.dismiss()
            } else if (selectedId == R.id.radioMarkAllUnread) {
                markAllArticlesUnread()
                dialog.dismiss()
            } else if (selectedId == R.id.radioMarkBeforeUnread) {
                val daysValue = etDaysUnread.text.toString().toIntOrNull() ?: 0
                val hoursValue = etHoursUnread.text.toString().toIntOrNull() ?: 0
                val minutesValue = etMinutesUnread.text.toString().toIntOrNull() ?: 0
                
                if (daysValue == 0 && hoursValue == 0 && minutesValue == 0) {
                    Toast.makeText(requireContext(), "Please enter a valid time period", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                markArticlesBeforeTimeUnread(daysValue, hoursValue, minutesValue)
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Marks all articles in the current feed as read
     */
    private fun markAllArticlesRead() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                
                if (selectedFeedId > 0) {
                    // Get source IDs for this feed
                    val sourceRefs = dataManager.database.readingFeedDao().getSourceIdsForFeed(selectedFeedId)
                    val sourceIds = sourceRefs.map { it.sourceId }
                    
                    // Mark all articles from these sources as read
                    val count = dataManager.database.newsArticleDao().markAllArticlesReadBySourceIds(sourceIds)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Marked $count articles as read", Toast.LENGTH_SHORT).show()
                        loadArticles() // Refresh UI
                    }
                } else {
                    // Mark all articles as read if no specific feed selected
                    val count = dataManager.database.newsArticleDao().markAllArticlesRead()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Marked $count articles as read", Toast.LENGTH_SHORT).show()
                        loadArticles() // Refresh UI
                    }
                }
            } catch (e: Exception) {
                Log.e("ArticlesFragment", "Error marking articles as read: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error marking articles as read", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Marks all articles in the current feed as unread
     */
    private fun markAllArticlesUnread() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                
                if (selectedFeedId > 0) {
                    // Get source IDs for this feed
                    val sourceRefs = dataManager.database.readingFeedDao().getSourceIdsForFeed(selectedFeedId)
                    val sourceIds = sourceRefs.map { it.sourceId }
                    
                    // Mark all articles from these sources as unread
                    val count = dataManager.database.newsArticleDao().markAllArticlesUnreadBySourceIds(sourceIds)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Marked $count articles as unread", Toast.LENGTH_SHORT).show()
                        loadArticles() // Refresh UI
                    }
                } else {
                    // Mark all articles as unread if no specific feed selected
                    val count = dataManager.database.newsArticleDao().markAllArticlesUnread()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Marked $count articles as unread", Toast.LENGTH_SHORT).show()
                        loadArticles() // Refresh UI
                    }
                }
            } catch (e: Exception) {
                Log.e("ArticlesFragment", "Error marking articles as unread: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error marking articles as unread", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Marks articles published before the specified time period as read
     */
    private fun markArticlesBeforeTimeRead(days: Int, hours: Int, minutes: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                
                // Calculate cutoff time in milliseconds
                val totalMinutes = days * 24 * 60 + hours * 60 + minutes
                val cutoffTime = System.currentTimeMillis() - (totalMinutes * 60 * 1000L)
                
                if (selectedFeedId > 0) {
                    // Get source IDs for this feed
                    val sourceRefs = dataManager.database.readingFeedDao().getSourceIdsForFeed(selectedFeedId)
                    val sourceIds = sourceRefs.map { it.sourceId }
                    
                    // Mark articles before cutoff time as read
                    val count = dataManager.database.newsArticleDao().markArticlesBeforeTimeReadBySourceIds(sourceIds, cutoffTime)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Marked $count articles as read", Toast.LENGTH_SHORT).show()
                        loadArticles() // Refresh UI
                    }
                } else {
                    // TODO: Handle case when no feed is selected
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Please select a feed first", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ArticlesFragment", "Error marking articles as read: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error marking articles as read", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Marks articles published before the specified time period as unread
     */
    private fun markArticlesBeforeTimeUnread(days: Int, hours: Int, minutes: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                
                // Calculate cutoff time in milliseconds
                val totalMinutes = days * 24 * 60 + hours * 60 + minutes
                val cutoffTime = System.currentTimeMillis() - (totalMinutes * 60 * 1000L)
                
                if (selectedFeedId > 0) {
                    // Get source IDs for this feed
                    val sourceRefs = dataManager.database.readingFeedDao().getSourceIdsForFeed(selectedFeedId)
                    val sourceIds = sourceRefs.map { it.sourceId }
                    
                    // Mark articles before cutoff time as unread
                    val count = dataManager.database.newsArticleDao().markArticlesBeforeTimeUnreadBySourceIds(sourceIds, cutoffTime)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Marked $count articles as unread", Toast.LENGTH_SHORT).show()
                        loadArticles() // Refresh UI
                    }
                } else {
                    // TODO: Handle case when no feed is selected
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Please select a feed first", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ArticlesFragment", "Error marking articles as unread: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error marking articles as unread", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Handle a start option selection
     */
    private fun handleStartOption(sessionId: Long, option: StartOption) {
        when (option) {
            StartOption.NOW -> {
                // Start immediately
                sessionStartHelper.startNow(sessionId) {
                    updateInfoBar()
                }
            }
            StartOption.LATER -> {
                // Show dialog to select delay
                showDelayInputDialog(sessionId)
            }
            StartOption.TIME -> {
                // Show time picker
                showTimePickerDialog(sessionId)
            }
        }
    }
    
    /**
     * Show dialog to input delay time
     */
    private fun showDelayInputDialog(sessionId: Long) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_delay_input, null)
            
        val etMinutes = dialogView.findViewById<EditText>(R.id.etMinutes)
        
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                try {
                    val minutes = etMinutes.text.toString().toInt()
                    if (minutes > 0) {
                        sessionStartHelper.startAfterDelay(sessionId, minutes) {
                            updateInfoBar()
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Please enter a valid number of minutes",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Please enter a valid number",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show time picker dialog
     */
    private fun showTimePickerDialog(sessionId: Long) {
        val timePickerDialog = TimePickerDialogFragment.newInstance { timeString ->
            sessionStartHelper.startAtTime(sessionId, timeString) {
                updateInfoBar()
            }
        }
        timePickerDialog.show(childFragmentManager, "timePicker")
    }

    private fun applyPendingChanges() {
        lifecycleScope.launch {
            try {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager

                // Apply all pending changes
                for ((ruleId, isActive) in pendingRuleChanges) {
                    // Update the rule's active state in the database
                    withContext(Dispatchers.IO) {
                        val rule = dataManager.database.repeatedSessionDao().getRuleById(ruleId)
                        if (rule != null) {
                            // Update the rule with the new active state
                            val updatedRule = rule.copy(isActive = isActive)
                            dataManager.database.repeatedSessionDao().updateRule(updatedRule)

                            // Get the session
                            val session = dataManager.database.repeatedSessionDao()
                            .getRepeatedSessionById(rule.sessionId)

                            if (session != null) {
                                if (isActive) {
                                    // Handle special start options
                                    val startOption: String? = pendingStartOptions[ruleId]
                                    when (startOption) {
                                        "NOW" -> {
                                            RepeatedSessionScheduler.startSessionNow(requireContext(), session.id!!)
                                        }
                                        "LATER" -> {
                                            // This would be handled via a separate dialog
                                            // For now, just schedule it with default timing
                                            RepeatedSessionScheduler.scheduleAlarmForRule(
                                                requireContext(), updatedRule, session)
                                        }
                                        "TIME" -> {
                                            // This would be handled via a separate dialog
                                            // For now, just schedule it with default timing
                                            RepeatedSessionScheduler.scheduleAlarmForRule(
                                                requireContext(), updatedRule, session)
                                        }
                                        else -> {
                                            // Default behavior - just schedule alarm
                                            RepeatedSessionScheduler.scheduleAlarmForRule(
                                                requireContext(), updatedRule, session)
                                        }
                                    }
                                } else {
                                    // Cancel alarm for this rule
                                    RepeatedSessionScheduler.cancelAlarmForRule(requireContext(), ruleId)
                                }
                            }
                        }
                    }
                }

                // Update the info bar immediately to reflect changes
                withContext(Dispatchers.Main) {
                    // Force the info bar to update immediately
                    updateInfoBar()
                }

                // Clear pending changes
                pendingRuleChanges.clear()
                pendingStartOptions.clear()

            } catch (e: Exception) {
                Log.e("ArticlesFragment", "Error applying changes: ${e.message}", e)
                Toast.makeText(requireContext(), "Error applying changes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pendingStartOptions = mutableMapOf<Long, String>() // ruleId -> option

    /**
     * Remember which start option was selected for a rule
     */
    private fun rememberStartOption(ruleId: Long, option: String) {
        pendingStartOptions[ruleId] = option
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        popupWindow?.dismiss()
        
        // Clean up AI progress resources
        aiFilterProgressManager.cleanup()
        
        // Clear progress listener
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        val appContext = context
        if (appContext != null) {
            val geminiService = dataManager.getGeminiService(appContext)
            geminiService.setProgressListener(null)
        }
    }
}
// CodeCleaner_End_e3f51099-7980-4fd2-8005-8dd9d372bbb9
