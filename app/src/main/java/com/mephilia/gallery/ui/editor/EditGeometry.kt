package com.mephilia.gallery.ui.editor

import androidx.compose.ui.geometry.Rect
import com.mephilia.gallery.ui.components.FullCrop
import com.mephilia.gallery.ui.components.rotatedBy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Quarter turns in a full turn. */
const val QUARTER_TURNS_PER_TURN = 4

/** Degrees in a quarter turn. */
const val DEGREES_PER_QUARTER_TURN = 90f

/**
 * The edits of a photo (and the geometry of a GIF or video), applied in this order: [quarterTurns] clockwise,
 * the horizontal mirror ([flipH]), the fine rotation ([angle]), the [crop], then the adjustments and [marks].
 *
 * @param crop the part kept, normalized (0 to 1) in the turned, mirrored and straightened picture.
 * @param angle fine rotation in degrees, clockwise. The picture is zoomed in so the corners never show empty
 * areas; [crop] is relative to that zoomed picture.
 * @param adjust light and color adjustments.
 * @param marks drawings and hidden areas, applied last, on the cropped picture.
 */
data class PhotoEdits(
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    val crop: Rect = FullCrop,
    val angle: Float = 0f,
    val adjust: Adjustments = Adjustments(),
    val marks: List<Mark> = emptyList(),
)

/** Whether [quarterTurns] leaves the picture sideways, which swaps its width and height. */
fun isSideways(quarterTurns: Int) = quarterTurns.mod(2) == 1

/** The aspect ratio of a picture of [ratio] once turned by [quarterTurns]. */
fun turnedRatio(ratio: Float, quarterTurns: Int) = if (isSideways(quarterTurns)) 1f / ratio else ratio

/**
 * The largest scale at which a [width] × [height] picture, rotated by [angle] degrees, still covers a
 * [width] × [height] frame: how much the straighten tool must zoom in so no empty corner shows.
 */
fun straightenScale(width: Float, height: Float, angle: Float): Float {
    if (angle == 0f) return 1f
    val radians = Math.toRadians(abs(angle).toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    return min(width / (width * cosine + height * sine), height / (width * sine + height * cosine))
}

/** A normalized crop mirrored left to right, so it keeps covering the same part of a mirrored picture. */
fun Rect.mirroredHorizontally() = copy(left = 1f - right, right = 1f - left)

/** Whether these edits turn or mirror the picture. */
val PhotoEdits.isTurnedOrMirrored: Boolean get() = quarterTurns.mod(QUARTER_TURNS_PER_TURN) != 0 || flipH

/**
 * These edits turned by [delta] quarter turns (positive = clockwise). The crop turns with the picture.
 * When the picture is mirrored, a turn looks reversed on screen, hence the crop turning the other way.
 */
fun PhotoEdits.rotated(delta: Int) = copy(
    quarterTurns = quarterTurns + delta,
    crop = crop.rotatedBy(if (flipH) -delta else delta),
)

/**
 * These edits mirrored left to right. The crop follows, and the straightening angle changes sign since
 * mirroring reverses a tilt. A zero angle stays a positive zero, so unedited edits still equal the defaults.
 */
fun PhotoEdits.mirrored() = copy(
    flipH = !flipH,
    angle = if (angle == 0f) 0f else -angle,
    crop = crop.mirroredHorizontally(),
)
