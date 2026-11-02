package com.mephilia.gallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.data.StorageFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Aspect ratio assumed when the video's size is unknown. */
private const val DEFAULT_RATIO = 16f / 9f

/** Degrees in a half turn: rotations by a multiple of it keep the width and height. */
private const val HALF_TURN_DEGREES = 180

/** Microseconds in a millisecond. */
private const val MICROS_PER_MILLI = 1000L

/** Milliseconds in a second. */
private const val MILLIS_PER_SECOND = 1000L

/** Trim bar frames are extracted at most this many times wider than tall. */
private const val MAX_FRAME_ASPECT = 2

/** File extension of a saved frame. */
private const val FRAME_EXTENSION = "jpg"

/**
 * What [readVideoInfo] tells about a video.
 *
 * @param width displayed width, with the rotation applied.
 * @param height displayed height, with the rotation applied.
 * @param rotation the rotation from the metadata, in degrees.
 */
data class VideoInfo(val width: Int, val height: Int, val durationMs: Long, val rotation: Int = 0) {
    /** Aspect ratio, width / height (16:9 if unknown). */
    val ratio get() = if (height > 0) width.toFloat() / height else DEFAULT_RATIO
}

/** Reads the displayed size, duration and rotation of the video at [uri]. */
suspend fun readVideoInfo(context: Context, uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, uri)
        fun metadata(key: Int) = retriever.extractMetadata(key)
        val width = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val duration = metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        if (rotation % HALF_TURN_DEGREES != 0) VideoInfo(height, width, duration, rotation)
        else VideoInfo(width, height, duration, rotation)
    }
}

/**
 * [count] frames evenly spread over the video (each in the middle of its slice), [height] pixels tall,
 * for the trim bar. Frames that can't be read are left out.
 */
suspend fun extractFrames(context: Context, uri: Uri, durationMs: Long, count: Int, height: Int): List<Bitmap> =
    withContext(Dispatchers.IO) {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            (0 until count).mapNotNull { i ->
                val timeUs = durationMs * MICROS_PER_MILLI * (2 * i + 1) / (2 * count)
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, height * MAX_FRAME_ASPECT, height)
            }
        }
    }

/**
 * Saves the frame at [positionMs] as a JPEG photo next to the video, with the same turn, mirror and crop as
 * the video edit. Videos are often in Movies/, which doesn't accept photos: the photo then goes to MediaStore's
 * default folder for pictures (Pictures/).
 */
suspend fun saveVideoFrame(context: Context, item: MediaItem, positionMs: Long, edits: PhotoEdits) {
    val frame = withContext(Dispatchers.IO) {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, item.uri)
            retriever.getFrameAtTime(positionMs * MICROS_PER_MILLI, MediaMetadataRetriever.OPTION_CLOSEST)
        }
    } ?: error("No frame")
    val result = withContext(Dispatchers.Default) { frame.applyEdits(edits) }
    val writer = MediaWriter(context)
    val file = writer.tempFile(FRAME_EXTENSION)
    try {
        withContext(Dispatchers.IO) {
            file.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, AppSettings.current.jpegQuality, it) }
        }
        val name = item.name.substringBeforeLast('.', item.name) + "_frame_${positionMs / MILLIS_PER_SECOND}s.$FRAME_EXTENSION"
        val folder = item.relativePath.takeUnless { it.startsWith(StorageFolders.MOVIES) }.orEmpty()
        writer.insertCopy(item.copy(relativePath = folder), file, name, JPEG_MIME)
        MediaEvents.changed()
    } finally {
        file.delete()
        if (result !== frame) result.recycle()
        frame.recycle()
    }
}
