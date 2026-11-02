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
    /** The load in progress, if any. */
    private var job: Job? = null

    /** Whether a load is in progress. */
    val isLoading: Boolean get() = job?.isActive == true

    /**
     * Starts a load, cancelling the one in progress. If this load is cancelled in turn (a newer request, or
     * [cancel]) while it runs, its result is dropped.
     */
    fun reload() {
        job?.cancel()
        job = scope.launch {
            val result = load()
            coroutineContext.ensureActive()
            apply(result)
        }
    }

    /** Restarts a load that's in progress, since it started before a change and its result would be outdated. */
    fun restartIfLoading() {
        if (isLoading) reload()
    }

    /** Cancels the load in progress, if any; its result won't be applied. */
    fun cancel() {
        job?.cancel()
        job = null
    }
}
