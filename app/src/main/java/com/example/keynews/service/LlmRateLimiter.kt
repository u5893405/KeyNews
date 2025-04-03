package com.example.keynews.service

import android.util.Log
import kotlinx.coroutines.delay

/**
 * Helper class to manage rate limiting for LLM API calls.
 * Ensures we don't exceed 15 requests per minute by enforcing 
 * a minimum delay between requests.
 */
class LlmRateLimiter {
    companion object {
        private const val TAG = "LlmRateLimiter"
        private const val MIN_DELAY_MS = 4500L // 4.5 seconds between requests
    }
    
    private val requestQueue = mutableListOf<suspend () -> Unit>()
    private var lastRequestTime = 0L
    private var isProcessing = false
    
    /**
     * Ensures appropriate delay between API requests
     */
    suspend fun enqueueRequest(request: suspend () -> Unit = {}) {
        Log.d(TAG, "Enqueuing request, queue size: ${requestQueue.size}")
        requestQueue.add(request)
        if (!isProcessing) {
            processQueue()
        } else {
            // If already processing, just wait for the rate limiting to take effect
            waitForTurn()
        }
    }
    
    /**
     * Waits for this request's turn in the queue
     */
    private suspend fun waitForTurn() {
        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - lastRequestTime
        
        if (lastRequestTime > 0 && timeElapsed < MIN_DELAY_MS) {
            // Need to wait before next request
            val waitTime = MIN_DELAY_MS - timeElapsed
            Log.d(TAG, "Rate limiting: waiting ${waitTime}ms for turn")
            delay(waitTime)
        }
        
        // Update last request time
        lastRequestTime = System.currentTimeMillis()
    }
    
    /**
     * Processes the queue of requests with appropriate delays
     */
    private suspend fun processQueue() {
        isProcessing = true
        Log.d(TAG, "Starting to process queue with ${requestQueue.size} requests")
        
        while (requestQueue.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            val timeElapsed = currentTime - lastRequestTime
            
            if (lastRequestTime > 0 && timeElapsed < MIN_DELAY_MS) {
                // Need to wait before next request
                val waitTime = MIN_DELAY_MS - timeElapsed
                Log.d(TAG, "Rate limiting: waiting ${waitTime}ms before next request")
                delay(waitTime)
            }
            
            // Execute the next request
            val nextRequest = requestQueue.removeAt(0)
            Log.d(TAG, "Executing request, remaining in queue: ${requestQueue.size}")
            lastRequestTime = System.currentTimeMillis()
            nextRequest()
        }
        
        isProcessing = false
        Log.d(TAG, "Queue processing complete")
    }
}
