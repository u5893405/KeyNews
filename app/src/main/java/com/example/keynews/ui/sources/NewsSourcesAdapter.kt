package com.example.keynews.ui.sources

// CodeCleaner_Start_ae3aca06-5313-4d5b-ae44-5404748fc0e3
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.data.model.NewsSource
import com.example.keynews.databinding.ItemNewsSourceBinding

class NewsSourcesAdapter(
    private val onDeleteClick: (NewsSource) -> Unit,
    private val onEditClick: (NewsSource) -> Unit
) : RecyclerView.Adapter<NewsSourcesAdapter.ViewHolder>() {

    private val sources = mutableListOf<NewsSource>()

    fun submitList(list: List<NewsSource>) {
        sources.clear()
        sources.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNewsSourceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(sources[position])
    }

    override fun getItemCount(): Int = sources.size

    inner class ViewHolder(private val binding: ItemNewsSourceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(source: NewsSource) {
            // Show URL as fallback if name is empty
            val displayName = if (source.name.isNotEmpty()) source.name else source.rssUrl
            binding.tvSourceName.text = displayName
            binding.tvSourceUrl.text = source.rssUrl
            binding.btnDelete.setOnClickListener {
                onDeleteClick(source)
            }
            binding.btnEdit.setOnClickListener {
                onEditClick(source)
            }
        }
    }
}
// CodeCleaner_End_ae3aca06-5313-4d5b-ae44-5404748fc0e3

