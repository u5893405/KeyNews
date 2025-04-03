package com.example.keynews.ui.ai

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.keynews.data.model.AiRule
import com.example.keynews.databinding.DialogAddAiRuleBinding

class AiRuleDialogFragment(
    private val existingRule: AiRule? = null,
    private val onSaveClicked: (AiRule) -> Unit,
    private val onDeleteClicked: ((AiRule) -> Unit)? = null
) : DialogFragment() {

    private var _binding: DialogAddAiRuleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddAiRuleBinding.inflate(layoutInflater)

        // Use fullscreen dialog
        val dialog = Dialog(requireContext(), android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        dialog.setContentView(binding.root)
        
        // Set dialog to match parent width and height
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

        setupUI()
        loadExistingData()

        return dialog
    }

    private fun setupUI() {
        // Set title depending on whether we're adding or editing
        binding.tvTitle.text = if (existingRule == null) {
            getString(com.example.keynews.R.string.add_ai_rule)
        } else {
            getString(com.example.keynews.R.string.edit_ai_rule)
        }

        // Save button
        binding.btnSave.setOnClickListener {
            saveRule()
        }

        // Cancel button
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        // Delete button (only visible when editing)
        if (existingRule != null && onDeleteClicked != null) {
            binding.btnDelete.visibility = android.view.View.VISIBLE
            binding.btnDelete.setOnClickListener {
                // Confirm deletion
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Rule")
                    .setMessage("Are you sure you want to delete this rule?")
                    .setPositiveButton("Delete") { _, _ ->
                        onDeleteClicked.invoke(existingRule)
                        dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun loadExistingData() {
        if (existingRule != null) {
            binding.etRuleName.setText(existingRule.name)
            binding.etRuleText.setText(existingRule.ruleText)
            binding.rbWhitelist.isChecked = existingRule.isWhitelist
            binding.rbBlacklist.isChecked = !existingRule.isWhitelist
        }
    }

    private fun saveRule() {
        val name = binding.etRuleName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a rule name", Toast.LENGTH_SHORT).show()
            return
        }

        val ruleText = binding.etRuleText.text.toString().trim()
        if (ruleText.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter rule text", Toast.LENGTH_SHORT).show()
            return
        }

        val isWhitelist = binding.rbWhitelist.isChecked

        val rule = existingRule?.copy(
            name = name,
            ruleText = ruleText,
            isWhitelist = isWhitelist
        ) ?: AiRule(
            name = name,
            ruleText = ruleText,
            isWhitelist = isWhitelist
        )

        onSaveClicked(rule)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
