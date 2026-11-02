package com.mephilia.gallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Most pixels decoded for a full-size render, to stay within memory; bigger photos are scaled down to it. */
private const val MAX_FULL_PIXELS = 50_000_000

/**
 * Pixels trimmed from each edge when straightening, so filtered edge pixels don't bleed in as a thin dark line.
 * Counted on both sides of the shortest dimension.
 */
private const val STRAIGHTEN_MARGIN_PX = 2f

/** MIME type of JPEG, the output of formats Android can't encode. */
const val JPEG_MIME = "image/jpeg"

/** MIME type of PNG. */
private const val PNG_MIME = "image/png"

/** MIME type of WebP. */
private const val WEBP_MIME = "image/webp"

/**
 * How an edited photo is written.
 *
 * @param mime its MIME type.
 * @param extension its file extension, without the dot.
 * @param format the encoder used.
 */
data class OutputFormat(val mime: String, val extension: String, val format: Bitmap.CompressFormat)

/**
 * The format an edited photo of type [mime] is saved in: PNG and WebP keep their format, everything else is
 * saved as JPEG (Android can't encode HEIC, AVIF…).
 */
fun outputFormatFor(mime: String): OutputFormat = when (mime.lowercase()) {
    PNG_MIME -> OutputFormat(PNG_MIME, "png", Bitmap.CompressFormat.PNG)
    WEBP_MIME -> OutputFormat(WEBP_MIME, "webp", Bitmap.CompressFormat.WEBP_LOSSY)
    else -> OutputFormat(JPEG_MIME, "jpg", Bitmap.CompressFormat.JPEG)
}

/**
 * Decodes the photo at [uri] into a software bitmap (which can be read and drawn on).
 *
 * @param maxSide scales it down so its longest side fits; without it, only photos bigger than 50 megapixels
 * are scaled down.
 */
suspend fun decodeBitmap(context: Context, uri: Uri, maxSide: Int? = null): Bitmap = withContext(Dispatchers.IO) {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val pixels = width.toLong() * height
        val scale = when {
            maxSide != null -> minOf(1f, maxSide.toFloat() / maxOf(width, height))
            pixels > MAX_FULL_PIXELS -> sqrt(MAX_FULL_PIXELS.toFloat() / pixels)
            else -> 1f
        }
        if (scale < 1f) decoder.setTargetSize((width * scale).roundToInt(), (height * scale).roundToInt())
    }
}

/** The matrix that turns and mirrors a picture as [edits] say. */
private fun turnMatrix(edits: PhotoEdits) = Matrix().apply {
    postRotate(DEGREES_PER_QUARTER_TURN * edits.quarterTurns.mod(QUARTER_TURNS_PER_TURN))
    if (edits.flipH) postScale(-1f, 1f)
}

/** This bitmap turned and mirrored as [edits] say (nothing else applied), or this bitmap if it's unchanged. */
fun Bitmap.transformed(edits: PhotoEdits): Bitmap =
    if (!edits.isTurnedOrMirrored) this
    else Bitmap.createBitmap(this, 0, 0, width, height, turnMatrix(edits), true)

/** Maps the crop, expressed in the turned and mirrored picture, back to the source picture. */
private fun sourceRect(edits: PhotoEdits): Rect {
    fun toSource(shownX: Float, shownY: Float): Pair<Float, Float> {
        val x = if (edits.flipH) 1f - shownX else shownX
        return when (edits.quarterTurns.mod(QUARTER_TURNS_PER_TURN)) {
            1 -> shownY to 1f - x
            2 -> 1f - x to 1f - shownY
            3 -> 1f - shownY to x
            else -> x to shownY
        }
    }
    val (x1, y1) = toSource(edits.crop.left, edits.crop.top)
    val (x2, y2) = toSource(edits.crop.right, edits.crop.bottom)
    return Rect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
}

/** This bitmap with the geometry of [edits]: turned, mirrored, straightened and cropped. */
fun Bitmap.applyEdits(edits: PhotoEdits): Bitmap {
    if (edits.angle != 0f) return applyEditsWithAngle(edits)
    val rect = sourceRect(edits)
    val x = (rect.left * width).roundToInt().coerceIn(0, width - 1)
    val y = (rect.top * height).roundToInt().coerceIn(0, height - 1)
    val cropWidth = (rect.width * width).roundToInt().coerceIn(1, width - x)
    val cropHeight = (rect.height * height).roundToInt().coerceIn(1, height - y)
    return Bitmap.createBitmap(this, x, y, cropWidth, cropHeight, turnMatrix(edits), true)
}

/** Renders every geometry edit in a single pass: quarter turns, mirror, fine rotation with zoom, then crop. */
private fun Bitmap.applyEditsWithAngle(edits: PhotoEdits): Bitmap {
    val sideways = isSideways(edits.quarterTurns)
    val shownWidth = (if (sideways) height else width).toFloat()
    val shownHeight = (if (sideways) width else height).toFloat()
    val margin = 1f - STRAIGHTEN_MARGIN_PX / min(shownWidth, shownHeight)
    val scale = straightenScale(shownWidth, shownHeight, edits.angle) * margin
    val zoomedWidth = shownWidth * scale
    val zoomedHeight = shownHeight * scale
    val outWidth = (edits.crop.width * zoomedWidth).roundToInt().coerceAtLeast(1)
    val outHeight = (edits.crop.height * zoomedHeight).roundToInt().coerceAtLeast(1)

    val matrix = Matrix()
    matrix.postRotate(DEGREES_PER_QUARTER_TURN * edits.quarterTurns.mod(QUARTER_TURNS_PER_TURN))
    val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat()).also { matrix.mapRect(it) }
    matrix.postTranslate(-bounds.left, -bounds.top)
    if (edits.flipH) {
        matrix.postScale(-1f, 1f)
        matrix.postTranslate(shownWidth, 0f)
    }
    matrix.postRotate(edits.angle, shownWidth / 2, shownHeight / 2)
    matrix.postTranslate(
        -(shownWidth - zoomedWidth) / 2 - edits.crop.left * zoomedWidth,
        -(shownHeight - zoomedHeight) / 2 - edits.crop.top * zoomedHeight,
    )

    val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(this, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

/**
 * Renders [item] at full size with [edits] into [out], in the format given by [outputFormatFor] and the JPEG
 * quality from the settings. The EXIF data (date, camera, and the location if kept) is copied for JPEG output.
 */
suspend fun renderPhoto(context: Context, item: MediaItem, edits: PhotoEdits, out: File): File =
    withContext(Dispatchers.Default) {
        val format = outputFormatFor(item.mimeType)
        val source = decodeBitmap(context, item.uri)
        val geometry = source.applyEdits(edits)
        if (geometry !== source) source.recycle()
        val result = geometry.withEffects(edits.adjust, edits.marks)
        if (result !== geometry) geometry.recycle()
        withContext(Dispatchers.IO) {
            val settings = AppSettings.current
            out.outputStream().use { result.compress(format.format, settings.jpegQuality, it) }
            if (format.mime == JPEG_MIME) copyExif(context, item.uri, out, result.width, result.height, settings.keepLocation)
        }
        result.recycle()
        out
    }
