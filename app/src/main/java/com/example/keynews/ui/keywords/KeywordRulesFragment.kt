package com.example.keynews.ui.keywords
// CodeCleaner_Start_c371f61d-13b0-4429-8046-90b36ddf626f
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.data.model.KeywordItem
import com.example.keynews.data.model.KeywordRule
import com.example.keynews.databinding.FragmentKeywordRulesBinding
import kotlinx.coroutines.launch

class KeywordRulesFragment : Fragment() {

    private var _binding: FragmentKeywordRulesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: KeywordRulesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeywordRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = KeywordRulesAdapter(
            onRuleClicked = { rule -> editRule(rule) },
            onDeleteClick = { ruleId -> deleteRule(ruleId) }
        )

        binding.rvKeywordRules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvKeywordRules.adapter = adapter

        binding.btnAddRule.setOnClickListener {
            showAddRuleDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        loadRules()
    }

    private fun loadRules() {
        val dm = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            val allRules = dm.database.keywordRuleDao().getAllRules()
            adapter.submitList(allRules)
        }
    }

    private fun showAddRuleDialog() {
        val dialog = KeywordRuleDialogFragment(
            onSaveClicked = { rule, keywords ->
                saveRule(rule, keywords)
            }
        )
        dialog.show(parentFragmentManager, "AddRuleDialog")
    }

    private fun editRule(rule: KeywordRule) {
        val dialog = KeywordRuleDialogFragment(
            existingRule = rule,
            onSaveClicked = { updatedRule, keywords ->
                saveRule(updatedRule, keywords)
            },
            onDeleteClicked = { ruleToDelete ->
                deleteRule(ruleToDelete.id)
            }
        )
        dialog.show(parentFragmentManager, "EditRuleDialog")
    }

    private fun saveRule(rule: KeywordRule, keywords: List<KeywordItem>) {
        val dm = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            // First save or update the rule
            val ruleId = dm.database.keywordRuleDao().insertRule(rule)
            
            // If it's a new rule, update the rule ID in keywords
            val keywordsToSave = if (rule.id == 0L) {
                keywords.map { it.copy(ruleId = ruleId) }
            } else {
                keywords
            }
            
            // Delete existing keywords for this rule (if any)
            if (rule.id != 0L) {
                val existingKeywords = dm.database.keywordRuleDao().getKeywordsForRule(rule.id)
                for (keyword in existingKeywords) {
                    // Only delete keywords that are not in the updated list
                    if (keywordsToSave.none { it.id == keyword.id }) {
                        dm.database.keywordRuleDao().deleteKeyword(keyword)
                    }
                }
            }
            
            // Save or update keywords
            for (keyword in keywordsToSave) {
                // If keyword has a temporary ID (negative), insert it as new
                if (keyword.id < 0) {
                    dm.database.keywordRuleDao().insertKeyword(
                        keyword.copy(id = 0, ruleId = ruleId)
                    )
                } else if (keyword.id > 0) {
                    // Update existing keyword
                    dm.database.keywordRuleDao().insertKeyword(keyword)
                }
            }
            
            loadRules()
        }
    }

    private fun deleteRule(ruleId: Long) {
        val dm = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            // First, delete all associated keywords
            val keywords = dm.database.keywordRuleDao().getKeywordsForRule(ruleId)
            for (keyword in keywords) {
                dm.database.keywordRuleDao().deleteKeyword(keyword)
            }
            
            // Then delete the rule itself
            dm.database.keywordRuleDao().deleteRuleById(ruleId)
            
            // Finally, remove any associations with reading feeds
            val readingFeedDao = dm.database.readingFeedDao()
            // Get all associations for this rule
            // Note: We need to add a method to ReadingFeedDao to get these
            
            loadRules()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
// CodeCleaner_End_c371f61d-13b0-4429-8046-90b36ddf626f
