package com.example.keynews.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.keynews.KeyNewsApp
import com.example.keynews.MainActivity
import com.example.keynews.R
import com.example.keynews.data.model.NewsSource
import com.example.keynews.data.model.ReadingFeed
import com.example.keynews.data.model.ReadingFeedSourceCrossRef
import com.example.keynews.databinding.DialogBackupOptionsBinding
import com.example.keynews.databinding.DialogRestoreOptionsBinding
import com.example.keynews.databinding.FragmentSettingsBinding
import com.example.keynews.ui.settings.tts.TtsEngineManager
import com.example.keynews.ui.settings.tts.TtsSettingsHelper
import com.example.keynews.util.BackupManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings fragment that allows the user to configure app behavior.
 * Includes both general app settings and TTS-specific settings.
 */
class SettingsFragment : Fragment(), TextToSpeech.OnInitListener {

    // Backup/Restore
    private lateinit var backupManager: BackupManager
    
    // Activity result launchers for file operations
    private lateinit var createDocumentLauncher: ActivityResultLauncher<Intent>
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    
    // Temporary storage for backup options
    private val selectedBackupCategories = mutableListOf<String>()
    private var backupUri: Uri? = null
    private var restoreUri: Uri? = null

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    // TTS engine manager
    private lateinit var ttsEngineManager: TtsEngineManager
    
    companion object {
        const val PREFS_NAME = "keynews_settings"
        private const val KEY_READ_BODY = "read_body"
        private const val KEY_HEADLINES_PER_SESSION = "headlines_per_session"
        private const val KEY_DELAY_BETWEEN_HEADLINES = "delay_between_headlines"
        private const val KEY_ARTICLE_BODY_LENGTH = "article_body_length"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val DEFAULT_HEADLINES_PER_SESSION = 10
        private const val DEFAULT_DELAY_BETWEEN_HEADLINES = 2
        private const val DEFAULT_ARTICLE_BODY_LENGTH = 120
        private const val TAG = "SettingsFragment"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize TTS engine manager
        ttsEngineManager = TtsEngineManager(requireContext(), lifecycleOwner = this)
        // Initialize TTS but defer complex setup until we have the view binding
        
        // Initialize BackupManager
        val app = requireActivity().application as KeyNewsApp
        backupManager = BackupManager(requireContext(), app.dataManager)
        
        // Register activity result launchers
        registerActivityResultLaunchers()
    }
    
    /**
     * Register ActivityResultLaunchers for file operations and permissions
     */
    private fun registerActivityResultLaunchers() {
        // Launcher for creating backup files
        createDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    backupUri = uri
                    // Only proceed if there are categories selected
                    if (selectedBackupCategories.isNotEmpty()) {
                        performBackup(uri, selectedBackupCategories)
                    } else {
                        Toast.makeText(requireContext(), "Error: No backup categories were selected", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        // Launcher for opening backup files
        openDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    restoreUri = uri
                    showRestoreOptionsDialog(uri)
                }
            }
        }
        
        // Launcher for requesting permissions
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(requireContext(), "Storage permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Storage permission denied. Cannot access external storage.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupGeneralSettings(prefs)
        setupAiFilteringSettings()
        setupDebugSection()
        setupBackupRestoreSection()
        
        // Initialize TTS after view is created to avoid initialization loops
        ttsEngineManager.initializeTts(this)
        setupTtsSettings(prefs)
    }
    
    /**
     * Helper function to safely get an integer preference that might be stored as another type
     */
    private fun safeGetIntPreference(prefs: android.content.SharedPreferences, key: String, defaultValue: Int): Int {
        return try {
            prefs.getInt(key, defaultValue)
        } catch (e: ClassCastException) {
            // If there's a type mismatch, try to get it as a float or a string
            try {
                val floatValue = prefs.getFloat(key, defaultValue.toFloat())
                val intValue = floatValue.toInt()
                // Fix the preference type for future use
                prefs.edit().putInt(key, intValue).apply()
                Log.d(TAG, "Fixed preference type for $key: $floatValue -> $intValue")
                intValue
            } catch (e2: Exception) {
                try {
                    // Last resort - try getting it as a string and parsing
                    val stringValue = prefs.getString(key, defaultValue.toString())
                    val intValue = stringValue?.toIntOrNull() ?: defaultValue
                    // Fix the preference
                    prefs.edit().putInt(key, intValue).apply()
                    Log.d(TAG, "Fixed preference type for $key from string: $stringValue -> $intValue")
                    intValue
                } catch (e3: Exception) {
                    Log.e(TAG, "Failed to read preference $key, using default: $defaultValue")
                    defaultValue
                }
            }
        }
    }

    /**
     * Set up general app settings
     */
    private fun setupGeneralSettings(prefs: android.content.SharedPreferences) {
        // Read article body setting
        val readBodyValue = prefs.getBoolean(KEY_READ_BODY, false)
        binding.cbReadBody.isChecked = readBodyValue
        
        binding.cbReadBody.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            prefs.edit().putBoolean(KEY_READ_BODY, isChecked).apply()
        }
        
        // Article body length setting - use safe getter for all numeric preferences
        val articleBodyLengthValue = safeGetIntPreference(prefs, KEY_ARTICLE_BODY_LENGTH, DEFAULT_ARTICLE_BODY_LENGTH)
        binding.etArticleBodyLength.setText(articleBodyLengthValue.toString())
        
        binding.etArticleBodyLength.setOnEditorActionListener { v, _, _ ->
            try {
                val value = (v as android.widget.TextView).text.toString().toIntOrNull() ?: DEFAULT_ARTICLE_BODY_LENGTH
                prefs.edit().putInt(KEY_ARTICLE_BODY_LENGTH, value).apply()
                // Immediately notify that settings have changed so UI can update
                com.example.keynews.util.FeedUpdateNotifier.notifyUpdated()
            } catch (e: Exception) {
                // Reset to default if there's a problem
                binding.etArticleBodyLength.setText(DEFAULT_ARTICLE_BODY_LENGTH.toString())
                prefs.edit().putInt(KEY_ARTICLE_BODY_LENGTH, DEFAULT_ARTICLE_BODY_LENGTH).apply()
            }
            false
        }
        
        // Also update when focus is lost
        binding.etArticleBodyLength.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                try {
                    // EditText is a TextView, so we can cast to TextView
                    val value = (v as android.widget.TextView).text.toString().toIntOrNull() ?: DEFAULT_ARTICLE_BODY_LENGTH
                    prefs.edit().putInt(KEY_ARTICLE_BODY_LENGTH, value).apply()
                    // Notify that settings have changed
                    com.example.keynews.util.FeedUpdateNotifier.notifyUpdated()
                } catch (e: Exception) {
                    // Reset to default if there's a problem
                    binding.etArticleBodyLength.setText(DEFAULT_ARTICLE_BODY_LENGTH.toString())
                    prefs.edit().putInt(KEY_ARTICLE_BODY_LENGTH, DEFAULT_ARTICLE_BODY_LENGTH).apply()
                }
            }
        }
        
        // Headlines per session setting
        val headlinesPerSessionValue = safeGetIntPreference(prefs, KEY_HEADLINES_PER_SESSION, DEFAULT_HEADLINES_PER_SESSION)
        binding.etHeadlinesPerSession.setText(headlinesPerSessionValue.toString())
        
        binding.etHeadlinesPerSession.setOnEditorActionListener { v, _, _ ->
            try {
                val value = (v as android.widget.TextView).text.toString().toIntOrNull() ?: DEFAULT_HEADLINES_PER_SESSION
                prefs.edit().putInt(KEY_HEADLINES_PER_SESSION, value).apply()
            } catch (e: Exception) {
                // Reset to default if there's a problem
                binding.etHeadlinesPerSession.setText(DEFAULT_HEADLINES_PER_SESSION.toString())
                prefs.edit().putInt(KEY_HEADLINES_PER_SESSION, DEFAULT_HEADLINES_PER_SESSION).apply()
            }
            false
        }
        
        // Delay between headlines setting
        val delayBetweenHeadlinesValue = safeGetIntPreference(prefs, KEY_DELAY_BETWEEN_HEADLINES, DEFAULT_DELAY_BETWEEN_HEADLINES)
        binding.etDelayBetweenHeadlines.setText(delayBetweenHeadlinesValue.toString())
        
        binding.etDelayBetweenHeadlines.setOnEditorActionListener { v, _, _ ->
            try {
                val value = (v as android.widget.TextView).text.toString().toIntOrNull() ?: DEFAULT_DELAY_BETWEEN_HEADLINES
                prefs.edit().putInt(KEY_DELAY_BETWEEN_HEADLINES, value).apply()
            } catch (e: Exception) {
                // Reset to default if there's a problem
                binding.etDelayBetweenHeadlines.setText(DEFAULT_DELAY_BETWEEN_HEADLINES.toString())
                prefs.edit().putInt(KEY_DELAY_BETWEEN_HEADLINES, DEFAULT_DELAY_BETWEEN_HEADLINES).apply()
            }
            false
        }
    }
    
    /**
     * Set up TTS settings
     */
    private fun setupTtsSettings(prefs: android.content.SharedPreferences) {
        // Setup TTS-related UI components
        ttsEngineManager.setupTtsSettings(binding, prefs)
        
        // Setup test button for the selected language/voice
        binding.btnTestTts.setOnClickListener {
            if (ttsEngineManager.isInitialized()) {
                val testText = binding.etTestText.text.toString().takeIf { it.isNotBlank() } 
                    ?: "Testing text to speech"
                    
                // Use the enhanced method that safely handles the currently selected language/voice
                ttsEngineManager.testTtsWithCurrentSelection(testText)
            } else {
                Toast.makeText(requireContext(), "TTS not initialized yet", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Setup save button
        binding.btnSaveTtsPreferences.setOnClickListener {
            if (ttsEngineManager.isInitialized()) {
                ttsEngineManager.saveLanguageVoicePreferences()
            } else {
                Toast.makeText(context, "TTS engine not initialized yet", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Button to add a new language-voice preference
        binding.btnAddLanguageVoice.setOnClickListener {
            if (ttsEngineManager.isInitialized()) {
                ttsEngineManager.addLanguageVoicePreference()
            } else {
                Toast.makeText(context, "TTS engine not initialized yet", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Set up debug section
     */
    private fun setupDebugSection() {
        // Debug section - Add RSS source button
        binding.btnAddRssSource.setOnClickListener {
            addWashingtonTimesRssSource()
        }
    }
    
    /**
     * Set up AI filtering settings
     */
    private fun setupAiFilteringSettings() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Initialize Gemini API key field
        val apiKey = prefs.getString(KEY_GEMINI_API_KEY, "")
        Log.d(TAG, "🔍 AI FILTERING: Retrieved API key from preferences, exists: ${!apiKey.isNullOrEmpty()}")
        binding.etGeminiApiKey.setText(apiKey)
        
        // Set up save button
        binding.btnSaveGeminiApiKey.setOnClickListener {
            val newApiKey = binding.etGeminiApiKey.text.toString().trim()
            prefs.edit().putString(KEY_GEMINI_API_KEY, newApiKey).apply()
            Log.d(TAG, "🔍 AI FILTERING: Saved API key to preferences, length: ${newApiKey.length}")
            
            // Initialize GeminiService with the new API key
            val dataManager = (requireActivity().application as KeyNewsApp).dataManager
            val geminiService = dataManager.getGeminiService(requireContext())
            geminiService.saveApiKey(newApiKey)
            Log.d(TAG, "🔍 AI FILTERING: Updated GeminiService with new API key")
            
            Toast.makeText(requireContext(), "Gemini API key saved", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Set up backup/restore section
     */
    private fun setupBackupRestoreSection() {
        // Backup button
        binding.btnBackupSettings.setOnClickListener {
            checkStoragePermissionAndProceed { showBackupOptionsDialog() }
        }
        
        // Restore button
        binding.btnRestoreSettings.setOnClickListener {
            checkStoragePermissionAndProceed { openRestoreFilePicker() }
        }
    }
    
    /**
     * Check if storage permission is granted and proceed with action
     */
    private fun checkStoragePermissionAndProceed(action: () -> Unit) {
        // For Android 10 (API 29) and above, we don't need WRITE_EXTERNAL_STORAGE
        // permission for specific application directories or when using Storage Access Framework
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    action()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    // Show rationale if needed
                    AlertDialog.Builder(requireContext())
                        .setTitle("Storage Permission Required")
                        .setMessage("This app needs storage permission to backup and restore settings.")
                        .setPositiveButton("Grant") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                else -> {
                    // Request permission
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else {
            // For Android 10+, we can proceed without permission for our use case
            action()
        }
    }
    
    /**
     * Show dialog with backup options
     */
    private fun showBackupOptionsDialog() {
        val dialogBinding = DialogBackupOptionsBinding.inflate(layoutInflater)
        
        // Create dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        // Setup default path suggestion
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val defaultFileName = "keynews_backup_$timestamp.json"
        
        // Set up choose location button
        dialogBinding.btnChooseBackupLocation.setOnClickListener {
            // Get selected categories first
            selectedBackupCategories.clear()  // Clear previous selections
            
            // Add each selected category
            if (dialogBinding.cbBackupSources.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_SOURCES)
            }
            if (dialogBinding.cbBackupReadingFeeds.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_READING_FEEDS)
            }
            if (dialogBinding.cbBackupKeywordRules.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_KEYWORD_RULES)
            }
            if (dialogBinding.cbBackupRepeatedSessions.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_REPEATED_SESSIONS)
            }
            if (dialogBinding.cbBackupSettings.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_SETTINGS)
            }
            if (dialogBinding.cbBackupReadMarks.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_READ_MARKS)
            }
            if (dialogBinding.cbBackupAiRules.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_AI_RULES)
            }
            if (dialogBinding.cbBackupAiFilterCache.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_AI_FILTER_CACHE)
            }
            
            // Verify at least one category is selected
            if (selectedBackupCategories.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one category to backup", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            Log.d("SettingsFragment", "Choose location clicked, selected categories: " + selectedBackupCategories.joinToString())
            
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, defaultFileName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Start in the Documents directory
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, 
                            Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents"))
                }
            }
            createDocumentLauncher.launch(intent)
            dialog.dismiss()
        }
        
        // Set up cancel button
        dialogBinding.btnCancelBackup.setOnClickListener {
            dialog.dismiss()
        }
        
        // Set up export button
        dialogBinding.btnExport.setOnClickListener {
            // Get selected categories
            selectedBackupCategories.clear()  // Clear previous selections
            
            // Add each selected category
            if (dialogBinding.cbBackupSources.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_SOURCES)
            }
            if (dialogBinding.cbBackupReadingFeeds.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_READING_FEEDS)
            }
            if (dialogBinding.cbBackupKeywordRules.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_KEYWORD_RULES)
            }
            if (dialogBinding.cbBackupRepeatedSessions.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_REPEATED_SESSIONS)
            }
            if (dialogBinding.cbBackupSettings.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_SETTINGS)
            }
            if (dialogBinding.cbBackupReadMarks.isChecked) {
                selectedBackupCategories.add(BackupManager.CATEGORY_READ_MARKS)
            }
            
            // Verify at least one category is selected
            if (selectedBackupCategories.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one category to backup", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            Log.d("SettingsFragment", "Selected categories: " + selectedBackupCategories.joinToString())
            
            // Launch document picker
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, defaultFileName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Start in the Documents directory
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, 
                            Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents"))
                }
            }
            createDocumentLauncher.launch(intent)
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Perform backup using the selected options
     */
    private fun performBackup(uri: Uri, categoriesToBackup: List<String>) {
        if (categoriesToBackup.isEmpty()) {
            // This shouldn't happen, but just in case
            Toast.makeText(requireContext(), "No backup categories selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show progress
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("Backup in Progress")
            .setMessage("Please wait while your data is being backed up...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        // Perform backup in background
        lifecycleScope.launch {
            val result = backupManager.createBackup(uri, categoriesToBackup)
            
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "Backup completed successfully", Toast.LENGTH_SHORT).show()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Toast.makeText(requireContext(), "Backup failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * Open file picker for restore
     */
    private fun openRestoreFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        openDocumentLauncher.launch(intent)
    }
    
    /**
     * Show dialog with restore options
     */
    private fun showRestoreOptionsDialog(uri: Uri) {
        val dialogBinding = DialogRestoreOptionsBinding.inflate(layoutInflater)
        
        // Create dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        // Set file path
        dialogBinding.etRestorePath.setText(uri.toString())
        
        // Analyze backup file
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("Analyzing Backup File")
            .setMessage("Please wait...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        // Parse backup file in background
        lifecycleScope.launch {
            val result = backupManager.parseBackupFile(uri)
            
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                
                if (result.isSuccess) {
                    val backupData = result.getOrNull()!!
                    
                    // Enable restore options
                    dialogBinding.llRestoreOptions.visibility = View.VISIBLE
                    dialogBinding.btnImport.isEnabled = true
                    
                    // Only enable checkboxes for available data
                    dialogBinding.cbRestoreSources.isEnabled = backupData.containsKey(BackupManager.CATEGORY_SOURCES)
                    dialogBinding.cbRestoreSources.isChecked = backupData.containsKey(BackupManager.CATEGORY_SOURCES)
                    
                    dialogBinding.cbRestoreReadingFeeds.isEnabled = backupData.containsKey(BackupManager.CATEGORY_READING_FEEDS)
                    dialogBinding.cbRestoreReadingFeeds.isChecked = backupData.containsKey(BackupManager.CATEGORY_READING_FEEDS)
                    
                    dialogBinding.cbRestoreKeywordRules.isEnabled = backupData.containsKey(BackupManager.CATEGORY_KEYWORD_RULES)
                    dialogBinding.cbRestoreKeywordRules.isChecked = backupData.containsKey(BackupManager.CATEGORY_KEYWORD_RULES)
                    
                    dialogBinding.cbRestoreRepeatedSessions.isEnabled = backupData.containsKey(BackupManager.CATEGORY_REPEATED_SESSIONS)
                    dialogBinding.cbRestoreRepeatedSessions.isChecked = backupData.containsKey(BackupManager.CATEGORY_REPEATED_SESSIONS)
                    
                    dialogBinding.cbRestoreSettings.isEnabled = backupData.containsKey(BackupManager.CATEGORY_SETTINGS)
                    dialogBinding.cbRestoreSettings.isChecked = backupData.containsKey(BackupManager.CATEGORY_SETTINGS)
                    
                    // Initialize AI rule options if they exist in the backup
                    dialogBinding.cbRestoreAiRules.isEnabled = backupData.containsKey(BackupManager.CATEGORY_AI_RULES)
                    dialogBinding.cbRestoreAiRules.isChecked = backupData.containsKey(BackupManager.CATEGORY_AI_RULES)
                    
                    dialogBinding.cbRestoreAiFilterCache.isEnabled = backupData.containsKey(BackupManager.CATEGORY_AI_FILTER_CACHE)
                    dialogBinding.cbRestoreAiFilterCache.isChecked = backupData.containsKey(BackupManager.CATEGORY_AI_FILTER_CACHE)
                    
                    // Set up import button
                    dialogBinding.btnImport.setOnClickListener {
                        // Get selected categories
                        val selectedCategories = mutableListOf<String>()
                        if (dialogBinding.cbRestoreSources.isChecked && dialogBinding.cbRestoreSources.isEnabled) {
                            selectedCategories.add(BackupManager.CATEGORY_SOURCES)
                        }
                        if (dialogBinding.cbRestoreReadingFeeds.isChecked && dialogBinding.cbRestoreReadingFeeds.isEnabled) {
                            selectedCategories.add(BackupManager.CATEGORY_READING_FEEDS)
                        }
                        if (dialogBinding.cbRestoreKeywordRules.isChecked && dialogBinding.cbRestoreKeywordRules.isEnabled) {
                            selectedCategories.add(BackupManager.CATEGORY_KEYWORD_RULES)
                        }
                        if (dialogBinding.cbRestoreRepeatedSessions.isChecked && dialogBinding.cbRestoreRepeatedSessions.isEnabled) {
                            selectedCategories.add(BackupManager.CATEGORY_REPEATED_SESSIONS)
                        }
                        if (dialogBinding.cbRestoreSettings.isChecked && dialogBinding.cbRestoreSettings.isEnabled) {
                            selectedCategories.add(BackupManager.CATEGORY_SETTINGS)
                        }
                        if (dialogBinding.cbRestoreReadMarks.isChecked && dialogBinding.cbRestoreReadMarks.isEnabled) {
                            selectedCategories.add(BackupManager.CATEGORY_READ_MARKS)
                        }
                        if (dialogBinding.cbRestoreAiRules.isChecked && dialogBinding.cbRestoreAiRules.isEnabled) {
                            selectedCategories.add(BackupManager.CATEGORY_AI_RULES)
                        }
                        if (dialogBinding.cbRestoreAiFilterCache.isChecked && dialogBinding.cbRestoreAiFilterCache.isEnabled) {
                            selectedCategories.add(BackupManager.CATEGORY_AI_FILTER_CACHE)
                        }
                        
                        if (selectedCategories.isEmpty()) {
                            Toast.makeText(requireContext(), "Please select at least one category to restore", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        
                        // Get conflict resolution strategy
                        val overwriteExisting = dialogBinding.rbOverwriteExisting.isChecked
                        
                        // Create overwrite map
                        val overwriteMap = BackupManager.ALL_CATEGORIES.associateWith { overwriteExisting }
                        
                        // Close dialog
                        dialog.dismiss()
                        
                        // Perform restore
                        performRestore(backupData, selectedCategories, overwriteMap)
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Toast.makeText(requireContext(), "Failed to parse backup file: $error", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                }
            }
        }
        
        // Set up cancel button
        dialogBinding.btnCancelRestore.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Perform restore using the selected options
     */
    private fun performRestore(
        backupData: Map<String, Any>,
        selectedCategories: List<String>,
        overwriteExisting: Map<String, Boolean>
    ) {
        // Show progress
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("Restore in Progress")
            .setMessage("Please wait while your data is being restored...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        // Perform restore in background
        lifecycleScope.launch {
            val result = backupManager.restoreBackup(backupData, selectedCategories, overwriteExisting)
            
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "Restore completed successfully", Toast.LENGTH_SHORT).show()
                    
                    // Notify the app that data has changed
                    com.example.keynews.util.FeedUpdateNotifier.notifyUpdated()
                    
                    // Refresh the main drawer if in MainActivity
                    (activity as? MainActivity)?.loadFeedsIntoDrawer()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Toast.makeText(requireContext(), "Restore failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * TTS initialization callback
     */
    override fun onInit(status: Int) {
        // Forward the init callback to the TTS engine manager
        ttsEngineManager.onTtsInit(status)
    }
    
    /**
     * Debug method to add Washington Times RSS source
     */
    private fun addWashingtonTimesRssSource() {
        lifecycleScope.launch {
            try {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                
                // Create source with Washington Times URL
                val source = NewsSource(
                    name = "Washington Times World",
                    rssUrl = "http://p.washingtontimes.com/rss/headlines/news/world/"
                )
                
                // Save the source
                val newSourceId = dataManager.saveNewsSource(source)
                
                // Find default feed or create one if it doesn't exist
                val feedDao = dataManager.database.readingFeedDao()
                var defaultFeed = feedDao.getAllFeeds().firstOrNull { it.isDefault }
                
                if (defaultFeed == null) {
                    defaultFeed = ReadingFeed(name = "Default feed", isDefault = true)
                    val defaultFeedId = feedDao.insertReadingFeed(defaultFeed)
                    defaultFeed = defaultFeed.copy(id = defaultFeedId)
                }
                
                // Link source to default feed
                feedDao.insertFeedSourceCrossRef(
                    ReadingFeedSourceCrossRef(defaultFeed.id, newSourceId)
                )
                
                // Refresh the main drawer if needed
                withContext(Dispatchers.Main) {
                    (activity as? MainActivity)?.loadFeedsIntoDrawer()
                    Toast.makeText(requireContext(), "Washington Times RSS added to Default feed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error adding RSS source: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ttsEngineManager.shutdown()
    }
}