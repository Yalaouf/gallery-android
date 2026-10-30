package com.mephilia.gallery

import android.app.Activity

/**
 * Requests the highest refresh rate (e.g. 120 Hz) at the current resolution.
 * Without it, some phones keep apps at 60 Hz.
 */
fun Activity.requestHighestRefreshRate() {
    val display = display ?: return
    val current = display.mode
    val best = display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .maxByOrNull { it.refreshRate } ?: return
    window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
}
