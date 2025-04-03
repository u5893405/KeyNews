package com.example.keynews.ui.articles

// CodeCleaner_Start_55a57a52-1b5d-4128-ad98-ff9c72fd7d57
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import java.util.Calendar

/**
 * Simple dialog fragment for picking a time
 */
class TimePickerDialogFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {
    
    private var onTimeSelectedListener: ((String) -> Unit)? = null
    
    companion object {
        fun newInstance(onTimeSelected: (String) -> Unit): TimePickerDialogFragment {
            return TimePickerDialogFragment().apply {
                onTimeSelectedListener = onTimeSelected
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use current time as the default values for the picker
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        // Create a new instance of TimePickerDialog
        return TimePickerDialog(
            requireContext(),
            this,
            hour,
            minute,
            true // 24-hour view
        )
    }
    
    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        // Format time as HH:mm and pass to listener
        val timeString = String.format("%02d:%02d", hourOfDay, minute)
        onTimeSelectedListener?.invoke(timeString)
    }
}
// CodeCleaner_End_55a57a52-1b5d-4128-ad98-ff9c72fd7d57

