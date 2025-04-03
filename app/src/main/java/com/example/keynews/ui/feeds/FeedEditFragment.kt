// CodeCleaner_Start_7931ae0a-8b3d-4660-8f07-6c3786495f22
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.R
import com.example.keynews.data.model.AiRule
import com.example.keynews.data.model.ReadingFeed
import com.example.keynews.data.model.ReadingFeedAiRuleCrossRef
import com.example.keynews.data.model.ReadingFeedKeywordRuleCrossRef
import com.example.keynews.data.model.ReadingFeedSourceCrossRef
import com.example.keynews.databinding.FragmentFeedEditBinding
import com.example.keynews.ui.ai.AiRuleSelectDialog
import com.example.keynews.ui.keywords.KeywordRuleSelectDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.keynews.MainActivity

class FeedEditFragment : Fragment() {

    companion object {
        private const val ARG_FEED_ID = "feed_id"

        fun newInstance(feedId: Long): FeedEditFragment {
            return FeedEditFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_FEED_ID, feedId)
                }
            }
        }
    }

    private var _binding: FragmentFeedEditBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SourceCheckAdapter
    private var currentFeed: ReadingFeed? = null
    private var selectedKeywordRuleIds = mutableSetOf<Long>()
    
    // AI rules
    private var whitelistAiRule: AiRule? = null
    private var blacklistAiRule: AiRule? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SourceCheckAdapter()
        binding.rvSources.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSources.adapter = adapter

        binding.btnSave.setOnClickListener { saveFeed() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnEditRules.setOnClickListener { showKeywordRuleSelector() }
        binding.btnEditWhitelistRule.setOnClickListener { showAiRuleSelector(true) }
        binding.btnEditBlacklistRule.setOnClickListener { showAiRuleSelector(false) }

        loadFeedData()
    }

    private fun loadFeedData() {
        val feedId = arguments?.getLong(ARG_FEED_ID, 0L) ?: 0L

        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            // Load feed details
            val feeds = dataManager.database.readingFeedDao().getAllFeeds()
            currentFeed = feeds.find { it.id == feedId }

            // Load all sources
            val allSources = dataManager.database.newsSourceDao().getAllSources()

            // Get sources linked to this feed
            val feedSources = dataManager.database.readingFeedDao()
                .getSourceIdsForFeed(feedId)
                .map { it.sourceId }
                .toSet()

            // Get keyword rules linked to this feed
            val keywordRules = dataManager.database.readingFeedDao().getKeywordRulesForFeed(feedId)
            selectedKeywordRuleIds = keywordRules.map { it.id }.toMutableSet()
            
            // Get AI rules linked to this feed
            whitelistAiRule = dataManager.database.readingFeedDao().getWhitelistAiRuleForFeed(feedId)
            blacklistAiRule = dataManager.database.readingFeedDao().getBlacklistAiRuleForFeed(feedId)
            
            android.util.Log.d("FeedEditFragment", "🔍 AI FILTERING: Retrieved AI rules for feed $feedId - whitelist: ${whitelistAiRule?.name}, blacklist: ${blacklistAiRule?.name}")

            withContext(Dispatchers.Main) {
                // Display feed name
                binding.etFeedName.setText(currentFeed?.name ?: "")

                // Display all sources with checkboxes
                adapter.submitList(allSources, feedSources)

                // Update keyword rules text
                updateKeywordRulesText()
                
                // Update AI rules text
                updateAiRulesText()
                
                android.util.Log.d("FeedEditFragment", "🔍 AI FILTERING: UI updated with AI rules - whitelist: ${whitelistAiRule?.name}, blacklist: ${blacklistAiRule?.name}")
            }
        }
    }

    private fun updateKeywordRulesText() {
        if (selectedKeywordRuleIds.isEmpty()) {
            binding.tvSelectedRules.text = "No rules selected"
            return
        }

        lifecycleScope.launch {
            val dataManager = (requireActivity().application as KeyNewsApp).dataManager
            val allRules = dataManager.database.keywordRuleDao().getAllRules()
            val selectedRules = allRules.filter { selectedKeywordRuleIds.contains(it.id) }

            val ruleText = if (selectedRules.isEmpty()) {
                "No rules selected"
            } else if (selectedRules.size == 1) {
                "1 rule selected: ${selectedRules[0].name}"
            } else {
                "${selectedRules.size} rules selected"
            }

            withContext(Dispatchers.Main) {
                binding.tvSelectedRules.text = ruleText
            }
        }
    }
    
    private fun updateAiRulesText() {
        // Update whitelist rule text
        if (whitelistAiRule != null) {
            binding.tvSelectedWhitelistRule.text = getString(R.string.ai_whitelist_rule_selected, whitelistAiRule?.name)
        } else {
            binding.tvSelectedWhitelistRule.text = getString(R.string.no_ai_whitelist_rule)
        }
        
        // Update blacklist rule text
        if (blacklistAiRule != null) {
            binding.tvSelectedBlacklistRule.text = getString(R.string.ai_blacklist_rule_selected, blacklistAiRule?.name)
        } else {
            binding.tvSelectedBlacklistRule.text = getString(R.string.no_ai_blacklist_rule)
        }
    }

    private fun showKeywordRuleSelector() {
        val dialog = KeywordRuleSelectDialog(
            currentSelectedRuleIds = selectedKeywordRuleIds,
            onRulesSelected = { newSelectedRuleIds ->
                selectedKeywordRuleIds.clear()
                selectedKeywordRuleIds.addAll(newSelectedRuleIds)
                updateKeywordRulesText()
            }
        )
        dialog.show(parentFragmentManager, "SelectKeywordRulesDialog")
    }
    
    private fun showAiRuleSelector(isWhitelist: Boolean) {
        val feedId = currentFeed?.id ?: return
        
        val dialog = AiRuleSelectDialog(
            feedId = feedId,
            isWhitelist = isWhitelist,
            onRuleSelected = { rule ->
                if (isWhitelist) {
                    whitelistAiRule = rule
                } else {
                    blacklistAiRule = rule
                }
                updateAiRulesText()
            }
        )
        dialog.show(parentFragmentManager, "SelectAiRuleDialog")
    }

    private fun saveFeed() {
        val newName = binding.etFeedName.text.toString().trim()
        if (newName.isEmpty()) {
            Toast.makeText(requireContext(), "Feed name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            currentFeed?.let { feed ->
                // Update feed name
                val updatedFeed = feed.copy(name = newName)
                dataManager.database.readingFeedDao().insertReadingFeed(updatedFeed)

                // Get current source selections
                val selectedSourceIds = adapter.getSelectedSourceIds()

                // Get existing linked sources
                val existingLinks = dataManager.database.readingFeedDao()
                    .getSourceIdsForFeed(feed.id)

                // Remove sources that were unselected
                val sourcesToRemove = existingLinks.filter {
                    !selectedSourceIds.contains(it.sourceId)
                }
                for (link in sourcesToRemove) {
                    dataManager.database.readingFeedDao()
                        .deleteFeedSourceCrossRefByIds(feed.id, link.sourceId)
                }

                // Add newly selected sources
                val existingSourceIds = existingLinks.map { it.sourceId }.toSet()
                val sourcesToAdd = selectedSourceIds.filter { !existingSourceIds.contains(it) }
                for (sourceId in sourcesToAdd) {
                    dataManager.database.readingFeedDao().insertFeedSourceCrossRef(
                        ReadingFeedSourceCrossRef(feed.id, sourceId)
                    )
                }

                // Update keyword rule associations
                // First, get existing associations
                val existingRuleLinks = dataManager.database.readingFeedDao()
                    .getKeywordRuleIdsForFeed(feed.id)
                    .map { it.ruleId }
                    .toSet()

                // Remove rules that were unselected
                val rulesToRemove = existingRuleLinks.filter { !selectedKeywordRuleIds.contains(it) }
                for (ruleId in rulesToRemove) {
                    dataManager.database.readingFeedDao()
                        .deleteFeedKeywordRuleCrossRefByIds(feed.id, ruleId)
                }

                // Add newly selected rules
                val rulesToAdd = selectedKeywordRuleIds.filter { !existingRuleLinks.contains(it) }
                for (ruleId in rulesToAdd) {
                    dataManager.database.readingFeedDao().insertFeedKeywordRuleCrossRef(
                        ReadingFeedKeywordRuleCrossRef(feed.id, ruleId)
                    )
                }

                // Update AI rule associations
                // First, delete existing AI rule associations
                dataManager.database.readingFeedDao().deleteAllAiRuleAssociationsForFeed(feed.id)
                android.util.Log.d("FeedEditFragment", "🔍 AI FILTERING: Deleted existing AI rule associations for feed ${feed.id}")
                
                // Add the whitelist rule if selected
                whitelistAiRule?.let { rule ->
                    dataManager.database.readingFeedDao().insertFeedAiRuleCrossRef(
                        ReadingFeedAiRuleCrossRef(feed.id, rule.id, isWhitelist = true)
                    )
                    android.util.Log.d("FeedEditFragment", "🔍 AI FILTERING: Added whitelist rule ${rule.id} (${rule.name}) to feed ${feed.id}")
                }
                
                // Add the blacklist rule if selected
                blacklistAiRule?.let { rule ->
                    dataManager.database.readingFeedDao().insertFeedAiRuleCrossRef(
                        ReadingFeedAiRuleCrossRef(feed.id, rule.id, isWhitelist = false)
                    )
                    android.util.Log.d("FeedEditFragment", "🔍 AI FILTERING: Added blacklist rule ${rule.id} (${rule.name}) to feed ${feed.id}")
                }

                withContext(Dispatchers.Main) {
                    // Refresh the drawer menu in MainActivity
                    (activity as? MainActivity)?.loadFeedsIntoDrawer()

                    Toast.makeText(requireContext(), "Feed updated", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }

    private fun confirmDelete() {
        currentFeed?.let { feed ->
            lifecycleScope.launch {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                val allFeeds = dataManager.database.readingFeedDao().getAllFeeds()

                val message = if (feed.isDefault && allFeeds.size == 1) {
                    "You are deleting the only feed. You won't see news unless you create another feed."
                } else {
                    "Are you sure you want to delete this feed?"
                }

                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Delete Feed")
                        .setMessage(message)
                        .setPositiveButton("Delete") { _, _ ->
                            deleteFeed(feed.id)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    private fun deleteFeed(feedId: Long) {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            // Delete keyword rule associations
            dataManager.database.readingFeedDao().deleteAllKeywordRuleAssociationsForFeed(feedId)
            
            // Delete AI rule associations
            dataManager.database.readingFeedDao().deleteAllAiRuleAssociationsForFeed(feedId)
            
            // Delete the feed
            dataManager.database.readingFeedDao().deleteFeedById(feedId)

            withContext(Dispatchers.Main) {
                // Refresh the drawer menu in MainActivity
                (activity as? MainActivity)?.loadFeedsIntoDrawer()

                Toast.makeText(requireContext(), "Feed deleted", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
// CodeCleaner_End_7931ae0a-8b3d-4660-8f07-6c3786495f22
