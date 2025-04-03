package com.example.keynews.ui.feeds

// CodeCleaner_Start_67520052-ecb1-4bea-9477-b74ab0789872
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.data.model.ReadingFeed
import com.example.keynews.databinding.FragmentReadingFeedsBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.keynews.R
import androidx.appcompat.app.AlertDialog
import com.example.keynews.MainActivity


class ReadingFeedsFragment : Fragment() {

    private var _binding: FragmentReadingFeedsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ReadingFeedsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadingFeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ReadingFeedsAdapter(
            onDeleteClick = { feedId -> confirmDelete(feedId) },
                                      onFeedClick = { feedId -> openFeedEdit(feedId) }
        )

        binding.rvReadingFeeds.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReadingFeeds.adapter = adapter

        binding.btnAddFeed.setOnClickListener {
            showAddFeedDialog()
        }
    }

    private fun openFeedEdit(feedId: Long) {
        val fragment = FeedEditFragment.newInstance(feedId)
        parentFragmentManager.beginTransaction()
        .replace(R.id.main_content_frame, fragment)
        .addToBackStack(null)
        .commit()
    }


    override fun onResume() {
        super.onResume()
        loadFeeds()
    }

    private fun loadFeeds() {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            val feeds = dataManager.database.readingFeedDao().getAllFeeds()

            // Get source counts for each feed
            val sourceCounts = mutableMapOf<Long, Int>()
            for (feed in feeds) {
                val sources = dataManager.database.readingFeedDao().getSourceIdsForFeed(feed.id)
                sourceCounts[feed.id] = sources.size
            }

            adapter.submitList(feeds, sourceCounts)
        }
    }

    private fun showAddFeedDialog() {
        val dialog = FeedDialogFragment { feedName ->
            val dataManager = (requireActivity().application as KeyNewsApp).dataManager
            lifecycleScope.launch {
                val newFeedId = dataManager.database.readingFeedDao().insertReadingFeed(
                    ReadingFeed(name = feedName, isDefault = false)
                )

                withContext(Dispatchers.Main) {
                    // Open the edit screen after creating the feed
                    openFeedEdit(newFeedId)
                }
            }
        }
        dialog.show(parentFragmentManager, "AddFeedDialog")
    }

    private fun confirmDelete(feedId: Long) {
        val dataManager = (requireActivity().application as KeyNewsApp).dataManager
        lifecycleScope.launch {
            val feed = dataManager.database.readingFeedDao().getAllFeeds().find { it.id == feedId }
            val feedCount = dataManager.database.readingFeedDao().getAllFeeds().size

            // Special warning for deleting the only default feed
            val message = if (feed?.isDefault == true && feedCount == 1) {
                "You are deleting the only feed. You won't see news unless you create another feed."
            } else {
                "Are you sure you want to delete this feed?"
            }

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(requireContext())
                .setTitle("Delete Feed")
                .setMessage(message)
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        dataManager.database.readingFeedDao().deleteFeedById(feedId)
                        loadFeeds()

                        // Update the navigation drawer
                        (activity as? MainActivity)?.loadFeedsIntoDrawer()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
// CodeCleaner_End_67520052-ecb1-4bea-9477-b74ab0789872

