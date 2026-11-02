package com.mephilia.gallery.ui.components

import androidx.compose.ui.geometry.Rect

/** A normalized (0 to 1) crop covering the whole content. */
val FullCrop = Rect(0f, 0f, 1f, 1f)

/** Quarter turns in a full turn. */
private const val QUARTER_TURNS = 4

/**
 * A crop aspect ratio offered in the editors.
 *
 * @param ratio width / height, or null for a free crop.
 */
data class AspectPreset(val label: String, val ratio: Float?)

/** The crop ratios offered: free, the content's own ([originalRatio]), then the usual photo and screen ratios. */
fun aspectPresets(originalRatio: Float, freeLabel: String, originalLabel: String) = listOf(
    AspectPreset(freeLabel, null),
    AspectPreset(originalLabel, originalRatio),
    AspectPreset("1:1", 1f),
    AspectPreset("4:5", 4f / 5f),
    AspectPreset("3:4", 3f / 4f),
    AspectPreset("4:3", 4f / 3f),
    AspectPreset("9:16", 9f / 16f),
    AspectPreset("16:9", 16f / 9f),
)

/**
 * This normalized crop turned along with the picture by [quarterTurns] (positive = clockwise),
 * so it keeps covering the same part of it. A clockwise turn moves the point (x, y) to (1 − y, x).
 */
fun Rect.rotatedBy(quarterTurns: Int): Rect {
    var rect = this
    repeat(quarterTurns.mod(QUARTER_TURNS)) {
        rect = Rect(1f - rect.bottom, rect.left, 1f - rect.top, rect.right)
    }
    return rect
}

/**
 * The largest centered crop with the pixel aspect [ratio] inside content of aspect [contentRatio], normalized;
 * the whole content when [ratio] is null.
 */
fun centeredCrop(ratio: Float?, contentRatio: Float): Rect {
    if (ratio == null) return FullCrop
    val relative = ratio / contentRatio
    val (width, height) = if (relative >= 1f) 1f to 1f / relative else relative to 1f
    return Rect((1f - width) / 2f, (1f - height) / 2f, (1f + width) / 2f, (1f + height) / 2f)
}
