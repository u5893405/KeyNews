package com.example.keynews.ui.keywords

// CodeCleaner_Start_dd4ab0b1-df1e-4af0-b37d-c4fc6fc50790
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.data.model.KeywordItem
import com.example.keynews.databinding.ItemKeywordBinding

class KeywordAdapter(
    private val onEditClick: (KeywordItem) -> Unit,
    private val onDeleteClick: (KeywordItem) -> Unit
) : RecyclerView.Adapter<KeywordAdapter.ViewHolder>() {

    private val keywords = mutableListOf<KeywordItem>()

    fun submitList(list: List<KeywordItem>) {
        keywords.clear()
        keywords.addAll(list)
        notifyDataSetChanged()
    }

    fun addKeyword(keyword: KeywordItem) {
        keywords.add(keyword)
        notifyItemInserted(keywords.size - 1)
    }

    fun removeKeyword(keyword: KeywordItem) {
        val position = keywords.indexOfFirst { it.id == keyword.id }
        if (position != -1) {
            keywords.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun updateKeyword(updatedKeyword: KeywordItem) {
        val position = keywords.indexOfFirst { it.id == updatedKeyword.id }
        if (position != -1) {
            keywords[position] = updatedKeyword
            notifyItemChanged(position)
        }
    }

    fun getAllKeywords(): List<KeywordItem> = keywords.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKeywordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(keywords[position])
    }

    override fun getItemCount(): Int = keywords.size

    inner class ViewHolder(private val binding: ItemKeywordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(keyword: KeywordItem) {
            binding.tvKeyword.text = keyword.keyword
            
            // Show keyword options
            val options = mutableListOf<String>()
            if (keyword.isCaseSensitive) options.add("Case sensitive")
            if (keyword.isFullWordMatch) options.add("Full word match")
            if (keyword.keyword.contains('*', false)) options.add("Uses wildcard")
            
            if (options.isNotEmpty()) {
                binding.tvOptions.text = options.joinToString(" • ")
                binding.tvOptions.visibility = android.view.View.VISIBLE
            } else {
                binding.tvOptions.visibility = android.view.View.GONE
            }
            
            binding.btnEdit.setOnClickListener {
                onEditClick(keyword)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(keyword)
            }
        }
    }
}
// CodeCleaner_End_dd4ab0b1-df1e-4af0-b37d-c4fc6fc50790

