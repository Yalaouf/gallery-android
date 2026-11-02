package com.mephilia.gallery.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.roundToInt

/** Contrast change at full strength: ±60 %. */
private const val CONTRAST_STRENGTH = 0.6f

/** Brightness change at full strength, in 8-bit levels. */
private const val BRIGHTNESS_LEVELS = 70f

/** Middle grey, around which the contrast is changed. */
private const val MID_GREY = 128f

/** Red gained and blue lost at full warmth: 12 %. */
private const val WARMTH_STRENGTH = 0.12f

/** The mosaic has about this many blocks along the picture's longest side. */
private const val MOSAIC_BLOCKS = 40

/** Smallest mosaic block, in pixels, so small pictures are still well hidden. */
private const val MIN_MOSAIC_BLOCK = 4

/** Length of the tiny line drawn for a single tap, so it still leaves a dot. */
private const val DOT_LENGTH = 0.1f

/** Thinnest stroke, in pixels. */
private const val MIN_STROKE_PX = 1f

/** Converts an adjustment (−1 to 1) to a percentage. */
private const val PERCENT = 100

/** Light and color adjustments, each from −1 to 1 (0 = unchanged). */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    /** Whether nothing is adjusted. */
    val isIdentity get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f

    /** The color matrix applying these adjustments: saturation, then contrast and brightness, then warmth. */
    fun colorMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.setSaturation(1f + saturation)
        val scale = 1f + contrast * CONTRAST_STRENGTH
        val offset = MID_GREY * (1f - scale) + brightness * BRIGHTNESS_LEVELS
        matrix.postConcat(ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, offset,
            0f, scale, 0f, 0f, offset,
            0f, 0f, scale, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )))
        val warm = warmth * WARMTH_STRENGTH
        matrix.postConcat(ColorMatrix(floatArrayOf(
            1f + warm, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - warm, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
        return matrix
    }
}

/**
 * A stroke drawn on the photo, in coordinates normalized to the edited (cropped) picture: x and y from 0 to 1,
 * [width] relative to the picture's width. [mosaic] strokes hide what's under them; the others are drawn in [color].
 */
data class Mark(val points: List<Offset>, val width: Float, val color: Int, val mosaic: Boolean)

/** A pixelated copy of this bitmap: averaged blocks, so the hidden details can't be recovered. */
fun Bitmap.mosaic(): Bitmap {
    val block = max(MIN_MOSAIC_BLOCK, max(width, height) / MOSAIC_BLOCKS)
    val small = Bitmap.createScaledBitmap(this, max(1, width / block), max(1, height / block), true)
    val big = Bitmap.createScaledBitmap(small, width, height, false)
    if (small !== big) small.recycle()
    return big
}

/** The path through normalized [points], scaled to [width] × [height]. A single point still leaves a dot. */
fun strokePath(points: List<Offset>, width: Float, height: Float): Path {
    val path = Path()
    points.forEachIndexed { i, point ->
        if (i == 0) path.moveTo(point.x * width, point.y * height) else path.lineTo(point.x * width, point.y * height)
    }
    if (points.size == 1) path.lineTo(points[0].x * width + DOT_LENGTH, points[0].y * height)
    return path
}

/** A new bitmap with [adjust] then [marks] applied, or this one if there is nothing to apply. */
fun Bitmap.withEffects(adjust: Adjustments, marks: List<Mark>): Bitmap {
    if (adjust.isIdentity && marks.isEmpty()) return this
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawBitmap(this, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
        if (!adjust.isIdentity) colorFilter = ColorMatrixColorFilter(adjust.colorMatrix())
    })
    if (marks.isEmpty()) return out
    val pixelated = if (marks.any { it.mosaic }) out.copy(Bitmap.Config.ARGB_8888, false).mosaic() else null
    for (mark in marks) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = (mark.width * width).coerceAtLeast(MIN_STROKE_PX)
            if (mark.mosaic && pixelated != null) shader = BitmapShader(pixelated, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            else color = mark.color
        }
        canvas.drawPath(strokePath(mark.points, width.toFloat(), height.toFloat()), paint)
    }
    pixelated?.recycle()
    return out
}

/** The adjustments' color matrix in Compose's format (the same 4 × 5 layout), for the live preview. */
fun Adjustments.composeMatrix() = androidx.compose.ui.graphics.ColorMatrix(colorMatrix().array)

/** An adjustment as a rounded percentage, shown next to its slider. */
fun Float.asPercent() = (this * PERCENT).roundToInt()
