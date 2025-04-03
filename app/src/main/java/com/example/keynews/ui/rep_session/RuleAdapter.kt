package com.example.keynews.ui.rep_session

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.R
import com.example.keynews.data.model.RepeatedSessionRule
import com.example.keynews.data.model.RuleType

class RuleAdapter(
    private val rules: List<RepeatedSessionRule>,
        private val onDeleteRule: (Int) -> Unit
) : RecyclerView.Adapter<RuleAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
        .inflate(R.layout.item_rule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rules[position])
    }

    override fun getItemCount(): Int = rules.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRuleDescription: TextView = itemView.findViewById(R.id.tvRuleDescription)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteRule)

                fun bind(rule: RepeatedSessionRule) {
                    tvRuleDescription.text = formatRuleDescription(rule)

                    btnDelete.setOnClickListener {
                        onDeleteRule(adapterPosition)
                    }
                }

                private fun formatRuleDescription(rule: RepeatedSessionRule): String {
                    return when (rule.type) {
                        RuleType.INTERVAL -> {
                            val interval = rule.intervalMinutes ?: 0
                            when {
                                interval < 60 -> "Every $interval minutes"
                                interval % 60 == 0 -> "Every ${interval / 60} hours"
                                else -> "Every ${interval / 60}h ${interval % 60}m"
                            }
                        }
                        RuleType.SCHEDULE -> {
                            val days = formatDaysOfWeek(rule.daysOfWeek)
                            val time = rule.timeOfDay ?: ""
                            "$days at $time"
                        }
                    }
                }

                private fun formatDaysOfWeek(daysString: String?): String {
                    if (daysString.isNullOrEmpty()) return "Every day"

                        val daysList = daysString.split(",").mapNotNull { it.toIntOrNull() }
                        if (daysList.isEmpty()) return "Every day"

                            val daysAbbreviations = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

                            // Check if it's all days
                            if (daysList.size == 7) return "Every day"

                                // Check if it's weekdays
                                if (daysList.size == 5 && daysList.containsAll(listOf(1, 2, 3, 4, 5)))
                                    return "Weekdays"

                                    // Check if it's weekends
                                    if (daysList.size == 2 && daysList.containsAll(listOf(6, 7)))
                                        return "Weekends"

                                        // Otherwise, list the days
                                        return daysList.joinToString(", ") { daysAbbreviations[it] }
                }
    }
}
