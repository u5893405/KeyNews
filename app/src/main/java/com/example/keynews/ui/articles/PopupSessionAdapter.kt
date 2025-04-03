package com.example.keynews.ui.articles

// CodeCleaner_Start_4ff03a65-21ba-4848-a2f9-b637634e118f
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.R
import com.example.keynews.data.model.RuleType

enum class StartOption {
    NOW,
    LATER,
    TIME
}

/**
 * Data class representing one interval rule row in the popup
 * If 'pendingStart' is true, we disallow changes until that start fires or user toggles it off
 */
data class PopupSessionItem(
    val sessionId: Long,
    val sessionName: String,
    val ruleId: Long,
    val ruleDescription: String,
    var isEnabled: Boolean,
    var pendingStart: Boolean = false, // If a single-time start is set and hasn't triggered yet
    var selectedOption: StartOption? = null,
    // If user picks Start after => store hours/min/sec
    var afterHours: Int? = null,
    var afterMinutes: Int? = null,
    var afterSeconds: Int? = null
)

/**
 * Adapter for the "Manage Interval Rules" popup
 */
class PopupSessionAdapter(
    private val onToggleChanged: (ruleId: Long, isEnabled: Boolean) -> Unit,
        private val onOptionSelected: (ruleId: Long, option: StartOption) -> Unit,
            private val onHoursMinutesSecondsChanged: (ruleId: Long, hours: Int, minutes: Int, seconds: Int) -> Unit
) : ListAdapter<PopupSessionItem, PopupSessionAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PopupSessionItem>() {
            override fun areItemsTheSame(oldItem: PopupSessionItem, newItem: PopupSessionItem): Boolean {
                return oldItem.ruleId == newItem.ruleId
            }
            override fun areContentsTheSame(oldItem: PopupSessionItem, newItem: PopupSessionItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
        .inflate(R.layout.item_popup_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        private val switchEnabled: Switch = itemView.findViewById(R.id.switchEnabled)
            private val tvSessionName: TextView = itemView.findViewById(R.id.tvSessionName)
                private val tvRuleInfo: TextView = itemView.findViewById(R.id.tvRuleInfo)

                    private val rgStartOptions: RadioGroup = itemView.findViewById(R.id.rgStartOptions)
                        private val rbStartNow: RadioButton = itemView.findViewById(R.id.rbStartNow)
                            private val rbStartLater: RadioButton = itemView.findViewById(R.id.rbStartLater)
                                private val rbStartTime: RadioButton = itemView.findViewById(R.id.rbStartTime)

                                    // Because user wants "x hours y minutes z seconds" in that same row for Start after:
                                    // We'll place them in a horizontal layout next to "Start after" in item_popup_session
                                    // or we embed them just below. For simplicity let's do a separate row or create them programmatically
                                    // For now, assume we have some EditTexts:
                                    private val etHours: EditText? = itemView.findViewById(R.id.etHours)
                                        private val etMinutes: EditText? = itemView.findViewById(R.id.etMinutes)
                                            private val etSeconds: EditText? = itemView.findViewById(R.id.etSeconds)

                                                fun bind(item: PopupSessionItem) {
                                                    tvSessionName.text = item.sessionName
                                                    tvRuleInfo.text = item.ruleDescription
                                                    switchEnabled.isChecked = item.isEnabled

                                                        // If there's a pending single-run start that hasn't triggered yet, lock the switch
                                                        // and radio group so user can't override it.
                                                        val locked = (item.pendingStart && item.isEnabled)

                                                        switchEnabled.isEnabled = !locked
                                                            rgStartOptions.isEnabled = !locked
                                                            for (i in 0 until rgStartOptions.childCount) {
                                                                rgStartOptions.getChildAt(i).isEnabled = !locked
                                                            }

                                                            if (locked) {
                                                                // Force them to remain in some previously chosen state
                                                                // We'll disable all input
                                                                etHours?.isEnabled = false
                                                                etMinutes?.isEnabled = false
                                                                etSeconds?.isEnabled = false
                                                            } else {
                                                                etHours?.isEnabled = rbStartLater.isChecked && item.isEnabled
                                                                etMinutes?.isEnabled = rbStartLater.isChecked && item.isEnabled
                                                                etSeconds?.isEnabled = rbStartLater.isChecked && item.isEnabled
                                                            }

                                                            // If user had selected an option earlier, reflect it:
                                                            when (item.selectedOption) {
                                                                StartOption.NOW -> rbStartNow.isChecked = true
                                                                StartOption.LATER -> rbStartLater.isChecked = true
                                                                StartOption.TIME -> rbStartTime.isChecked = true
                                                                null -> rgStartOptions.clearCheck()
                                                            }

                                                            // Hours/min/sec
                                                            etHours?.setText(item.afterHours?.toString() ?: "")
                                                            etMinutes?.setText(item.afterMinutes?.toString() ?: "")
                                                            etSeconds?.setText(item.afterSeconds?.toString() ?: "")

                                                            // Listeners
                                                            switchEnabled.setOnCheckedChangeListener(null)
                                                                rgStartOptions.setOnCheckedChangeListener(null)
                                                                etHours?.removeTextChangedListener(hoursWatcher)
                                                                etMinutes?.removeTextChangedListener(minutesWatcher)
                                                                etSeconds?.removeTextChangedListener(secondsWatcher)

                                                                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                                                                    item.isEnabled = isChecked
                                                                    onToggleChanged(item.ruleId, isChecked)
                                                                    // If toggled ON but no radio selected yet, we'll handle that in "Apply" check
                                                                    // Meanwhile, enable or disable the radio group
                                                                    rgStartOptions.isEnabled = !locked && isChecked
                                                                    for (i in 0 until rgStartOptions.childCount) {
                                                                        rgStartOptions.getChildAt(i).isEnabled = !locked && isChecked
                                                                    }
                                                                    etHours?.isEnabled = rbStartLater.isChecked && isChecked && !locked
                                                                    etMinutes?.isEnabled = rbStartLater.isChecked && isChecked && !locked
                                                                    etSeconds?.isEnabled = rbStartLater.isChecked && isChecked && !locked
                                                                }

                                                                rgStartOptions.setOnCheckedChangeListener { _, checkedId ->
                                                                    if (!item.isEnabled || locked) return@setOnCheckedChangeListener
                                                                        val option = when (checkedId) {
                                                                            R.id.rbStartNow -> StartOption.NOW
                                                                            R.id.rbStartLater -> StartOption.LATER
                                                                            R.id.rbStartTime -> StartOption.TIME
                                                                            else -> null
                                                                        }
                                                                        item.selectedOption = option
                                                                        if (option != null) {
                                                                            onOptionSelected(item.ruleId, option)
                                                                        }
                                                                        // Only enable hours/min/sec if "Start after" is chosen
                                                                        val afterEnabled = (option == StartOption.LATER && item.isEnabled)
                                                                        etHours?.isEnabled = afterEnabled
                                                                        etMinutes?.isEnabled = afterEnabled
                                                                        etSeconds?.isEnabled = afterEnabled
                                                                }

                                                                // watchers
                                                                etHours?.addTextChangedListener(hoursWatcher)
                                                                etMinutes?.addTextChangedListener(minutesWatcher)
                                                                etSeconds?.addTextChangedListener(secondsWatcher)
                                                }

                                                private val hoursWatcher = object : TextWatcher {
                                                    override fun afterTextChanged(s: Editable?) {
                                                        val position = adapterPosition
                                                        if (position != RecyclerView.NO_POSITION) {
                                                            val item = getItem(position)
                                                            val h = s?.toString()?.toIntOrNull() ?: 0
                                                            item.afterHours = h
                                                            if (item.isEnabled && item.selectedOption == StartOption.LATER && !item.pendingStart) {
                                                                onHoursMinutesSecondsChanged(item.ruleId, h, item.afterMinutes ?: 0, item.afterSeconds ?: 0)
                                                            }
                                                        }
                                                    }
                                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                                }
                                                private val minutesWatcher = object : TextWatcher {
                                                    override fun afterTextChanged(s: Editable?) {
                                                        val position = adapterPosition
                                                        if (position != RecyclerView.NO_POSITION) {
                                                            val item = getItem(position)
                                                            val m = s?.toString()?.toIntOrNull() ?: 0
                                                            item.afterMinutes = m
                                                            if (item.isEnabled && item.selectedOption == StartOption.LATER && !item.pendingStart) {
                                                                onHoursMinutesSecondsChanged(item.ruleId, item.afterHours ?: 0, m, item.afterSeconds ?: 0)
                                                            }
                                                        }
                                                    }
                                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                                }
                                                private val secondsWatcher = object : TextWatcher {
                                                    override fun afterTextChanged(s: Editable?) {
                                                        val position = adapterPosition
                                                        if (position != RecyclerView.NO_POSITION) {
                                                            val item = getItem(position)
                                                            val sec = s?.toString()?.toIntOrNull() ?: 0
                                                            item.afterSeconds = sec
                                                            if (item.isEnabled && item.selectedOption == StartOption.LATER && !item.pendingStart) {
                                                                onHoursMinutesSecondsChanged(item.ruleId, item.afterHours ?: 0, item.afterMinutes ?: 0, sec)
                                                            }
                                                        }
                                                    }
                                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                                }
    }
}

// CodeCleaner_End_4ff03a65-21ba-4848-a2f9-b637634e118f

