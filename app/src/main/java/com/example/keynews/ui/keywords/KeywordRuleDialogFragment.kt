package com.example.keynews.ui.keywords
// CodeCleaner_Start_5e4f1ac6-c4e9-4aef-a5da-2742c7636e16
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.data.model.KeywordItem
import com.example.keynews.data.model.KeywordRule
import com.example.keynews.databinding.DialogAddKeywordRuleBinding
import com.example.keynews.databinding.DialogEditKeywordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicLong

class KeywordRuleDialogFragment(
    private val existingRule: KeywordRule? = null,
    private val onSaveClicked: (KeywordRule, List<KeywordItem>) -> Unit,
    private val onDeleteClicked: ((KeywordRule) -> Unit)? = null
) : DialogFragment() {

    private var _binding: DialogAddKeywordRuleBinding? = null
    private val binding get() = _binding!!

    private lateinit var keywordAdapter: KeywordAdapter
    private val tempKeywordId = AtomicLong(-1) // Temporary IDs for new keywords
    private val keywords = mutableListOf<KeywordItem>()
    
    // Register file picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processSelectedFile(it) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddKeywordRuleBinding.inflate(layoutInflater)

        // Use fullscreen dialog as requested
        val dialog = Dialog(requireContext(), android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        dialog.setContentView(binding.root)
        
        // Set dialog to match parent width and height
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

        setupRecyclerView()
        setupButtons()
        loadExistingData()

        return dialog
    }

    private fun setupRecyclerView() {
        keywordAdapter = KeywordAdapter(
            onEditClick = { showEditKeywordDialog(it) },
            onDeleteClick = { removeKeyword(it) }
        )
        binding.rvKeywords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvKeywords.adapter = keywordAdapter
    }

    private fun setupButtons() {
        // Add keyword button
        binding.btnAddKeyword.setOnClickListener {
            val keyword = binding.etKeyword.text.toString().trim()
            if (keyword.isNotEmpty()) {
                addKeyword(keyword)
                binding.etKeyword.text.clear()
            } else {
                Toast.makeText(requireContext(), "Please enter a keyword", Toast.LENGTH_SHORT).show()
            }
        }

        // Import button - Added for new feature
        binding.btnImport.setOnClickListener {
            openFilePicker()
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
                onDeleteClicked.invoke(existingRule)
                dismiss()
            }
        }
    }

    private fun loadExistingData() {
        if (existingRule != null) {
            binding.etRuleName.setText(existingRule.name)
            binding.rbWhitelist.isChecked = existingRule.isWhitelist
            binding.rbBlacklist.isChecked = !existingRule.isWhitelist

            // Load keywords
            lifecycleScope.launch {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                val keywordsList = dataManager.database.keywordRuleDao()
                    .getKeywordsForRule(existingRule.id)
                
                // Add keywords and sort them alphabetically
                keywords.addAll(keywordsList)
                sortKeywords()
                keywordAdapter.submitList(keywords)
            }
        }
    }

    private fun addKeyword(text: String) {
        // Check if the keyword already exists (case-insensitive)
        if (keywords.any { it.keyword.equals(text, ignoreCase = true) }) {
            Toast.makeText(requireContext(), "Keyword already exists", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a dialog to configure keyword options
        val dialogBinding = DialogEditKeywordBinding.inflate(layoutInflater)
        dialogBinding.etKeyword.setText(text)
        dialogBinding.etKeyword.setSelection(text.length)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Configure Keyword Options")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val updatedText = dialogBinding.etKeyword.text.toString().trim()
                if (updatedText.isNotEmpty()) {
                    // Create the keyword item with the configured options
                    val keywordItem = if (existingRule != null) {
                        KeywordItem(
                            id = tempKeywordId.decrementAndGet(), // Temporary negative ID
                            ruleId = existingRule.id,
                            keyword = updatedText,
                            isCaseSensitive = dialogBinding.cbCaseSensitive.isChecked,
                            isFullWordMatch = dialogBinding.cbFullWordMatch.isChecked
                        )
                    } else {
                        KeywordItem(
                            id = tempKeywordId.decrementAndGet(), // Temporary negative ID
                            ruleId = -1, // Will be updated after rule is saved
                            keyword = updatedText,
                            isCaseSensitive = dialogBinding.cbCaseSensitive.isChecked,
                            isFullWordMatch = dialogBinding.cbFullWordMatch.isChecked
                        )
                    }
                    
                    keywords.add(keywordItem)
                    sortKeywords()
                    keywordAdapter.submitList(keywords.toList())
                    binding.etKeyword.text.clear()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeKeyword(keywordItem: KeywordItem) {
        keywords.remove(keywordItem)
        keywordAdapter.submitList(keywords.toList())
    }

    private fun showEditKeywordDialog(keywordItem: KeywordItem) {
        val dialogBinding = DialogEditKeywordBinding.inflate(layoutInflater)
        dialogBinding.etKeyword.setText(keywordItem.keyword)
        dialogBinding.etKeyword.setSelection(keywordItem.keyword.length) // Place cursor at the end
        
        // Set initial state of checkboxes
        dialogBinding.cbCaseSensitive.isChecked = keywordItem.isCaseSensitive
        dialogBinding.cbFullWordMatch.isChecked = keywordItem.isFullWordMatch

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Keyword")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val updatedText = dialogBinding.etKeyword.text.toString().trim()
                if (updatedText.isNotEmpty()) {
                    // Check if the edited keyword would be a duplicate
                    val isDuplicate = keywords.any { 
                        it.id != keywordItem.id && it.keyword.equals(updatedText, ignoreCase = true)
                    }
                    
                    if (isDuplicate) {
                        Toast.makeText(requireContext(), "Keyword already exists", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    val updatedKeyword = keywordItem.copy(
                        keyword = updatedText,
                        isCaseSensitive = dialogBinding.cbCaseSensitive.isChecked,
                        isFullWordMatch = dialogBinding.cbFullWordMatch.isChecked
                    )
                    
                    val index = keywords.indexOfFirst { it.id == keywordItem.id }
                    if (index != -1) {
                        keywords[index] = updatedKeyword
                        sortKeywords()
                        keywordAdapter.submitList(keywords.toList())
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveRule() {
        val name = binding.etRuleName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a rule name", Toast.LENGTH_SHORT).show()
            return
        }

        if (keywords.isEmpty()) {
            Toast.makeText(requireContext(), "Please add at least one keyword", Toast.LENGTH_SHORT).show()
            return
        }

        val isWhitelist = binding.rbWhitelist.isChecked

        val rule = existingRule?.copy(
            name = name,
            isWhitelist = isWhitelist
        ) ?: KeywordRule(
            name = name,
            isWhitelist = isWhitelist
        )

        onSaveClicked(rule, keywords)
        dismiss()
    }
    
    // New functions for file import feature
    
    private fun openFilePicker() {
        filePickerLauncher.launch("text/plain")
    }
    
    private fun processSelectedFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val keywordsFromFile = withContext(Dispatchers.IO) {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val lines = mutableListOf<String>()
                    
                    reader.useLines { sequence ->
                        sequence.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && trimmed.length <= 30) {
                                lines.add(trimmed)
                            }
                        }
                    }
                    
                    lines
                }
                
                // Show dialog to configure import options
                val dialogBinding = DialogEditKeywordBinding.inflate(layoutInflater)
                dialogBinding.etKeyword.isEnabled = false  // Disable text field
                dialogBinding.etKeyword.setText("Import ${keywordsFromFile.size} keywords")
                
                AlertDialog.Builder(requireContext())
                    .setTitle("Configure Import Options")
                    .setView(dialogBinding.root)
                    .setPositiveButton("Import") { _, _ ->
                        // Process the keywords and add them with the selected options
                        var addedCount = 0
                        var duplicateCount = 0
                        
                        for (keyword in keywordsFromFile) {
                            // Check if this keyword already exists (case-insensitive)
                            if (keywords.any { it.keyword.equals(keyword, ignoreCase = true) }) {
                                duplicateCount++
                                continue
                            }
                            
                            // Add the keyword with the selected options
                            val keywordItem = KeywordItem(
                                id = tempKeywordId.decrementAndGet(),
                                ruleId = existingRule?.id ?: -1,
                                keyword = keyword,
                                isCaseSensitive = dialogBinding.cbCaseSensitive.isChecked,
                                isFullWordMatch = dialogBinding.cbFullWordMatch.isChecked
                            )
                            
                            keywords.add(keywordItem)
                            addedCount++
                        }
                        
                        if (addedCount > 0) {
                            // Sort and update the UI
                            sortKeywords()
                            keywordAdapter.submitList(keywords.toList())
                        }
                        
                        // Show result message
                        val message = if (addedCount > 0) {
                            "Added $addedCount keywords" + if (duplicateCount > 0) " ($duplicateCount duplicates skipped)" else ""
                        } else if (duplicateCount > 0) {
                            "No new keywords added ($duplicateCount duplicates skipped)"
                        } else {
                            "No valid keywords found in file"
                        }
                        
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun sortKeywords() {
        // Sort keywords case-insensitively
        keywords.sortBy { it.keyword.lowercase() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// CodeCleaner_End_5e4f1ac6-c4e9-4aef-a5da-2742c7636e16
