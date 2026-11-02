package com.mephilia.gallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mephilia.gallery.data.GifDecoder
import com.mephilia.gallery.data.GifEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** MIME type of GIFs. */
const val GIF_MIME = "image/gif"

/** File extension of GIFs. */
const val GIF_EXTENSION = "gif"

/** GIF frame delays are stored in hundredths of a second (centiseconds). */
const val CENTISECONDS_PER_SECOND = 100

/** Shortest frame delay players respect, in hundredths of a second; shorter frames are slowed down. */
const val MIN_PLAYABLE_DELAY_CS = 2

/**
 * What [readGifInfo] tells about a GIF.
 *
 * @param durationCs total duration, in hundredths of a second.
 * @param loops whether the animation loops (it has a looping extension).
 */
data class GifInfo(val width: Int, val height: Int, val frames: Int, val durationCs: Int, val loops: Boolean) {
    /** Aspect ratio, width / height. */
    val ratio get() = width.toFloat() / height
}

/** Reads the size, frame count, duration and looping of the GIF at [uri]. */
suspend fun readGifInfo(context: Context, uri: Uri): GifInfo = withContext(Dispatchers.IO) {
    val decoder = GifDecoder(readBytes(context, uri))
    var frames = 0
    var duration = 0
    decoder.forEachFrame {
        frames++
        duration += it.delayCs
    }
    GifInfo(decoder.width, decoder.height, frames, duration, decoder.loopCount != null)
}

/**
 * Re-encodes the GIF at [uri] with [edits], played at [speed], looping or not, into [out].
 *
 * Speeding up may drop frames: a frame that would show for less than [MIN_PLAYABLE_DELAY_CS] is skipped, since
 * players would slow it down. A kept frame's delay is only known once the next kept frame starts, so each frame
 * is written one step late.
 *
 * @param onProgress the share of frames done, from 0 to 1.
 */
suspend fun renderGif(
    context: Context,
    uri: Uri,
    edits: PhotoEdits,
    speed: Float,
    loop: Boolean,
    out: File,
    onProgress: (Float) -> Unit,
): File = withContext(Dispatchers.Default) {
    val decoder = GifDecoder(withContext(Dispatchers.IO) { readBytes(context, uri) })
    val total = decoder.frameCount().coerceAtLeast(1)

    out.outputStream().buffered().use { stream ->
        var encoder: GifEncoder? = null
        var pixels = IntArray(0)
        var pending: IntArray? = null
        var pendingStartCs = 0
        var sourceTimeCs = 0
        var index = 0
        decoder.forEachFrame { frame ->
            ensureActive()
            val startCs = (sourceTimeCs / speed).roundToInt()
            sourceTimeCs += frame.delayCs
            index++
            val tooShort = pending != null && startCs - pendingStartCs < MIN_PLAYABLE_DELAY_CS
            if (!tooShort) {
                val source = Bitmap.createBitmap(frame.argb, decoder.width, decoder.height, Bitmap.Config.ARGB_8888)
                val edited = source.applyEdits(edits)
                val gif = encoder ?: GifEncoder(stream, edited.width, edited.height, loop).also {
                    encoder = it
                    pixels = IntArray(edited.width * edited.height)
                }
                pending?.let { gif.addFrame(it, startCs - pendingStartCs) }
                edited.getPixels(pixels, 0, edited.width, 0, 0, edited.width, edited.height)
                pending = pixels.copyOf()
                pendingStartCs = startCs
                if (edited !== source) edited.recycle()
                source.recycle()
            }
            onProgress(index.toFloat() / total)
        }
        val endCs = (sourceTimeCs / speed).roundToInt()
        val gif = encoder ?: error("Empty GIF")
        pending?.let { gif.addFrame(it, maxOf(MIN_PLAYABLE_DELAY_CS, endCs - pendingStartCs)) }
        gif.close()
    }
    out
}

/** The whole content at [uri]. */
private fun readBytes(context: Context, uri: Uri): ByteArray =
    checkNotNull(context.contentResolver.openInputStream(uri)) { "Can't open $uri" }.use { it.readBytes() }
