package com.mephilia.gallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.exifinterface.media.ExifInterface
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Clockwise quarter turns, then horizontal mirror, then a normalized crop in the displayed image. */
data class PhotoEdits(
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    val crop: Rect = Rect(0f, 0f, 1f, 1f),
)

data class OutputFormat(val mime: String, val extension: String, val format: Bitmap.CompressFormat)

fun outputFormatFor(mime: String): OutputFormat = when (mime.lowercase()) {
    "image/png" -> OutputFormat("image/png", "png", Bitmap.CompressFormat.PNG)
    "image/webp" -> OutputFormat("image/webp", "webp", Bitmap.CompressFormat.WEBP_LOSSY)
    // Formats Android can't encode (HEIC, AVIF…) are converted to JPEG
    else -> OutputFormat("image/jpeg", "jpg", Bitmap.CompressFormat.JPEG)
}

private const val MAX_FULL_PIXELS = 50_000_000

suspend fun decodeBitmap(context: Context, uri: Uri, maxSide: Int? = null): Bitmap = withContext(Dispatchers.IO) {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val w = info.size.width
        val h = info.size.height
        val scale = when {
            maxSide != null -> minOf(1f, maxSide.toFloat() / maxOf(w, h))
            w.toLong() * h > MAX_FULL_PIXELS -> sqrt(MAX_FULL_PIXELS.toFloat() / (w.toLong() * h))
            else -> 1f
        }
        if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
    }
}

private fun editMatrix(edits: PhotoEdits) = Matrix().apply {
    postRotate(90f * (edits.quarterTurns.mod(4)))
    if (edits.flipH) postScale(-1f, 1f)
}

fun Bitmap.transformed(edits: PhotoEdits): Bitmap =
    if (edits.quarterTurns.mod(4) == 0 && !edits.flipH) this
    else Bitmap.createBitmap(this, 0, 0, width, height, editMatrix(edits), true)

/** Maps the crop, expressed in the displayed (rotated and mirrored) image, back to the source image. */
private fun sourceRect(edits: PhotoEdits): Rect {
    fun toSource(u0: Float, v0: Float): Pair<Float, Float> {
        val u = if (edits.flipH) 1f - u0 else u0
        return when (edits.quarterTurns.mod(4)) {
            1 -> v0 to 1f - u
            2 -> 1f - u to 1f - v0
            3 -> 1f - v0 to u
            else -> u to v0
        }
    }
    val (x1, y1) = toSource(edits.crop.left, edits.crop.top)
    val (x2, y2) = toSource(edits.crop.right, edits.crop.bottom)
    return Rect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
}

fun Bitmap.applyEdits(edits: PhotoEdits): Bitmap {
    val r = sourceRect(edits)
    val x = (r.left * width).roundToInt().coerceIn(0, width - 1)
    val y = (r.top * height).roundToInt().coerceIn(0, height - 1)
    val w = (r.width * width).roundToInt().coerceIn(1, width - x)
    val h = (r.height * height).roundToInt().coerceIn(1, height - y)
    return Bitmap.createBitmap(this, x, y, w, h, editMatrix(edits), true)
}

/** Full-resolution render. EXIF data is kept for JPEG output. */
suspend fun renderPhoto(context: Context, item: MediaItem, edits: PhotoEdits, out: File): File =
    withContext(Dispatchers.Default) {
        val format = outputFormatFor(item.mimeType)
        val src = decodeBitmap(context, item.uri)
        val result = src.applyEdits(edits)
        if (result !== src) src.recycle()
        withContext(Dispatchers.IO) {
            val settings = AppSettings.current
            out.outputStream().use { result.compress(format.format, settings.jpegQuality, it) }
            if (format.mime == "image/jpeg") {
                copyExif(context, item.uri, out, result.width, result.height, settings.keepLocation)
            }
        }
        result.recycle()
        out
    }

private val exifTags = listOf(
    ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
    ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH,
    ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
)

private fun copyExif(context: Context, from: Uri, to: File, width: Int, height: Int, keepLocation: Boolean) {
    try {
        val src = context.contentResolver.openInputStream(from)?.use { ExifInterface(it) } ?: return
        val dst = ExifInterface(to)
        for (tag in exifTags) {
            if (!keepLocation && tag.startsWith("GPS")) continue
            src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
        }
        // The rotation is baked into the pixels
        dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        dst.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
        dst.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
        dst.saveAttributes()
    } catch (_: Exception) {
        // EXIF is best effort: don't fail the save over it
    }
}
