package com.example.keynews.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.R
import com.example.keynews.data.model.AiRule

class AiRuleSelectAdapter(
    private val onRuleSelected: (AiRule?) -> Unit,
    private val selectedRuleId: Long? = null,
    private val showNoneOption: Boolean = true
) : ListAdapter<AiRule, RecyclerView.ViewHolder>(AiRuleDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_NONE = 0
        private const val VIEW_TYPE_RULE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (showNoneOption && position == 0) VIEW_TYPE_NONE else VIEW_TYPE_RULE
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + if (showNoneOption) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_NONE) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_rule, parent, false)
            NoneViewHolder(view, onRuleSelected)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_rule, parent, false)
            RuleViewHolder(view, onRuleSelected)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is NoneViewHolder) {
            holder.bind(selectedRuleId == null)
        } else if (holder is RuleViewHolder) {
            val actualPosition = if (showNoneOption) position - 1 else position
            val rule = getItem(actualPosition)
            holder.bind(rule, rule.id == selectedRuleId)
        }
    }

    class NoneViewHolder(itemView: View, private val onRuleSelected: (AiRule?) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvRuleName: TextView = itemView.findViewById(R.id.tvRuleName)
        private val tvRuleText: TextView = itemView.findViewById(R.id.tvRuleText)
        private val tvRuleType: TextView = itemView.findViewById(R.id.tvRuleType)
        private val ivRuleType: ImageView = itemView.findViewById(R.id.ivRuleType)

        fun bind(isSelected: Boolean) {
            tvRuleName.text = "None (No Rule)"
            tvRuleText.text = "Do not apply AI filtering"
            tvRuleType.visibility = View.GONE
            ivRuleType.visibility = View.GONE

            // Hide edit and delete buttons
            itemView.findViewById<View>(R.id.btnEdit).visibility = View.GONE
            itemView.findViewById<View>(R.id.btnDelete).visibility = View.GONE

            // Add selected indicator
            if (isSelected) {
                itemView.setBackgroundResource(android.R.color.holo_blue_light)
            } else {
                itemView.setBackgroundResource(android.R.color.transparent)
            }

            itemView.setOnClickListener { onRuleSelected(null) }
        }
    }

    class RuleViewHolder(itemView: View, private val onRuleSelected: (AiRule) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvRuleName: TextView = itemView.findViewById(R.id.tvRuleName)
        private val tvRuleText: TextView = itemView.findViewById(R.id.tvRuleText)
        private val tvRuleType: TextView = itemView.findViewById(R.id.tvRuleType)
        private val ivRuleType: ImageView = itemView.findViewById(R.id.ivRuleType)

        fun bind(rule: AiRule, isSelected: Boolean) {
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

            // Hide edit and delete buttons
            itemView.findViewById<View>(R.id.btnEdit).visibility = View.GONE
            itemView.findViewById<View>(R.id.btnDelete).visibility = View.GONE

            // Add selected indicator
            if (isSelected) {
                itemView.setBackgroundResource(android.R.color.holo_blue_light)
            } else {
                itemView.setBackgroundResource(android.R.color.transparent)
            }

            itemView.setOnClickListener { onRuleSelected(rule) }
        }
    }
}
