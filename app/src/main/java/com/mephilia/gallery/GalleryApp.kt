package com.mephilia.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.request.crossfade
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.ThumbFetcher
import com.mephilia.gallery.data.ThumbKeyer

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
                // Animated GIF and WebP in the viewer and editor
                add(AnimatedImageDecoder.Factory())
            }
            .crossfade(150)
            .build()
}
