package com.example.keynews.ui.articles

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.keynews.R

/**
 * Manager for showing AI filtering progress in the UI.
 * Displays a progress bar and status text during AI filtering.
 */
class AiFilterProgressManager(
    private val context: Context,
    private val infoBar: ViewGroup,
    private val readingProgressTextView: TextView
) {
    private var progressView: View? = null
    private var progressBar: ProgressBar? = null
    private var statusText: TextView? = null
    private var filteringActive = false
    
    // Progress tracking
    private var currentBatch = 0
    private var totalBatches = 0
    private var processedArticles = 0
    private var totalArticles = 0
    
    /**
     * Show the AI filtering progress bar
     */
    fun showProgress(totalArticlesToProcess: Int) {
        if (progressView == null) {
            createProgressView()
        }
        
        totalArticles = totalArticlesToProcess
        processedArticles = 0
        currentBatch = 0
        totalBatches = 0
        filteringActive = true
        
        progressView?.visibility = View.VISIBLE
        progressBar?.progress = 0
        updateStatusText()
        
        // Save original reading progress text
        originalReadingProgressText = readingProgressTextView.text
        readingProgressTextView.text = "AI filtering in progress..."
    }
    
    /**
     * Update progress values
     */
    fun updateProgress(current: Int, total: Int, currentBatch: Int, totalBatches: Int) {
        if (!filteringActive) return
        
        this.processedArticles = current
        this.totalArticles = total
        this.currentBatch = currentBatch
        this.totalBatches = totalBatches
        
        val progress = if (total > 0) (current * 100 / total) else 0
        progressBar?.progress = progress
        
        updateStatusText()
    }
    
    /**
     * Hide progress bar
     */
    fun hideProgress() {
        filteringActive = false
        progressView?.visibility = View.GONE
        
        // Restore original reading progress text
        if (originalReadingProgressText != null) {
            readingProgressTextView.text = originalReadingProgressText
            originalReadingProgressText = null
        }
    }
    
    private var originalReadingProgressText: CharSequence? = null
    
    /**
     * Create the progress view to be added to the info bar
     */
    private fun createProgressView() {
        progressView = LayoutInflater.from(context)
            .inflate(R.layout.progress_ai_filtering, infoBar, false)
        
        progressBar = progressView?.findViewById(R.id.progressBarAiFiltering)
        statusText = progressView?.findViewById(R.id.tvAiFilteringStatus)
        
        infoBar.addView(progressView)
    }
    
    /**
     * Update the status text based on current progress
     */
    private fun updateStatusText() {
        val percent = if (totalArticles > 0) (processedArticles * 100 / totalArticles) else 0
        val statusMessage = if (totalBatches > 0) {
            "AI filtering: $processedArticles/$totalArticles articles ($percent%) - Batch $currentBatch/$totalBatches"
        } else {
            "AI filtering: $processedArticles/$totalArticles articles ($percent%)"
        }
        
        statusText?.text = statusMessage
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        if (progressView != null && progressView?.parent == infoBar) {
            infoBar.removeView(progressView)
        }
        progressView = null
        progressBar = null
        statusText = null
    }
}
