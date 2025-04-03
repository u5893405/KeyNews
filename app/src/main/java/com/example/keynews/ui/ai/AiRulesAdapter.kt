package com.example.keynews.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.R
import com.example.keynews.data.model.AiRule

class AiRulesAdapter(
    private val onRuleClicked: (AiRule) -> Unit,
    private val onDeleteClick: (Long) -> Unit
) : ListAdapter<AiRule, AiRulesAdapter.ViewHolder>(AiRuleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_rule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = getItem(position)
        holder.bind(rule)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRuleName: TextView = itemView.findViewById(R.id.tvRuleName)
        private val tvRuleType: TextView = itemView.findViewById(R.id.tvRuleType)
        private val tvRuleText: TextView = itemView.findViewById(R.id.tvRuleText)
        private val ivRuleType: ImageView = itemView.findViewById(R.id.ivRuleType)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(rule: AiRule) {
            tvRuleName.text = rule.name
            tvRuleText.text = rule.ruleText
            
            // Set rule type text and icon
            val context = itemView.context
            if (rule.isWhitelist) {
                tvRuleType.text = context.getString(R.string.whitelist)
                ivRuleType.setImageResource(android.R.drawable.ic_input_add)
            } else {
                tvRuleType.text = context.getString(R.string.blacklist)
                ivRuleType.setImageResource(android.R.drawable.ic_delete)
            }

            // Set click listeners
            itemView.setOnClickListener { onRuleClicked(rule) }
            btnEdit.setOnClickListener { onRuleClicked(rule) }
            btnDelete.setOnClickListener { onDeleteClick(rule.id) }
        }
    }
}

class AiRuleDiffCallback : DiffUtil.ItemCallback<AiRule>() {
    override fun areItemsTheSame(oldItem: AiRule, newItem: AiRule): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: AiRule, newItem: AiRule): Boolean {
        return oldItem == newItem
    }
}
