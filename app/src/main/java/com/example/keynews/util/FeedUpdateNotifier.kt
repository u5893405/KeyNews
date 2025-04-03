package com.example.keynews.util

import kotlinx.coroutines.CompletableDeferred

object FeedUpdateNotifier {
    // A deferred that will be completed when the UI update finishes.
    private var updateDeferred: CompletableDeferred<Unit>? = null

        // Call from UI (e.g. ArticlesFragment.onResume) to register that it wants to be notified.
        fun register() {
            if (updateDeferred == null || updateDeferred?.isCompleted == true) {
                updateDeferred = CompletableDeferred()
            }
        }

        // Call from UI (e.g. after loadArticles finishes) to signal that the UI has updated.
        fun notifyUpdated() {
            updateDeferred?.complete(Unit)
            updateDeferred = null
        }

        // Optionally call on UI exit to clear any waiting callback.
        fun unregister() {
            updateDeferred = null
        }

        // TTS service will await this if registered.
        suspend fun waitForUpdate() {
            updateDeferred?.await()
        }

        fun isRegistered(): Boolean {
            return updateDeferred != null && !updateDeferred!!.isCompleted
        }
}
