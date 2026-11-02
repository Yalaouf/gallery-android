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

/** Light and color adjustments, each from -1 to 1 (0 = unchanged). */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    val isIdentity get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f

    fun colorMatrix(): ColorMatrix {
        val m = ColorMatrix()
        m.setSaturation(1f + saturation)
        val c = 1f + contrast * 0.6f
        val t = 128f * (1f - c) + brightness * 70f
        m.postConcat(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )))
        val w = warmth * 0.12f
        m.postConcat(ColorMatrix(floatArrayOf(
            1f + w, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - w, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
        return m
    }
}

/**
 * A stroke drawn on the photo, in coordinates normalized to the edited (cropped) image:
 * x and y from 0 to 1, [width] relative to the image width. [mosaic] strokes hide what's under them.
 */
data class Mark(val points: List<Offset>, val width: Float, val color: Int, val mosaic: Boolean)

/** Size of the mosaic blocks, relative to the longest side. */
private const val MOSAIC_BLOCKS = 40

/** Pixelated copy of [src]: averaged blocks, so the hidden details can't be recovered. */
fun Bitmap.mosaic(): Bitmap {
    val block = max(4, max(width, height) / MOSAIC_BLOCKS)
    val small = Bitmap.createScaledBitmap(this, max(1, width / block), max(1, height / block), true)
    val big = Bitmap.createScaledBitmap(small, width, height, false)
    if (small !== big) small.recycle()
    return big
}

fun strokePath(points: List<Offset>, w: Float, h: Float): Path {
    val path = Path()
    points.forEachIndexed { i, p ->
        if (i == 0) path.moveTo(p.x * w, p.y * h) else path.lineTo(p.x * w, p.y * h)
    }
    // A single tap still leaves a dot
    if (points.size == 1) path.lineTo(points[0].x * w + 0.1f, points[0].y * h)
    return path
}

/** Applies the adjustments, then the marks. Returns a new bitmap (the input is left untouched). */
fun Bitmap.withEffects(adjust: Adjustments, marks: List<Mark>): Bitmap {
    if (adjust.isIdentity && marks.isEmpty()) return this
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawBitmap(this, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
        if (!adjust.isIdentity) colorFilter = ColorMatrixColorFilter(adjust.colorMatrix())
    })
    if (marks.isEmpty()) return out
    val w = width.toFloat()
    val h = height.toFloat()
    val pixelated = if (marks.any { it.mosaic }) out.copy(Bitmap.Config.ARGB_8888, false).mosaic() else null
    for (mark in marks) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = (mark.width * w).coerceAtLeast(1f)
            if (mark.mosaic && pixelated != null) shader = BitmapShader(pixelated, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            else color = mark.color
        }
        canvas.drawPath(strokePath(mark.points, w, h), paint)
    }
    pixelated?.recycle()
    return out
}

/** Scales the color matrix to Compose's format (same 4×5 layout). */
fun Adjustments.composeMatrix() = androidx.compose.ui.graphics.ColorMatrix(colorMatrix().array)

/** Rounded integer percent shown next to an adjustment slider. */
fun Float.asPercent() = (this * 100).roundToInt()
