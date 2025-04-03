package com.example.keynews.ui.sources

// CodeCleaner_Start_7d64e6d0-b2ff-4793-98a4-dc945e8950d7
import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.keynews.databinding.DialogAddSourceBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.keynews.KeyNewsApp
import com.example.keynews.MainActivity
import com.example.keynews.data.model.ReadingFeed
import com.example.keynews.data.model.NewsSource
import com.example.keynews.data.model.ReadingFeedSourceCrossRef


class SourceDialogFragment(
    private val onSaveClicked: (String, String) -> Unit
) : DialogFragment() {

    private var _binding: DialogAddSourceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddSourceBinding.inflate(layoutInflater)

        val dialog = Dialog(requireContext())
        dialog.setContentView(binding.root)

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        // Check if any feeds exist to determine checkbox text
        lifecycleScope.launch {
            val dataManager = (requireActivity().application as KeyNewsApp).dataManager
            val feeds = dataManager.database.readingFeedDao().getAllFeeds()

            withContext(Dispatchers.Main) {
                if (feeds.isEmpty()) {
                    binding.cbAddToDefaultFeed.text = "Create Default feed and add this source to it"
                } else {
                    binding.cbAddToDefaultFeed.text = "Add to Default feed"
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val url = binding.etUrl.text.toString().trim()
            val addToDefaultFeed = binding.cbAddToDefaultFeed.isChecked

            lifecycleScope.launch {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                val sourceDao = dataManager.database.newsSourceDao()
                
                // Check for duplicate URL
                val existingSources = sourceDao.getAllSources()
                val isDuplicate = existingSources.any { it.rssUrl.trim().equals(url.trim(), ignoreCase = true) }
                
                if (isDuplicate) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "This RSS URL already exists", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Save the source
                    val newSourceId = dataManager.saveNewsSource(NewsSource(name = name, rssUrl = url))

                    // If checkbox is checked, add to default feed
                    if (addToDefaultFeed) {
                        val feedDao = dataManager.database.readingFeedDao()

                        // Find default feed or create one if none exists
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
                        
                        // Ensure the feed appears in the drawer immediately
                        withContext(Dispatchers.Main) {
                            (activity as? MainActivity)?.loadFeedsIntoDrawer()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        dismiss()
                        // Callback with just name and url to maintain compatibility with existing code
                        onSaveClicked(name, url)
                    }
                }
            }
        }

        binding.btnCancel.setOnClickListener { dismiss() }

        return dialog
    }
}
// CodeCleaner_End_7d64e6d0-b2ff-4793-98a4-dc945e8950d7

