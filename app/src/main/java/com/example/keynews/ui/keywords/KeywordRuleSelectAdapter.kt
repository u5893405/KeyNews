package com.example.keynews.ui.keywords

// CodeCleaner_Start_0770af48-c0a3-4555-8853-b8b58ce63b67
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.data.model.KeywordRule
import com.example.keynews.databinding.ItemKeywordRuleSelectableBinding

class KeywordRuleSelectAdapter : RecyclerView.Adapter<KeywordRuleSelectAdapter.ViewHolder>() {

    private val rules = mutableListOf<KeywordRule>()
    private val selectedRuleIds = mutableSetOf<Long>()

    fun submitList(list: List<KeywordRule>, preSelectedRuleIds: Set<Long> = emptySet()) {
        rules.clear()
        rules.addAll(list)
        selectedRuleIds.clear()
        selectedRuleIds.addAll(preSelectedRuleIds)
        notifyDataSetChanged()
    }

    fun getSelectedRuleIds(): Set<Long> = selectedRuleIds.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKeywordRuleSelectableBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rules[position])
    }

    override fun getItemCount(): Int = rules.size

    inner class ViewHolder(private val binding: ItemKeywordRuleSelectableBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: KeywordRule) {
            binding.tvRuleName.text = rule.name
            binding.tvRuleType.text = if (rule.isWhitelist) "WHITELIST" else "BLACKLIST"
            binding.checkboxRule.isChecked = selectedRuleIds.contains(rule.id)
            
            // Handle checkbox changes
            binding.checkboxRule.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedRuleIds.add(rule.id)
                } else {
                    selectedRuleIds.remove(rule.id)
                }
            }
            
            // Make whole item clickable to toggle checkbox
            itemView.setOnClickListener {
                binding.checkboxRule.isChecked = !binding.checkboxRule.isChecked
            }
        }
    }
}
// CodeCleaner_End_0770af48-c0a3-4555-8853-b8b58ce63b67

