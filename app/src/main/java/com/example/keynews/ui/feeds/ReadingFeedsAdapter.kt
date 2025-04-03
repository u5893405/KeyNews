package com.example.keynews.ui.feeds

// CodeCleaner_Start_eeafb189-3b31-4c71-932b-a1a83e4f80ac
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.data.model.ReadingFeed
import com.example.keynews.databinding.ItemReadingFeedBinding

class ReadingFeedsAdapter(
    private val onDeleteClick: (Long) -> Unit,
        private val onFeedClick: (Long) -> Unit  // Add this callback
) : RecyclerView.Adapter<ReadingFeedsAdapter.ViewHolder>() {

    private val feeds = mutableListOf<ReadingFeed>()
    private val sourceCounts = mutableMapOf<Long, Int>() // Map of feedId to source count

    fun submitList(list: List<ReadingFeed>, counts: Map<Long, Int>) {
        feeds.clear()
        feeds.addAll(list)

        sourceCounts.clear()
        sourceCounts.putAll(counts)

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReadingFeedBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(feeds[position])
    }

    override fun getItemCount(): Int = feeds.size

    inner class ViewHolder(private val binding: ItemReadingFeedBinding) :
    RecyclerView.ViewHolder(binding.root) {

        fun bind(feed: ReadingFeed) {
            if (feed.isDefault) {
                binding.tvFeedName.text = "${feed.name} (Default)"
            } else {
                binding.tvFeedName.text = feed.name
            }

            // Show source count if available
            val count = sourceCounts[feed.id] ?: 0
            binding.tvSourceCount.text = "$count sources"

            binding.btnDelete.setOnClickListener {
                onDeleteClick(feed.id)
            }

            binding.root.setOnClickListener {
                onFeedClick(feed.id)
            }
        }
    }
}

// CodeCleaner_End_eeafb189-3b31-4c71-932b-a1a83e4f80ac

