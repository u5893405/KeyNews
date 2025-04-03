package com.example.keynews.ui.rep_session

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.keynews.KeyNewsApp
import com.example.keynews.data.model.ReadingFeed
import com.example.keynews.data.model.RepeatedSession
import com.example.keynews.data.model.RepeatedSessionRule
import com.example.keynews.data.model.RepeatedSessionWithRules
import com.example.keynews.data.model.RuleType
import com.example.keynews.databinding.DialogRepeatedSessionBinding
import com.example.keynews.ui.rep_session.RuleAdapter
import com.example.keynews.ui.rep_session.TimePickerFragment
import com.example.keynews.ui.rep_session.RepeatedSessionScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RepeatedSessionDialogFragment(
    private val sessionId: Long = 0L,
        private val onComplete: () -> Unit
) : DialogFragment() {

    private var _binding: DialogRepeatedSessionBinding? = null
        private val binding get() = _binding!!

        private var feeds = listOf<ReadingFeed>()
        private val rules = mutableListOf<RepeatedSessionRule>()
        private var selectedFeedId: Long = 0L
            private var isEditMode = false
            private var ruleAdapter: RuleAdapter? = null

                // ------------------------------------------------
                // onCreateDialog
                // ------------------------------------------------
                override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                    _binding = DialogRepeatedSessionBinding.inflate(layoutInflater)
                    isEditMode = (sessionId != 0L)

                    val dialog = Dialog(requireContext())
                    dialog.setContentView(binding.root)
                    dialog.window?.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT
                    )

                    setupUI()
                    loadData()

                    return dialog
                }

                // ------------------------------------------------
                // setupUI
                // ------------------------------------------------
                private fun setupUI() {
                    ruleAdapter = RuleAdapter(
                        rules,
                        onDeleteRule = { position: Int ->
                            if (position < rules.size) {
                                rules.removeAt(position)
                                ruleAdapter?.notifyItemRemoved(position)
                                updateRuleCount()
                            }
                        }
                    )

                    binding.rvRules.adapter = ruleAdapter

                    binding.btnAddRule.setOnClickListener {
                        if (rules.size >= 10) {
                            Toast.makeText(context, "Maximum 10 rules allowed", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        showAddRuleDialog()
                    }

                    binding.btnSave.setOnClickListener {
                        saveSession()
                    }
                    binding.btnCancel.setOnClickListener {
                        dismiss()
                    }

                    binding.tvDialogTitle.text =
                    if (isEditMode) "Edit Repeated Session" else "Create Repeated Session"

                        // Show or hide "Start after creation" only if not edit
                        binding.checkStartAfterCreation.isEnabled = !isEditMode
                        binding.checkStartAfterCreation.isChecked = false

                        // Add "Read article body?" toggle
                        // If you haven't placed it in dialog_repeated_session.xml, do so. Suppose it has ID checkReadBody.
                        binding.checkReadBody.visibility = View.VISIBLE
                }

                // ------------------------------------------------
                // loadData
                // ------------------------------------------------
                private fun loadData() {
                    val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                    lifecycleScope.launch {
                        // 1) Load all reading feeds
                        feeds = withContext(Dispatchers.IO) {
                            dataManager.database.readingFeedDao().getAllReadingFeeds()
                        }
                        setupFeedSpinner()

                        // 2) If editing, load existing session + rules
                        if (isEditMode) {
                            val sessionWithRules = withContext(Dispatchers.IO) {
                                dataManager.database.repeatedSessionDao().getRepeatedSessionWithRules(sessionId)
                            }
                            sessionWithRules?.let { populateFields(it) }
                        }
                        updateRuleCount()
                    }
                }

                // ------------------------------------------------
                // setupFeedSpinner
                // ------------------------------------------------
                private fun setupFeedSpinner() {
                    val feedNames = feeds.map { it.name }
                    val adapter = ArrayAdapter(
                        requireContext(),
                                               android.R.layout.simple_spinner_item,
                                               feedNames
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerFeed.adapter = adapter

                    binding.spinnerFeed.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            if (position < feeds.size) {
                                selectedFeedId = feeds[position].id
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                }

                // ------------------------------------------------
                // populateFields
                // ------------------------------------------------
                private fun populateFields(sessionWithRules: RepeatedSessionWithRules) {
                    val session = sessionWithRules.session
                    binding.etSessionName.setText(session.name)
                    binding.etHeadlinesCount.setText(session.headlinesPerSession.toString())
                    binding.etDelay.setText(session.delayBetweenHeadlinesSec.toString())

                    // If your RepeatedSession has readBody:
                    binding.checkReadBody.isChecked = session.readBody
                    
                    // Set the announce article age checkbox
                    binding.checkAnnounceArticleAge.isChecked = session.announceArticleAge
                    
                    // Set the article age threshold value if available
                    session.articleAgeThresholdMinutes?.let {
                        binding.etArticleAgeThreshold.setText(it.toString())
                    }

                    val feedIndex = feeds.indexOfFirst { it.id == session.feedId }
                    if (feedIndex >= 0) {
                        binding.spinnerFeed.setSelection(feedIndex)
                        selectedFeedId = session.feedId
                    }

                    // Prepare rules
                    rules.clear()
                    rules.addAll(sessionWithRules.rules)
                    ruleAdapter?.notifyDataSetChanged()
                }

                // ------------------------------------------------
                // updateRuleCount
                // ------------------------------------------------
                private fun updateRuleCount() {
                    binding.tvRuleCount.text = "${rules.size}/10 rules"
                }

                // ------------------------------------------------
                // showAddRuleDialog
                // ------------------------------------------------
                private fun showAddRuleDialog() {
                    val dialogView = layoutInflater.inflate(com.example.keynews.R.layout.dialog_add_rule, null)
                    val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Add Rule")
                    .setView(dialogView)
                    .setPositiveButton("Add") { _, _ -> }
                    .setNegativeButton("Cancel", null)
                    .create()

                    dialog.show()

                    val rbInterval = dialogView.findViewById<android.widget.RadioButton>(
                        com.example.keynews.R.id.rbInterval
                    )
                    val rbSchedule = dialogView.findViewById<android.widget.RadioButton>(
                        com.example.keynews.R.id.rbSchedule
                    )
                    val layoutInterval = dialogView.findViewById<android.view.View>(
                        com.example.keynews.R.id.layoutInterval
                    )
                    val layoutSchedule = dialogView.findViewById<android.view.View>(
                        com.example.keynews.R.id.layoutSchedule
                    )
                    val etIntervalValue = dialogView.findViewById<android.widget.EditText>(
                        com.example.keynews.R.id.etIntervalValue
                    )
                    val spinnerIntervalUnit = dialogView.findViewById<android.widget.Spinner>(
                        com.example.keynews.R.id.spinnerIntervalUnit
                    )
                    val etTime = dialogView.findViewById<android.widget.EditText>(
                        com.example.keynews.R.id.etTime
                    )
                    val cbMonday = dialogView.findViewById<android.widget.CheckBox>(
                        com.example.keynews.R.id.cbMonday
                    )
                    val cbTuesday = dialogView.findViewById<android.widget.CheckBox>(
                        com.example.keynews.R.id.cbTuesday
                    )
                    val cbWednesday = dialogView.findViewById<android.widget.CheckBox>(
                        com.example.keynews.R.id.cbWednesday
                    )
                    val cbThursday = dialogView.findViewById<android.widget.CheckBox>(
                        com.example.keynews.R.id.cbThursday
                    )
                    val cbFriday = dialogView.findViewById<android.widget.CheckBox>(
                        com.example.keynews.R.id.cbFriday
                    )
                    val cbSaturday = dialogView.findViewById<android.widget.CheckBox>(
                        com.example.keynews.R.id.cbSaturday
                    )
                    val cbSunday = dialogView.findViewById<android.widget.CheckBox>(
                        com.example.keynews.R.id.cbSunday
                    )

                    layoutInterval.visibility =
                    if (rbInterval.isChecked) View.VISIBLE else View.GONE
                        layoutSchedule.visibility =
                        if (rbSchedule.isChecked) View.VISIBLE else View.GONE

                            rbInterval.setOnCheckedChangeListener { _, isChecked ->
                                layoutInterval.visibility = if (isChecked) View.VISIBLE else View.GONE
                            }
                            rbSchedule.setOnCheckedChangeListener { _, isChecked ->
                                layoutSchedule.visibility = if (isChecked) View.VISIBLE else View.GONE
                            }

                            val unitAdapter = ArrayAdapter.createFromResource(
                                requireContext(),
                                                                              com.example.keynews.R.array.interval_units,
                                                                              android.R.layout.simple_spinner_item
                            )
                            unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            spinnerIntervalUnit.adapter = unitAdapter

                            etTime.setOnClickListener {
                                val timePicker = TimePickerFragment { hourOfDay: Int, minute: Int ->
                                    val hourStr = hourOfDay.toString().padStart(2, '0')
                                    val minuteStr = minute.toString().padStart(2, '0')
                                    etTime.setText("$hourStr:$minuteStr")
                                }
                                timePicker.show(parentFragmentManager, "timePicker")
                            }

                            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                                if (rbInterval.isChecked) {
                                    val intervalValue = etIntervalValue.text.toString().toIntOrNull()
                                    if (intervalValue == null || intervalValue <= 0) {
                                        Toast.makeText(context, "Please enter a valid interval", Toast.LENGTH_SHORT)
                                        .show()
                                        return@setOnClickListener
                                    }
                                    val unitMultiplier = when (spinnerIntervalUnit.selectedItemPosition) {
                                        0 -> 1       // minutes
                                        1 -> 60      // hours
                                        2 -> 60 * 24 // days
                                        else -> 1
                                    }
                                    val intervalMinutes = intervalValue * unitMultiplier

                                    rules.add(
                                        RepeatedSessionRule(
                                            sessionId = sessionId,
                                            type = RuleType.INTERVAL,
                                            intervalMinutes = intervalMinutes
                                        )
                                    )
                                } else {
                                    // schedule
                                    val timeText = etTime.text.toString()
                                    if (!timeText.matches(Regex("\\d{2}:\\d{2}"))) {
                                        Toast.makeText(
                                            context,
                                            "Please enter a valid time (HH:MM)",
                                                       Toast.LENGTH_SHORT
                                        ).show()
                                        return@setOnClickListener
                                    }

                                    val selectedDays = mutableListOf<Int>()
                                    if (cbMonday.isChecked) selectedDays.add(1)
                                        if (cbTuesday.isChecked) selectedDays.add(2)
                                            if (cbWednesday.isChecked) selectedDays.add(3)
                                                if (cbThursday.isChecked) selectedDays.add(4)
                                                    if (cbFriday.isChecked) selectedDays.add(5)
                                                        if (cbSaturday.isChecked) selectedDays.add(6)
                                                            if (cbSunday.isChecked) selectedDays.add(7)

                                                                if (selectedDays.isEmpty()) {
                                                                    Toast.makeText(context, "Please select at least one day", Toast.LENGTH_SHORT)
                                                                    .show()
                                                                    return@setOnClickListener
                                                                }
                                                                val daysOfWeekStr = selectedDays.joinToString(",")

                                                                rules.add(
                                                                    RepeatedSessionRule(
                                                                        sessionId = sessionId,
                                                                        type = RuleType.SCHEDULE,
                                                                        timeOfDay = timeText,
                                                                        daysOfWeek = daysOfWeekStr
                                                                    )
                                                                )
                                }

                                ruleAdapter?.notifyItemInserted(rules.size - 1)
                                updateRuleCount()
                                dialog.dismiss()
                            }
                }

                // ------------------------------------------------
                // saveSession
                // ------------------------------------------------
                private fun saveSession() {
                    val name = binding.etSessionName.text.toString().trim()
                    if (name.isEmpty()) {
                        Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                        return
                    }
                    if (selectedFeedId == 0L) {
                        Toast.makeText(context, "Please select a feed", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val headlinesCount =
                    binding.etHeadlinesCount.text.toString().toIntOrNull() ?: 10
                    val delay =
                    binding.etDelay.text.toString().toIntOrNull() ?: 4
                    
                    // Parse article age threshold (can be null)
                    val ageThresholdText = binding.etArticleAgeThreshold.text.toString().trim()
                    val ageThresholdMinutes = if (ageThresholdText.isNotEmpty()) {
                        ageThresholdText.toIntOrNull()
                    } else null
                    
                    if (rules.isEmpty()) {
                        Toast.makeText(context, "Please add at least one rule", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val dataManager = (requireActivity().application as KeyNewsApp).dataManager
                    val readBodyFlag = binding.checkReadBody.isChecked
                    val announceArticleAgeFlag = binding.checkAnnounceArticleAge.isChecked

                    lifecycleScope.launch {
                        val session = RepeatedSession(
                            id = if (isEditMode) sessionId else null,
                                                      name = name,
                                                      feedId = selectedFeedId,
                                                      headlinesPerSession = headlinesCount,
                                                      delayBetweenHeadlinesSec = delay,
                                                      readBody = readBodyFlag,
                                                      articleAgeThresholdMinutes = ageThresholdMinutes,
                                                      announceArticleAge = announceArticleAgeFlag
                        )

                        val newSessionId = withContext(Dispatchers.IO) {
                            dataManager.database.repeatedSessionDao().insertRepeatedSession(session)
                        }

                        if (isEditMode) {
                            // Remove old rules
                            withContext(Dispatchers.IO) {
                                val existingRules =
                                dataManager.database.repeatedSessionDao().getRulesForSession(sessionId)

                                // Cancel old alarms
                                for (rule in existingRules) {
                                    RepeatedSessionScheduler.cancelAlarmForRule(requireContext(), rule.id)
                                }
                                // Now delete them
                                for (rule in existingRules) {
                                    dataManager.database.repeatedSessionDao().deleteRuleById(rule.id)
                                }
                            }
                        }

                        val sessionToSchedule = session.copy(id = newSessionId)
                        val actualSessionId = if (isEditMode) sessionId else newSessionId

                        // Insert new rules
                        for (rule in rules) {
                            // By default, let's say schedule rules are active,
                            // while interval rules are not automatically active unless you want them so.
                            // If you prefer otherwise, modify as needed
                            val isActiveByDefault = rule.type == RuleType.SCHEDULE
                            val updatedRule = rule.copy(
                                sessionId = actualSessionId,
                                isActive = isActiveByDefault
                            )
                            val ruleId = withContext(Dispatchers.IO) {
                                dataManager.database.repeatedSessionDao().insertRepeatedSessionRule(updatedRule)
                            }

                            val ruleWithId = updatedRule.copy(id = ruleId)
                            // If it's a schedule rule, schedule it
                            if (ruleWithId.type == RuleType.SCHEDULE) {
                                RepeatedSessionScheduler.scheduleAlarmForRule(
                                    context = requireContext(),
                                                                              rule = ruleWithId,
                                                                              session = sessionToSchedule
                                )
                            }
                        }

                        // "Start after creation" => same as "Start now" for interval
                        if (!isEditMode && binding.checkStartAfterCreation.isChecked) {
                            // Only call startSessionNow(...) and do *not* schedule the next run for intervals
                            RepeatedSessionScheduler.startSessionNow(requireContext(), actualSessionId)
                        }

                        onComplete()
                        dismiss()
                    }
                }

                override fun onDestroyView() {
                    super.onDestroyView()
                    _binding = null
                }
}
