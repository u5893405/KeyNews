package com.example.keynews.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.data.model.AiRule
import com.example.keynews.databinding.FragmentAiRulesBinding
import kotlinx.coroutines.launch

class AiRulesFragment : Fragment() {

    private var _binding: FragmentAiRulesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AiRulesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = AiRulesAdapter(
            onRuleClicked = { rule -> editRule(rule) },
            onDeleteClick = { ruleId -> deleteRule(ruleId) }
        )

        binding.rvAiRules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAiRules.adapter = adapter

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
            val allRules = dm.database.aiRuleDao().getAllRules()
            
            if (allRules.isEmpty()) {
                binding.tvNoRules.visibility = View.VISIBLE
                binding.rvAiRules.visibility = View.GONE
            } else {
                binding.tvNoRules.visibility = View.GONE
                binding.rvAiRules.visibility = View.VISIBLE
                adapter.submitList(allRules)
            }
        }
    }

    private fun showAddRuleDialog() {
        val dialog = AiRuleDialogFragment(
            onSaveClicked = { rule ->
                saveRule(rule)
            }
        )
        dialog.show(parentFragmentManager, "AddRuleDialog")
    }

    private fun editRule(rule: AiRule) {
        val dialog = AiRuleDialogFragment(
            existingRule = rule,
            onSaveClicked = { updatedRule ->
                saveRule(updatedRule)
            },
            onDeleteClicked = { ruleToDelete ->
                deleteRule(ruleToDelete.id)
            }
        )
        dialog.show(parentFragmentManager, "EditRuleDialog")
    }

    private fun saveRule(rule: AiRule) {
        val dm = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            dm.database.aiRuleDao().insertRule(rule)
            loadRules()
        }
    }

    private fun deleteRule(ruleId: Long) {
        val dm = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            // Delete all feed associations for this rule
            dm.database.readingFeedDao().deleteAllFeedAssociationsForAiRule(ruleId)
            
            // Delete the rule itself
            dm.database.aiRuleDao().deleteRuleById(ruleId)
            
            loadRules()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
