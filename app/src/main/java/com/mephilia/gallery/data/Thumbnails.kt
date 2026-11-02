package com.mephilia.gallery.data

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/** Thumbnail size when the requested size isn't fixed. */
private const val DEFAULT_THUMB_PX = 512

/** Smallest thumbnail asked for, so tiny requests still look sharp. */
private const val MIN_THUMB_PX = 64

/** Largest thumbnail asked for; bigger views load the full picture. */
private const val MAX_THUMB_PX = 1024

/** Frame time meaning "any representative frame" for MediaMetadataRetriever. */
private const val ANY_FRAME = -1L

/** A request for the thumbnail of [item], loaded by [ThumbFetcher]. */
data class Thumb(val item: MediaItem)

/** Coil cache key of a thumbnail: it changes when the file changes, so an edited file gets a new thumbnail. */
class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options) = "thumb:${data.item.uri}@${data.item.dateModified}"
}

/**
 * Loads thumbnails for Coil: Android's own thumbnail (fast, and cached by the system) when there is one,
 * otherwise a small version decoded by the app. Locked items are private files, which have no system thumbnail;
 * trashed items have none either.
 */
class ThumbFetcher(private val data: Thumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { DEFAULT_THUMB_PX }.coerceIn(MIN_THUMB_PX, MAX_THUMB_PX)
        val height = options.size.height.pxOrElse { DEFAULT_THUMB_PX }.coerceIn(MIN_THUMB_PX, MAX_THUMB_PX)
        val bitmap = try {
            if (data.item.locked) decodeSmall(width, height) ?: error("Can't decode ${data.item.name}")
            else options.context.contentResolver.loadThumbnail(data.item.uri, Size(width, height), null)
        } catch (e: Exception) {
            decodeSmall(width, height) ?: throw e
        }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    /** A version of the item at least about [width] × [height]: a video frame, or a subsampled photo. */
    private fun decodeSmall(width: Int, height: Int): Bitmap? = runCatching {
        val context = options.context
        if (data.item.isVideo) {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, data.item.uri)
                retriever.getScaledFrameAtTime(ANY_FRAME, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height)
            }
        } else {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, data.item.uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(maxOf(1, minOf(info.size.width / width, info.size.height / height)))
            }
        }
    }.getOrNull()

    /** Creates a [ThumbFetcher] for each [Thumb] request. */
    class Factory : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher = ThumbFetcher(data, options)
    }
}
