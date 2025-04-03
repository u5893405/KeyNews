package com.example.keynews.ui.readlater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.R
import com.example.keynews.data.model.NewsArticle
import com.example.keynews.data.model.ReadingFeed
import com.example.keynews.databinding.FragmentReadLaterBinding
import com.example.keynews.service.TtsReadingService
import com.example.keynews.ui.articles.ArticlesAdapter
import com.example.keynews.ui.articles.InfoBarManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadLaterFragment : Fragment() {

    private var _binding: FragmentReadLaterBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ArticlesAdapter
    private var showUnreadOnly = true
    private var filterSourceId: Long = 0L
    private var filterFeedId: Long = 0L

    // Info bar related members
    private lateinit var infoBarManager: InfoBarManager

    companion object {
        private const val TAG = "ReadLaterFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadLaterBinding.inflate(inflater, container, false)
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

        binding.rvArticles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvArticles.adapter = adapter

        // Set the article body length limit from preferences
        val prefs = requireContext().getSharedPreferences("keynews_settings", Context.MODE_PRIVATE)
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

        // Stop button stops TTS reading
        binding.stopButton.setOnClickListener {
            val stopIntent = Intent(requireContext(), TtsReadingService::class.java)
            requireContext().stopService(stopIntent)
            // Remove highlighting when stopping manually
            adapter.setCurrentlyReadingLink(null)
            // Reset reading progress
            infoBarManager.resetReadingProgress()
        }

        // Play button starts manual reading session
        binding.btnManualSession.setOnClickListener {
            startManualTtsReading()
        }

        // Manage read status button
        binding.btnManageReadStatus.setOnClickListener {
            showManageReadStatusPopup(it)
        }

        // Filter button
        binding.btnFilterSource.setOnClickListener {
            showFilterPopup(it)
        }

        // Scroll to top FAB
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
        registerReadingReceiver()

        // Refresh article body length setting from preferences
        val prefs = requireContext().getSharedPreferences("keynews_settings", Context.MODE_PRIVATE)
        val bodyLengthLimit = try {
            prefs.getInt("article_body_length", 120)
        } catch (e: ClassCastException) {
            try {
                val value = prefs.getFloat("article_body_length", 120f)
                value.toInt().also {
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
    }

    override fun onPause() {
        super.onPause()
        unregisterReadingReceiver()

        // Clean up info bar resources
        infoBarManager.cleanup()
    }

    private fun registerReadingReceiver() {
        val filter = IntentFilter().apply {
            addAction(TtsReadingService.ACTION_READING_PROGRESS)
            addAction(TtsReadingService.ACTION_READING_DONE)
        }
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(readingProgressReceiver, filter)
    }

    private fun unregisterReadingReceiver() {
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(readingProgressReceiver)
    }

    private val readingProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                TtsReadingService.ACTION_READING_PROGRESS -> {
                    val link = intent.getStringExtra(TtsReadingService.EXTRA_ARTICLE_LINK)
                    val currentIndex = intent.getIntExtra("current_index", 0)
                    val total = intent.getIntExtra("total_articles", 0)
                    adapter.setCurrentlyReadingLink(link)

                    // Update reading progress in info bar
                    if (currentIndex > 0 && total > 0) {
                        infoBarManager.updateReadingProgress(currentIndex, total)
                    }
                }
                TtsReadingService.ACTION_READING_DONE -> {
                    adapter.setCurrentlyReadingLink(null)
                    loadArticles() // Refresh list in case read status changed
                    infoBarManager.resetReadingProgress()
                }
            }
        }
    }

    /**
     * Loads the read later articles based on current filters
     */
    private fun loadArticles() {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            try {
                // Get read later articles based on filters
                val articles = when {
                    // Filter by source ID
                    filterSourceId > 0 -> {
                        if (showUnreadOnly) {
                            dataManager.database.newsArticleDao().getUnreadReadLaterArticlesBySource(filterSourceId)
                        } else {
                            dataManager.database.newsArticleDao().getReadLaterArticlesBySource(filterSourceId)
                        }
                    }
                    // Filter by feed ID
                    filterFeedId > 0 -> {
                        val readLaterArticles = dataManager.database.newsArticleDao().getReadLaterArticlesByFeed(filterFeedId)
                        if (showUnreadOnly) {
                            readLaterArticles.filter { !it.isRead }
                        } else {
                            readLaterArticles
                        }
                    }
                    // No filter - get all read later articles
                    else -> {
                        if (showUnreadOnly) {
                            dataManager.database.newsArticleDao().getUnreadReadLaterArticles()
                        } else {
                            dataManager.database.newsArticleDao().getReadLaterArticles()
                        }
                    }
                }

                // Get sources for the displayed articles
                val uniqueSourceIds = articles.map { it.sourceId }.distinct()
                val sources = if (uniqueSourceIds.isNotEmpty()) {
                    dataManager.database.newsSourceDao().getSourcesByIds(uniqueSourceIds)
                } else {
                    emptyList()
                }

                // Update the adapter
                withContext(Dispatchers.Main) {
                    adapter.submitList(articles)
                    adapter.updateSources(sources)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading read later articles: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading articles: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Opens the article URL in a browser
     */
    private fun openArticleUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    /**
     * Marks an article as read/unread
     */
    private fun markRead(article: NewsArticle) {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            // Toggle read state
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
            // Toggle read later status (keeping the original feed ID if removing)
            val newState = !article.isReadLater
            val feedId = if (newState) article.readLaterFeedId else null
            dataManager.database.newsArticleDao().markReadLater(article.link, newState, feedId)

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

    /**
     * Start TTS reading session for read later articles
     */
    private fun startManualTtsReading() {
        val intent = Intent(requireContext(), TtsReadingService::class.java)

        // Set session parameters
        intent.putExtra(TtsReadingService.EXTRA_SESSION_TYPE, TtsReadingService.SESSION_TYPE_MANUAL)
        intent.putExtra(TtsReadingService.EXTRA_SESSION_NAME, "Read Later Reading")
        intent.putExtra("reading_type", TtsReadingService.READING_TYPE_READ_LATER)

        // Pass filter parameters
        intent.putExtra("show_unread_only", showUnreadOnly)
        intent.putExtra(TtsReadingService.EXTRA_FILTER_SOURCE_ID, filterSourceId)
        intent.putExtra(TtsReadingService.EXTRA_FILTER_FEED_ID, filterFeedId)

        // Get settings from preferences
        val prefs = requireContext().getSharedPreferences("keynews_settings", Context.MODE_PRIVATE)
        val headlinesLimit = prefs.getInt("headlines_per_session", 10)
        intent.putExtra(TtsReadingService.EXTRA_HEADLINES_LIMIT, headlinesLimit)

        val delayBetweenHeadlines = prefs.getInt("delay_between_headlines", 2)
        intent.putExtra(TtsReadingService.EXTRA_DELAY_BETWEEN_HEADLINES, delayBetweenHeadlines)

        val readBody = prefs.getBoolean("read_body", false)
        intent.putExtra(TtsReadingService.EXTRA_READ_BODY, readBody)

        // Start the service
        requireContext().startForegroundService(intent)
    }

    /**
     * Show popup for managing read status
     */
    private fun showManageReadStatusPopup(view: View) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menu.add(0, 1, 0, "Mark all articles as already read")
        popupMenu.menu.add(0, 2, 0, "Mark all articles as not read yet")

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    markAllArticlesRead()
                    true
                }
                2 -> {
                    markAllArticlesUnread()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    /**
     * Show popup for filtering read later articles
     */
    private fun showFilterPopup(view: View) {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            try {
                // Get all read later articles to extract available filters
                val readLaterArticles = dataManager.database.newsArticleDao().getReadLaterArticles()

                // Extract unique feed IDs and source IDs
                val uniqueFeedIds = readLaterArticles.mapNotNull { it.readLaterFeedId }.distinct()
                val uniqueSourceIds = readLaterArticles.map { it.sourceId }.distinct()

                // Get feed and source names
                val feeds = if (uniqueFeedIds.isNotEmpty()) {
                    dataManager.database.readingFeedDao().getFeedsByIds(uniqueFeedIds)
                } else {
                    emptyList()
                }

                val sources = if (uniqueSourceIds.isNotEmpty()) {
                    dataManager.database.newsSourceDao().getSourcesByIds(uniqueSourceIds)
                } else {
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    val popupMenu = PopupMenu(requireContext(), view)

                    // Add "Everything" option
                    popupMenu.menu.add(0, 0, 0, "Everything")

                    // Add feed options
                    if (feeds.isNotEmpty()) {
                        val feedsSubMenu = popupMenu.menu.addSubMenu("By Reading Feed")
                        for (feed in feeds) {
                            feedsSubMenu.add(1, feed.id!!.toInt(), 0, feed.name)
                        }
                    }

                    // Add source options
                    if (sources.isNotEmpty()) {
                        val sourcesSubMenu = popupMenu.menu.addSubMenu("By News Source")
                        for (source in sources) {
                            sourcesSubMenu.add(2, source.id.toInt(), 0, source.name)
                        }
                    }

                    popupMenu.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.groupId) {
                            0 -> { // Everything
                                filterFeedId = 0L
                                filterSourceId = 0L
                                loadArticles()
                                true
                            }
                            1 -> { // Feed
                                filterFeedId = menuItem.itemId.toLong()
                                filterSourceId = 0L
                                loadArticles()
                                true
                            }
                            2 -> { // Source
                                filterSourceId = menuItem.itemId.toLong()
                                filterFeedId = 0L
                                loadArticles()
                                true
                            }
                            else -> false
                        }
                    }

                    popupMenu.show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating filter menu: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error creating filter menu", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Marks all read later articles as read
     */
    private fun markAllArticlesRead() {
        lifecycleScope.launch {
            val dataManager = (requireActivity().application as KeyNewsApp).dataManager
            try {
                val readLaterArticles = when {
                    filterSourceId > 0 -> {
                        dataManager.database.newsArticleDao().getReadLaterArticlesBySource(filterSourceId)
                    }
                    filterFeedId > 0 -> {
                        dataManager.database.newsArticleDao().getReadLaterArticlesByFeed(filterFeedId)
                    }
                    else -> {
                        dataManager.database.newsArticleDao().getReadLaterArticles()
                    }
                }

                // Mark each article as read
                var count = 0
                for (article in readLaterArticles) {
                    if (!article.isRead) {
                        dataManager.database.newsArticleDao().markRead(article.link, true)
                        count++
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Marked $count articles as read", Toast.LENGTH_SHORT).show()
                    loadArticles()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking articles read: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error marking articles read", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Marks all read later articles as unread
     */
    private fun markAllArticlesUnread() {
        lifecycleScope.launch {
            val dataManager = (requireActivity().application as KeyNewsApp).dataManager
            try {
                val readLaterArticles = when {
                    filterSourceId > 0 -> {
                        dataManager.database.newsArticleDao().getReadLaterArticlesBySource(filterSourceId)
                    }
                    filterFeedId > 0 -> {
                        dataManager.database.newsArticleDao().getReadLaterArticlesByFeed(filterFeedId)
                    }
                    else -> {
                        dataManager.database.newsArticleDao().getReadLaterArticles()
                    }
                }

                // Mark each article as unread
                var count = 0
                for (article in readLaterArticles) {
                    if (article.isRead) {
                        dataManager.database.newsArticleDao().markRead(article.link, false)
                        count++
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Marked $count articles as unread", Toast.LENGTH_SHORT).show()
                    loadArticles()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking articles unread: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error marking articles unread", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Updates the info bar with current session information
     */
    private fun updateInfoBar() {
        // This would update info about any active sessions - reusing same code as in ArticlesFragment
        // For now, keeping it simple
    }

    /**
     * Read an article with TTS
     */
    private fun readArticleWithTts(article: NewsArticle) {
        // Stop any existing service first
        val stopIntent = Intent(requireContext(), TtsReadingService::class.java)
        requireContext().stopService(stopIntent)

        // Create intent for TTS service
        val intent = Intent(requireContext(), TtsReadingService::class.java)

        // Pass just this one article to read
        intent.putExtra(TtsReadingService.EXTRA_SINGLE_ARTICLE_LINK, article.link)

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

    override fun onDestroyView() {
        super.onDestroyView()
        infoBarManager.cleanup()
        _binding = null
    }
}
