package com.example.keynews.ui.keywords
// CodeCleaner_Start_4c8af0a3-1caa-40c9-9803-f55b314d24f3
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.data.model.KeywordRule
import com.example.keynews.databinding.ItemKeywordRuleBinding

class KeywordRulesAdapter(
    private val onRuleClicked: (KeywordRule) -> Unit,
    private val onDeleteClick: (Long) -> Unit
) : RecyclerView.Adapter<KeywordRulesAdapter.ViewHolder>() {

    private val rules = mutableListOf<KeywordRule>()

    fun submitList(list: List<KeywordRule>) {
        rules.clear()
        rules.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKeywordRuleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rules[position])
    }

    override fun getItemCount(): Int = rules.size

    inner class ViewHolder(private val binding: ItemKeywordRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: KeywordRule) {
            binding.tvRuleName.text = rule.name
            binding.tvRuleType.text = if (rule.isWhitelist) "WHITELIST" else "BLACKLIST"
            
            binding.root.setOnClickListener {
                onRuleClicked(rule)
            }
            
            binding.btnDelete.setOnClickListener {
                onDeleteClick(rule.id)
            }
        }
    }
}
// CodeCleaner_End_4c8af0a3-1caa-40c9-9803-f55b314d24f3
