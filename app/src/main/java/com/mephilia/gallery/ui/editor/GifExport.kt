package com.mephilia.gallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.mephilia.gallery.data.GifEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest part of a video that can become a GIF: longer GIFs get too heavy. */
const val GIF_MAX_DURATION_MS = 15_000L

/** Milliseconds in a second. */
private const val MILLIS_PER_SECOND = 1000.0

/** Microseconds in a millisecond. */
private const val MICROS_PER_MILLI = 1000L

/** Microseconds in a second. */
private const val MICROS_PER_SECOND = 1_000_000L

/** Frames are extracted at this many times the needed size, so the final reduction is done by sharp halvings. */
private const val EXTRACTION_OVERSAMPLING = 2f

/** Degrees in a half turn: a rotation by a multiple of it keeps the frame's orientation. */
private const val HALF_TURN_DEGREES = 180

/** Degrees in a full turn. */
private const val FULL_TURN_DEGREES = 360

/** Number of frames of a GIF made from [durationMs] of video at [fps]: at least one. */
fun gifFrameCount(durationMs: Long, fps: Int): Int = max(1, (durationMs * fps / MILLIS_PER_SECOND).roundToInt())

/**
 * Delay of the frame at [index] at [fps], in hundredths of a second. Rounding the cumulative time rather than each
 * delay keeps the overall speed exact (at 30 fps, delays alternate between 3 and 4).
 */
private fun frameDelayCs(index: Int, fps: Int): Int {
    val perFrame = CENTISECONDS_PER_SECOND.toDouble() / fps
    return ((index + 1) * perFrame).roundToInt() - (index * perFrame).roundToInt()
}

/**
 * Creates a GIF of the part of a video between [startMs] and [endMs], with the same [edits] as the video export.
 * Frames are sampled at [fps] and shown for 1/[fps] each, so the GIF plays at the video's speed.
 *
 * @param maxSide the GIF's longest side, in pixels.
 * @param onProgress the share of frames done, from 0 to 1.
 * @throws IllegalStateException if no frame could be read from the video.
 */
suspend fun exportGif(
    context: Context,
    uri: Uri,
    info: VideoInfo,
    startMs: Long,
    endMs: Long,
    edits: PhotoEdits,
    maxSide: Int,
    fps: Int,
    loop: Boolean,
    out: File,
    onProgress: (Float) -> Unit,
): File = withContext(Dispatchers.Default) {
    val count = gifFrameCount(endMs - startMs, fps)
    val request = extractionSize(info, edits, maxSide)

    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, uri)
        out.outputStream().buffered().use { stream ->
            var encoder: GifEncoder? = null
            var width = 0
            var height = 0
            var pixels = IntArray(0)
            for (i in 0 until count) {
                ensureActive()
                val timeUs = startMs * MICROS_PER_MILLI + i * MICROS_PER_SECOND / fps
                val raw = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, request, request) ?: continue
                val oriented = orient(raw, info)
                val edited = oriented.applyEdits(edits)
                val gif = encoder ?: run {
                    val scale = minOf(1f, maxSide.toFloat() / max(edited.width, edited.height))
                    width = (edited.width * scale).roundToInt().coerceAtLeast(1)
                    height = (edited.height * scale).roundToInt().coerceAtLeast(1)
                    pixels = IntArray(width * height)
                    GifEncoder(stream, width, height, loop).also { encoder = it }
                }
                val scaled = edited.scaleDownSmooth(width, height)
                scaled.getPixels(pixels, 0, width, 0, 0, width, height)
                gif.addFrame(pixels, frameDelayCs(i, fps))
                for (bitmap in setOf(raw, oriented, edited, scaled)) bitmap.recycle()
                onProgress((i + 1f) / count)
            }
            encoder?.close() ?: error("No frame could be read from the video")
        }
    }
    out
}

/**
 * The size to extract frames at, so that once cropped they still reach [maxSide] (twice that, see
 * [scaleDownSmooth]). The crop is expressed in the turned picture, where width and height swap on a quarter turn.
 */
private fun extractionSize(info: VideoInfo, edits: PhotoEdits, maxSide: Int): Int {
    val sideways = isSideways(edits.quarterTurns)
    val shownWidth = if (sideways) info.height else info.width
    val shownHeight = if (sideways) info.width else info.height
    val cropLongest = max(edits.crop.width * shownWidth, edits.crop.height * shownHeight).coerceAtLeast(1f)
    val fullLongest = max(info.width, info.height)
    return (fullLongest * EXTRACTION_OVERSAMPLING * maxSide / cropLongest).roundToInt().coerceIn(1, fullLongest)
}

/**
 * High-quality reduction to [targetWidth] × [targetHeight]: halves the size while it's more than twice the
 * target (each halving averages 2×2 pixels), then a final bilinear step. A single bilinear step over a large ratio
 * skips pixels and loses fine details. May return this bitmap when it already has the target size.
 */
private fun Bitmap.scaleDownSmooth(targetWidth: Int, targetHeight: Int): Bitmap {
    var current = this
    while (current.width >= targetWidth * 2 && current.height >= targetHeight * 2) {
        val half = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
        if (current !== this) current.recycle()
        current = half
    }
    if (current.width == targetWidth && current.height == targetHeight) return current
    val result = Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true)
    if (current !== this && current !== result) current.recycle()
    return result
}

/**
 * Turns [frame] upright if the retriever didn't: depending on the device, extracted frames may or may not have
 * the video's rotation applied.
 */
private fun orient(frame: Bitmap, info: VideoInfo): Bitmap {
    val rotation = info.rotation.mod(FULL_TURN_DEGREES)
    if (rotation % HALF_TURN_DEGREES == 0) return frame
    val frameLandscape = frame.width > frame.height
    val displayLandscape = info.width > info.height
    if (frameLandscape == displayLandscape) return frame
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
}
