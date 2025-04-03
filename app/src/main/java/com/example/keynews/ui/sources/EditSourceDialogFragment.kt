package com.example.keynews.ui.sources

// CodeCleaner_Start_f17728e1-2543-4e44-ba4b-5ad317e5a206
import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.keynews.KeyNewsApp
import com.example.keynews.data.model.NewsSource
import com.example.keynews.databinding.DialogEditSourceBinding

/**
 * Dialog for editing an existing news source
 */
class EditSourceDialogFragment(
    private val source: NewsSource,
    private val onSaveClicked: (NewsSource) -> Unit
) : DialogFragment() {

    private var _binding: DialogEditSourceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogEditSourceBinding.inflate(layoutInflater)

        val dialog = Dialog(requireContext())
        dialog.setContentView(binding.root)

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        // Pre-populate fields with current values
        binding.etName.setText(source.name)
        binding.etUrl.setText(source.rssUrl)

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val url = binding.etUrl.text.toString().trim()

            lifecycleScope.launch {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                val sourceDao = dataManager.database.newsSourceDao()
                
                // Check for duplicate URL (skipping the current source being edited)
                val existingSources = sourceDao.getAllSources()
                val isDuplicate = existingSources.any { 
                    it.id != source.id && it.rssUrl.trim().equals(url.trim(), ignoreCase = true)
                }
                
                if (isDuplicate) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "This RSS URL already exists", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Create updated source with same ID
                    val updatedSource = source.copy(name = name, rssUrl = url)
                    
                    // Save the source (this will replace the existing one with the same ID)
                    dataManager.saveNewsSource(updatedSource)

                    withContext(Dispatchers.Main) {
                        dismiss()
                        onSaveClicked(updatedSource)
                    }
                }
            }
        }

        binding.btnCancel.setOnClickListener { dismiss() }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
// CodeCleaner_End_f17728e1-2543-4e44-ba4b-5ad317e5a206

