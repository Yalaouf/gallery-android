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

data class GifInfo(val width: Int, val height: Int, val frames: Int, val durationCs: Int, val loops: Boolean) {
    val ratio get() = width.toFloat() / height
}

suspend fun readGifInfo(context: Context, uri: Uri): GifInfo = withContext(Dispatchers.IO) {
    val decoder = GifDecoder(context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
    var frames = 0
    var duration = 0
    decoder.forEachFrame { frames++; duration += it.delayCs }
    GifInfo(decoder.width, decoder.height, frames, duration, decoder.loopCount != null)
}

/**
 * Re-encodes a GIF with [edits], [speed] and [loop].
 * Speeding up may drop frames: players slow down frames shorter than 2 cs.
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
    val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
    val decoder = GifDecoder(bytes)
    val total = decoder.frameCount().coerceAtLeast(1)
    val w = decoder.width
    val h = decoder.height

    out.outputStream().buffered().use { os ->
        var encoder: GifEncoder? = null
        var pixels = IntArray(0)
        // A kept frame's delay is only known once the next kept frame starts
        var pending: IntArray? = null
        var pendingStart = 0
        var sourceTimeCs = 0
        var index = 0
        decoder.forEachFrame { frame ->
            ensureActive()
            val start = (sourceTimeCs / speed).roundToInt()
            sourceTimeCs += frame.delayCs
            index++
            if (pending != null && start - pendingStart < 2) {
                onProgress(index.toFloat() / total)
                return@forEachFrame
            }
            val src = Bitmap.createBitmap(frame.argb, w, h, Bitmap.Config.ARGB_8888)
            val edited = src.applyEdits(edits)
            if (encoder == null) {
                encoder = GifEncoder(os, edited.width, edited.height, loop)
                pixels = IntArray(edited.width * edited.height)
            }
            pending?.let { encoder!!.addFrame(it, start - pendingStart) }
            edited.getPixels(pixels, 0, edited.width, 0, 0, edited.width, edited.height)
            pending = pixels.copyOf()
            pendingStart = start
            if (edited !== src) edited.recycle()
            src.recycle()
            onProgress(index.toFloat() / total)
        }
        val end = (sourceTimeCs / speed).roundToInt()
        val enc = encoder ?: error("Empty GIF")
        enc.addFrame(pending!!, maxOf(2, end - pendingStart))
        enc.close()
    }
    out
}
