package com.mephilia.gallery.data

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
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever

/** System thumbnail (fast, cached by Android) for photos and videos. */
data class Thumb(val item: MediaItem)

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options) = "thumb:${data.item.uri}@${data.item.dateModified}"
}

class ThumbFetcher(private val data: Thumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = options.size.width.pxOrElse { 512 }.coerceIn(64, 1024)
        val h = options.size.height.pxOrElse { 512 }.coerceIn(64, 1024)
        val bitmap = try {
            // Locked items are private files, which have no system thumbnail
            if (data.item.locked) decodeSmall(w, h) ?: error("Can't decode ${data.item.name}")
            else options.context.contentResolver.loadThumbnail(data.item.uri, Size(w, h), null)
        } catch (e: Exception) {
            // No system thumbnail (e.g. trashed items): decode a small version ourselves
            decodeSmall(w, h) ?: throw e
        }
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    private fun decodeSmall(w: Int, h: Int): Bitmap? = runCatching {
        val context = options.context
        if (data.item.isVideo) {
            MediaMetadataRetriever().use { r ->
                r.setDataSource(context, data.item.uri)
                r.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h)
            }
        } else {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, data.item.uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(maxOf(1, minOf(info.size.width / w, info.size.height / h)))
            }
        }
    }.getOrNull()

    class Factory : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, options)
    }
}
