package com.example.keynews

// CodeCleaner_Start_b462e7cf-df17-4b7b-b1f0-a4f6be5224a8
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.keynews.databinding.ActivityMainBinding
import com.example.keynews.ui.ai.AiRulesFragment
import com.example.keynews.ui.articles.ArticlesFragment
import com.example.keynews.ui.feeds.ReadingFeedsFragment
import com.example.keynews.ui.keywords.KeywordRulesFragment
import com.example.keynews.ui.readlater.ReadLaterFragment
import com.example.keynews.ui.rep_session.RepeatedSessionFragment
import com.example.keynews.ui.settings.SettingsFragment
import com.example.keynews.ui.sources.NewsSourcesFragment
import com.google.android.material.navigation.NavigationView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var tvFeedName: android.widget.TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = binding.drawerLayout
        navigationView = binding.navigationView

        // Set up the toolbar
        toolbar = binding.toolbarLayout.toolbar
        setSupportActionBar(toolbar)
        
        // Get reference to feed name TextView
        tvFeedName = toolbar.findViewById(R.id.tvFeedName)

        // Set up the drawer toggle (hamburger menu)
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupDrawerMenu()

        if (savedInstanceState == null) {
            // Load the main screen (ArticlesFragment) without adding to back stack
            loadFragment(ArticlesFragment(), addToBackStack = false)
        }
    }

    override fun onBackPressed() {
        // If the navigation drawer is open, close it first
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            // Check the current fragment
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main_content_frame)
            
            // If we're not on the main screen (ArticlesFragment), go directly there
            if (currentFragment != null && currentFragment !is ArticlesFragment) {
                loadFragment(ArticlesFragment(), addToBackStack = false)
            } else if (supportFragmentManager.backStackEntryCount > 0) {
                // If there are entries in back stack, pop them
                supportFragmentManager.popBackStack()
            } else {
                // Otherwise, exit app (default behavior)
                super.onBackPressed()
            }
        }
    }

    private fun setupDrawerMenu() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_news_sources -> {
                    loadFragment(NewsSourcesFragment())
                    true
                }
                R.id.nav_reading_feeds -> {
                    loadFragment(ReadingFeedsFragment())
                    true
                }
                R.id.nav_keyword_rules -> {
                    loadFragment(KeywordRulesFragment())
                    true
                }
                R.id.nav_ai_rules -> {
                    loadFragment(AiRulesFragment())
                    true
                }
                R.id.nav_repeated_sessions -> {
                    loadFragment(RepeatedSessionFragment())
                    true
                }
                R.id.nav_read_later -> {
                    loadFragment(ReadLaterFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> {
                    // Check if it's a dynamically added feed
                    if (menuItem.groupId == FEED_GROUP_ID) {
                        val feedId = menuItem.itemId.toLong()
                        onFeedSelected(feedId)
                        true
                    } else false
                }
            }.also {
                drawerLayout.closeDrawers()
            }
        }

        // Initial load of feeds
        loadFeedsIntoDrawer()
    }

    private val FEED_GROUP_ID = 100

    fun loadFeedsIntoDrawer() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allFeeds = (application as KeyNewsApp).dataManager.database.readingFeedDao().getAllFeeds()

            withContext(Dispatchers.Main) {
                // ONLY modify the dynamically added feeds, not the main menu
                val menu = navigationView.menu
                
                // Remove existing dynamic feed items
                menu.removeGroup(FEED_GROUP_ID)
                
                // Only add feeds if we have any
                if (allFeeds.isNotEmpty()) {
                    // Add all feeds as menu items without a divider
                    // The NavigationView will automatically add its own divider between groups
                    for (feed in allFeeds) {
                        val displayName = feed.name
                        val feedItem = menu.add(FEED_GROUP_ID, feed.id.toInt(), Menu.CATEGORY_SECONDARY, displayName)
                        
                        // Set icon if default
                        if (feed.isDefault) {
                            feedItem.setIcon(android.R.drawable.btn_star_big_on)
                        }
                    }
                }
            }
        }
    }

    private fun loadFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        // If the fragment is a main navigation level item, clear the back stack first
        if (!addToBackStack || fragment is ArticlesFragment
            || fragment is NewsSourcesFragment
            || fragment is ReadingFeedsFragment
            || fragment is KeywordRulesFragment
            || fragment is AiRulesFragment
            || fragment is RepeatedSessionFragment
            || fragment is ReadLaterFragment
            || fragment is SettingsFragment) {
            // Clear back stack for main level navigation
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

            val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.main_content_frame, fragment)
            // Only add ArticlesFragment to back stack if it's explicitly requested
            if (addToBackStack && !(fragment is ArticlesFragment)) {
                transaction.addToBackStack(null)
            }
            transaction.commit()
            } else {
                // For nested fragments, add to back stack normally
                val transaction = supportFragmentManager.beginTransaction()
                .replace(R.id.main_content_frame, fragment)
                if (addToBackStack) {
                    transaction.addToBackStack(null)
                }
                transaction.commit()
            }
    }

    private fun onFeedSelected(feedId: Long) {
        // Update feed name in toolbar
        updateFeedNameInToolbar(feedId)
        
        val fragment = ArticlesFragment()
        val bundle = Bundle().apply { putLong("selectedFeedId", feedId) }
        fragment.arguments = bundle
        loadFragment(fragment)
    }
    
    /**
     * Updates the feed name displayed in the toolbar
     */
    fun updateFeedNameInToolbar(feedId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            // Only attempt to get feed name if we have a valid feed ID
            val feedName = if (feedId > 0) {
                val feed = (application as KeyNewsApp).dataManager.database.readingFeedDao().getReadingFeedById(feedId)
                feed?.name ?: ""
            } else {
                "" // No feed selected, don't show any name
            }
            
            withContext(Dispatchers.Main) {
                if (feedName.isNotEmpty()) {
                    tvFeedName.text = getString(R.string.current_feed, feedName)
                    tvFeedName.visibility = View.VISIBLE
                } else {
                    tvFeedName.text = ""
                    tvFeedName.visibility = View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Always refresh feeds to ensure they're up-to-date
        loadFeedsIntoDrawer()
    }
}
// CodeCleaner_End_b462e7cf-df17-4b7b-b1f0-a4f6be5224a8

