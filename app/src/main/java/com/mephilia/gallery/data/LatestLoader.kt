package com.mephilia.gallery.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Runs [load] and hands its result to [apply], keeping only the latest request: starting a new load cancels
 * the one in progress, so a slow, older query can never overwrite a newer result.
 * Meant to be called from a single thread (the main thread).
 */
class LatestLoader<T>(
    private val scope: CoroutineScope,
    private val load: suspend () -> T,
    private val apply: (T) -> Unit,
) {
    private var job: Job? = null

    val isLoading: Boolean get() = job?.isActive == true

    fun reload() {
        job?.cancel()
        job = scope.launch {
            val result = load()
            // Cancelled while loading (newer request, or [cancel]): drop the result
            coroutineContext.ensureActive()
            apply(result)
        }
    }

    /** Restarts a load that's in progress, since it started before a change and its result would be outdated. */
    fun restartIfLoading() {
        if (isLoading) reload()
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
