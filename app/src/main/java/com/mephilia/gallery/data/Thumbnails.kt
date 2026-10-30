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

/** System thumbnail (fast, cached by Android) for photos and videos. */
data class Thumb(val item: MediaItem)

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options) = "thumb:${data.item.uri}@${data.item.dateModified}"
}

class ThumbFetcher(private val data: Thumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = options.size.width.pxOrElse { 512 }.coerceIn(64, 1024)
        val h = options.size.height.pxOrElse { 512 }.coerceIn(64, 1024)
        val bitmap = options.context.contentResolver.loadThumbnail(data.item.uri, Size(w, h), null)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, options)
    }
}
