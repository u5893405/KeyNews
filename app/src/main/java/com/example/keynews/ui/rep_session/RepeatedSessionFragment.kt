package com.example.keynews.ui.rep_session

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.keynews.KeyNewsApp
import com.example.keynews.databinding.FragmentRepeatedSessionBinding
import com.example.keynews.ui.rep_session.RepeatedSessionAdapter
import kotlinx.coroutines.launch

class RepeatedSessionFragment : Fragment() {

    private var _binding: FragmentRepeatedSessionBinding? = null
        private val binding get() = _binding!!

        private lateinit var adapter: RepeatedSessionAdapter

            override fun onCreateView(
                inflater: LayoutInflater, container: ViewGroup?,
                savedInstanceState: Bundle?
            ): View {
                _binding = FragmentRepeatedSessionBinding.inflate(inflater, container, false)
                return binding.root
            }

            override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                super.onViewCreated(view, savedInstanceState)

                // Initialize the adapter with callbacks
                adapter = RepeatedSessionAdapter(
                    onEditClick = { sessionId: Long -> editRepeatedSession(sessionId) },
                    onDeleteClick = { sessionId: Long -> deleteRepeatedSession(sessionId) }
                )

                // Set up RecyclerView
                binding.rvRepeatedSessions.apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = this@RepeatedSessionFragment.adapter
                }

                // Set up add button
                binding.fabAddSession.setOnClickListener {
                    showAddSessionDialog()
                }
            }

            override fun onResume() {
                super.onResume()
                loadRepeatedSessions()
            }

            private fun loadRepeatedSessions() {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager

                lifecycleScope.launch {
                    val sessions = dataManager.database.repeatedSessionDao().getRepeatedSessionsWithRules()
                    adapter.submitList(sessions)

                    // Show empty state if no sessions
                    binding.tvEmptyState.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                }
            }

            private fun showAddSessionDialog() {
                val dialog = RepeatedSessionDialogFragment {
                    // Reload the list after a new session is added
                    loadRepeatedSessions()
                }
                dialog.show(parentFragmentManager, "AddRepeatedSessionDialog")
            }

            private fun editRepeatedSession(sessionId: Long) {
                val dialog = RepeatedSessionDialogFragment(
                    sessionId = sessionId,
                    onComplete = { loadRepeatedSessions() }
                )
                dialog.show(parentFragmentManager, "EditRepeatedSessionDialog")
            }

            private fun deleteRepeatedSession(sessionId: Long) {
                val dataManager = (requireActivity().application as KeyNewsApp).dataManager

                lifecycleScope.launch {
                    // First, cancel any pending alarms for this session
                    RepeatedSessionScheduler.cancelAlarmsForSession(requireContext(), sessionId)

                    // Then delete from database
                    dataManager.database.repeatedSessionDao().deleteRepeatedSessionById(sessionId)

                    // Refresh the list
                    loadRepeatedSessions()
                }
            }

            override fun onDestroyView() {
                super.onDestroyView()
                _binding = null
            }
}
