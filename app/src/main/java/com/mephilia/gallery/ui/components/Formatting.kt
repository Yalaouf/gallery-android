package com.mephilia.gallery.ui.components

/** Milliseconds in a second. */
private const val MILLIS_PER_SECOND = 1000L

/** Seconds in a minute. */
private const val SECONDS_PER_MINUTE = 60L

/** Seconds in an hour. */
private const val SECONDS_PER_HOUR = 3600L

/**
 * A duration in [ms] as shown on videos: "m:ss", or "h:mm:ss" from one hour on.
 * Partial seconds are dropped.
 */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
