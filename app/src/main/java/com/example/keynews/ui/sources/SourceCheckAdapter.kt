// CodeCleaner_Start_394f9e26-22a7-40bb-b64d-d3d9343bc98b
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.data.model.NewsSource
import com.example.keynews.databinding.ItemSourceCheckboxBinding

class SourceCheckAdapter : ListAdapter<NewsSource, SourceCheckAdapter.ViewHolder>(SourceDiffCallback()) {

    private val selectedSourceIds = mutableSetOf<Long>()

    fun submitList(newSources: List<NewsSource>, selectedIds: Set<Long>) {
        selectedSourceIds.clear()
        selectedSourceIds.addAll(selectedIds)
        
        // Use ListAdapter's submitList method instead of manual updates
        super.submitList(newSources)
    }

    fun getSelectedSourceIds(): Set<Long> = selectedSourceIds.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSourceCheckboxBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    // DiffUtil callback for efficient updates
    private class SourceDiffCallback : DiffUtil.ItemCallback<NewsSource>() {
        override fun areItemsTheSame(oldItem: NewsSource, newItem: NewsSource): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: NewsSource, newItem: NewsSource): Boolean {
            return oldItem == newItem
        }
    }

    inner class ViewHolder(private val binding: ItemSourceCheckboxBinding) :
    RecyclerView.ViewHolder(binding.root) {

        init {
            // Set click listeners only once in init to avoid multiple registrations
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val sourceId = getItem(position).id
                    // Toggle checkbox state
                    val newState = !binding.checkbox.isChecked
                    binding.checkbox.isChecked = newState
                    // Also update our data structure
                    updateSelection(sourceId, newState)
                }
            }
        }

        fun bind(source: NewsSource) {
            // Show URL as fallback if name is empty
            val displayName = if (source.name.isNotEmpty()) source.name else source.rssUrl
            binding.tvSourceName.text = displayName
            
            // Clear previous listener to prevent multiple callbacks
            binding.checkbox.setOnCheckedChangeListener(null)
            
            // Set the checkbox state from our data source
            binding.checkbox.isChecked = selectedSourceIds.contains(source.id)
            
            // Set new listener after updating state to avoid triggering during setup
            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                updateSelection(source.id, isChecked)
            }
        }

        private fun updateSelection(sourceId: Long, isSelected: Boolean) {
            if (isSelected) {
                selectedSourceIds.add(sourceId)
            } else {
                selectedSourceIds.remove(sourceId)
            }
        }
    }
}
// CodeCleaner_End_394f9e26-22a7-40bb-b64d-d3d9343bc98b

