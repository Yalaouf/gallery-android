package com.mephilia.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.preferEndFirstIntrinsicSize
import coil3.gif.AnimatedImageDecoder
import coil3.request.crossfade
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.ThumbFetcher
import com.mephilia.gallery.data.ThumbKeyer

/** Length of the fade between a placeholder and the loaded picture. */
private const val CROSSFADE_MS = 150

/**
 * The application: loads the settings at startup, and sets up the image loader shared by every screen.
 *
 * The image loader gets the thumbnail loader ([ThumbFetcher]) and animated GIF and WebP support for the viewer
 * and editor. After a fade, it sizes the image like the new picture rather than the larger of the old and the
 * new: otherwise a cropped (smaller) photo would be drawn stretched until it's reopened.
 */
class GalleryApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbKeyer())
                add(ThumbFetcher.Factory())
                add(AnimatedImageDecoder.Factory())
            }
            .crossfade(CROSSFADE_MS)
            .preferEndFirstIntrinsicSize(true)
            .build()
}
