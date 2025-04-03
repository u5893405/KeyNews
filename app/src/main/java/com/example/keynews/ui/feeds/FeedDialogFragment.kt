package com.example.keynews.ui.feeds

// CodeCleaner_Start_90a966ce-eed1-447b-8574-a096368bb229
import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.example.keynews.databinding.DialogAddFeedBinding

class FeedDialogFragment(
    private val onSaveClicked: (String) -> Unit
) : DialogFragment() {

    private var _binding: DialogAddFeedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddFeedBinding.inflate(layoutInflater)

        val dialog = Dialog(requireContext())
        dialog.setContentView(binding.root)

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        binding.btnSave.setOnClickListener {
            val feedName = binding.etFeedName.text.toString().trim()
            onSaveClicked(feedName)
            dismiss()
        }
        binding.btnCancel.setOnClickListener { dismiss() }

        return dialog
    }
}
// CodeCleaner_End_90a966ce-eed1-447b-8574-a096368bb229

