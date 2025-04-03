package com.example.keynews.ui.sources

// CodeCleaner_Start_844fa7eb-4091-4800-a31b-622404808e09
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.data.model.NewsSource
import com.example.keynews.databinding.FragmentNewsSourcesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class NewsSourcesFragment : Fragment() {

    private var _binding: FragmentNewsSourcesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NewsSourcesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsSourcesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = NewsSourcesAdapter(
            onDeleteClick = { source -> deleteSource(source) },
            onEditClick = { source -> showEditSourceDialog(source) }
        )

        binding.rvSources.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSources.adapter = adapter

        binding.btnAddSource.setOnClickListener {
            showAddSourceDialog()
        }
        
        binding.btnImportSources.setOnClickListener {
            openFilePicker()
        }
    }
    
    // Activity result launcher for file picking
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importSourcesFromFile(uri)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSources()
    }

    private fun loadSources() {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            val sources = dataManager.getAllSources()
            adapter.submitList(sources)
        }
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        filePickerLauncher.launch(intent)
    }
    
    private fun importSourcesFromFile(uri: Uri) {
        lifecycleScope.launch {
            val dataManager = (requireActivity().application as KeyNewsApp).dataManager
            val sourceDao = dataManager.database.newsSourceDao()
            
            try {
                // Read the file line by line
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val urls = reader.readLines().filter { it.isNotBlank() }
                reader.close()
                
                var importedCount = 0
                var duplicateCount = 0
                
                // Import each URL, skipping duplicates
                withContext(Dispatchers.IO) {
                    val existingSources = sourceDao.getAllSources()
                    val existingUrls = existingSources.map { it.rssUrl.trim().lowercase() }
                    
                    for (url in urls) {
                        val trimmedUrl = url.trim()
                        if (trimmedUrl.isNotEmpty() && !existingUrls.contains(trimmedUrl.lowercase())) {
                            // Create a new source with the URL as both name and RSS URL
                            // User can edit the name later
                            val newSource = NewsSource(name = "", rssUrl = trimmedUrl)
                            sourceDao.insert(newSource)
                            importedCount++
                        } else if (trimmedUrl.isNotEmpty()) {
                            duplicateCount++
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    // Show results and reload the list
                    val message = "Imported $importedCount sources. " + 
                        if (duplicateCount > 0) "Skipped $duplicateCount duplicates." else ""
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    loadSources()
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error importing sources: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showEditSourceDialog(source: NewsSource) {
        val dialog = EditSourceDialogFragment(source) { updatedSource ->
            // Reload the list after editing
            loadSources()
        }
        dialog.show(parentFragmentManager, "EditSourceDialog")
    }

    private fun showAddSourceDialog() {
        val dialog = SourceDialogFragment { name, url ->
            // Source is already saved in dialog, just need to refresh the list
            loadSources()
        }
        dialog.show(parentFragmentManager, "AddSourceDialog")
    }

    private fun deleteSource(source: NewsSource) {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            dataManager.database.newsSourceDao().deleteById(source.id)
            loadSources()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
// CodeCleaner_End_844fa7eb-4091-4800-a31b-622404808e09

