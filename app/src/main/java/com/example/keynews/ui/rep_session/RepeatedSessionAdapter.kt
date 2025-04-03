package com.example.keynews.ui.rep_session

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.data.model.ReadingFeed
import com.example.keynews.data.model.RepeatedSessionWithRules
import com.example.keynews.data.model.RuleType
import com.example.keynews.databinding.ItemRepeatedSessionBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.keynews.KeyNewsApp
import java.text.SimpleDateFormat
import java.util.*

class RepeatedSessionAdapter(
    private val onEditClick: (Long) -> Unit,
        private val onDeleteClick: (Long) -> Unit
) : ListAdapter<RepeatedSessionWithRules, RepeatedSessionAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val feedCache = mutableMapOf<Long, ReadingFeed?>() // Cache for feed names

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRepeatedSessionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRepeatedSessionBinding) :
    RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RepeatedSessionWithRules) {
            binding.tvSessionName.text = item.session.name
            binding.tvHeadlinesCount.text = "${item.session.headlinesPerSession} headlines per session"
            binding.tvDelay.text = "Delay: ${item.session.delayBetweenHeadlinesSec}s between headlines"

            // Format rules in a readable way
            val rulesText = formatRules(item)
            binding.tvRules.text = rulesText

            // Fetch and display feed name
            fetchFeedName(item.session.feedId) { feedName ->
                binding.tvFeedName.text = feedName ?: "Unknown Feed"
            }

            // Set click listeners
            binding.btnEdit.setOnClickListener { onEditClick(item.session.id!!) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item.session.id!!) }
        }

        private fun formatRules(item: RepeatedSessionWithRules): String {
            if (item.rules.isEmpty()) return "No rules set"

                return item.rules.joinToString("\n") { rule ->
                    when (rule.type) {
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

        private fun fetchFeedName(feedId: Long, callback: (String?) -> Unit) {
            // Check if we've already cached this feed
            if (feedCache.containsKey(feedId)) {
                callback(feedCache[feedId]?.name)
                return
            }

            // Otherwise fetch it
            coroutineScope.launch {
                val app = binding.root.context.applicationContext as? KeyNewsApp

                if (app != null) {
                    val feed = withContext(Dispatchers.IO) {
                        app.dataManager.database.readingFeedDao().getReadingFeedById(feedId)
                    }

                    // Cache it for future use
                    feedCache[feedId] = feed
                    callback(feed?.name)
                } else {
                    callback(null)
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RepeatedSessionWithRules>() {
            override fun areItemsTheSame(
                oldItem: RepeatedSessionWithRules,
                newItem: RepeatedSessionWithRules
            ): Boolean {
                return oldItem.session.id == newItem.session.id
            }

            override fun areContentsTheSame(
                oldItem: RepeatedSessionWithRules,
                newItem: RepeatedSessionWithRules
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
