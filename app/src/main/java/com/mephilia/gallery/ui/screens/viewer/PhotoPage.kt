package com.mephilia.gallery.ui.screens.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.Thumb
import com.mephilia.gallery.ui.components.Zoomable

/** Photos are decoded at this many times the screen size, to keep detail when zooming in. */
private const val ZOOM_DETAIL_FACTOR = 2

/** Largest size a photo is decoded at, in pixels, to stay within memory and GPU texture limits. */
private const val MAX_DECODED_SIZE_PX = 4096

/**
 * A photo in the viewer, zoomable, with [onTap] called on a single tap.
 *
 * The thumbnail shows until the full picture is decoded. After the file is replaced (edited), the previous
 * version stays on screen until the new one is ready, so there is no black frame. That key is saved state,
 * because the viewer is rebuilt when coming back from the editor.
 */
@Composable
internal fun PhotoPage(item: MediaItem, onTap: () -> Unit) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val maxPx = with(density) {
        (maxOf(config.screenWidthDp, config.screenHeightDp).dp.toPx() * ZOOM_DETAIL_FACTOR).toInt()
    }.coerceAtMost(MAX_DECODED_SIZE_PX)
    var loaded by remember(item.id) { mutableStateOf(false) }
    var shownKey by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    val key = "full:${item.uri}@${item.dateModified}"

    Zoomable(Modifier.fillMaxSize(), onTap = onTap) {
        if (!loaded) {
            AsyncImage(
                model = Thumb(item),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AsyncImage(
            model = remember(key, maxPx) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .memoryCacheKey(key)
                    .placeholderMemoryCacheKey(shownKey)
                    .size(maxPx)
                    .build()
            },
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            onState = {
                if (it is AsyncImagePainter.State.Success) {
                    loaded = true
                    shownKey = key
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
