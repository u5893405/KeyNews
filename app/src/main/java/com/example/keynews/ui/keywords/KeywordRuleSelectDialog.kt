package com.example.keynews.ui.keywords

// CodeCleaner_Start_521b3de5-b696-4fb7-8161-90873b20f56d
import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.databinding.DialogSelectKeywordRulesBinding
import kotlinx.coroutines.launch

class KeywordRuleSelectDialog(
    private val currentSelectedRuleIds: Set<Long>,
    private val onRulesSelected: (Set<Long>) -> Unit
) : DialogFragment() {

    private var _binding: DialogSelectKeywordRulesBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: KeywordRuleSelectAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSelectKeywordRulesBinding.inflate(layoutInflater)

        val dialog = Dialog(requireContext())
        dialog.setContentView(binding.root)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        setupRecyclerView()
        loadKeywordRules()
        
        binding.btnApply.setOnClickListener {
            onRulesSelected(adapter.getSelectedRuleIds())
            dismiss()
        }
        
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        return dialog
    }

    private fun setupRecyclerView() {
        adapter = KeywordRuleSelectAdapter()
        binding.rvKeywordRules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvKeywordRules.adapter = adapter
    }

    private fun loadKeywordRules() {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            val rules = dataManager.database.keywordRuleDao().getAllRules()
            adapter.submitList(rules, currentSelectedRuleIds)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
// CodeCleaner_End_521b3de5-b696-4fb7-8161-90873b20f56d

