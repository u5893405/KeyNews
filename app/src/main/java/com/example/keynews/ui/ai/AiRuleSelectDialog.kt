package com.example.keynews.ui.ai

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.R
import com.example.keynews.data.model.AiRule
import com.example.keynews.data.model.ReadingFeedAiRuleCrossRef
import com.example.keynews.databinding.DialogSelectAiRuleBinding
import kotlinx.coroutines.launch

class AiRuleSelectDialog(
    private val feedId: Long,
    private val isWhitelist: Boolean,
    private val onRuleSelected: (AiRule?) -> Unit
) : DialogFragment() {

    private var _binding: DialogSelectAiRuleBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AiRuleSelectAdapter
    private var selectedRuleId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSelectAiRuleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set the title based on whitelist/blacklist
        binding.tvTitle.text = if (isWhitelist) {
            getString(R.string.select_whitelist_rule)
        } else {
            getString(R.string.select_blacklist_rule)
        }

        setupRecyclerView()
        loadCurrentSelection()
        loadRules()

        // Set up buttons
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnApply.setOnClickListener { applySelection() }
    }

    private fun setupRecyclerView() {
        binding.rvRules.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadCurrentSelection() {
        val dm = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            val aiRuleRefs = dm.database.readingFeedDao().getAiRuleRefsForFeed(feedId)
            val currentRule = aiRuleRefs.find { it.isWhitelist == isWhitelist }
            selectedRuleId = currentRule?.ruleId

            // Now load the rules
            loadRules()
        }
    }

    private fun loadRules() {
        val dm = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            // Get all rules of the appropriate type (whitelist/blacklist)
            val allRules = dm.database.aiRuleDao().getAllRules()
                .filter { it.isWhitelist == isWhitelist }

            adapter = AiRuleSelectAdapter(
                onRuleSelected = { rule ->
                    // Update the selected rule
                    selectedRuleId = rule?.id
                    
                    // Refresh the adapter to update selection
                    adapter.notifyDataSetChanged()
                },
                selectedRuleId = selectedRuleId,
                showNoneOption = true
            )
            
            binding.rvRules.adapter = adapter
            adapter.submitList(allRules)

            // Show message if no rules available
            if (allRules.isEmpty()) {
                binding.tvNoRules.visibility = View.VISIBLE
                binding.rvRules.visibility = View.GONE
                binding.btnApply.isEnabled = false
            } else {
                binding.tvNoRules.visibility = View.GONE
                binding.rvRules.visibility = View.VISIBLE
                binding.btnApply.isEnabled = true
            }
        }
    }

    private fun applySelection() {
        val dm = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            try {
                // First, delete any existing rule of this type
                val currentRules = dm.database.readingFeedDao().getAiRuleRefsForFeed(feedId)
                val currentRule = currentRules.find { it.isWhitelist == isWhitelist }
                
                if (currentRule != null) {
                    dm.database.readingFeedDao().deleteFeedAiRuleCrossRef(currentRule)
                }
                
                // If a rule is selected, add the new association
                if (selectedRuleId != null) {
                    val selectedRule = dm.database.aiRuleDao().getRuleById(selectedRuleId!!)
                    if (selectedRule != null) {
                        val ref = ReadingFeedAiRuleCrossRef(
                            feedId = feedId,
                            ruleId = selectedRuleId!!,
                            isWhitelist = isWhitelist
                        )
                        dm.database.readingFeedDao().insertFeedAiRuleCrossRef(ref)
                        onRuleSelected(selectedRule)
                    }
                } else {
                    // No rule selected
                    onRuleSelected(null)
                }
                
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
