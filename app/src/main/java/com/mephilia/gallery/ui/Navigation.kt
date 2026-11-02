package com.mephilia.gallery.ui

import androidx.compose.runtime.mutableStateListOf
import com.mephilia.gallery.data.MediaItem

/** MIME type of GIFs, which open in the GIF editor. */
private const val GIF_MIME = "image/gif"

/** The app's screens. Each one has a [key] that identifies it in the back stack and its saved state. */
sealed interface Route {
    /** Identifies the screen, unique in the back stack. */
    val key: String

    /** The home screen, with the photos, albums and collections tabs. */
    data object Home : Route {
        override val key = "home"
    }

    /** A folder, or a pseudo album (favorites, on this day…) by its id. */
    data class Album(val id: Long, val name: String) : Route {
        override val key = "album/$id"
    }

    /** The viewer, opened on [startId], over the items of [bucketId] (or of the photos tab when null). */
    data class Viewer(val bucketId: Long?, val startId: Long) : Route {
        override val key = "viewer/$bucketId/$startId"
    }

    /** The photo editor. */
    data class EditPhoto(val item: MediaItem) : Route {
        override val key = "edit-photo/${item.id}"
    }

    /** The video editor. */
    data class EditVideo(val item: MediaItem) : Route {
        override val key = "edit-video/${item.id}"
    }

    /** The GIF editor. */
    data class EditGif(val item: MediaItem) : Route {
        override val key = "edit-gif/${item.id}"
    }

    /** The trash. */
    data object Trash : Route {
        override val key = "trash"
    }

    /** The viewer over the trash, opened on [startId]. */
    data class TrashViewer(val startId: Long) : Route {
        override val key = "trash-viewer/$startId"
    }

    /** The locked folder. */
    data object Locked : Route {
        override val key = "locked"
    }

    /** Picks the items to put in a new album named [name]. */
    data class NewAlbum(val name: String) : Route {
        override val key = "new-album/$name"
    }
}

/** The back stack of screens. The home screen is always at the bottom and can't be popped. */
class Navigator {
    /** The screens, from the bottom (home) to the one shown. */
    val stack = mutableStateListOf<Route>(Route.Home)

    /** The screen shown. */
    val current get() = stack.last()

    /** Opens [route] on top of the current screen. */
    fun push(route: Route) = stack.add(route)

    /** Closes the current screen and returns it, or returns null when only the home screen is left. */
    fun pop(): Route? = if (stack.size > 1) stack.removeAt(stack.lastIndex) else null
}

/** The editor for [item]: video, GIF or photo. */
fun editRouteFor(item: MediaItem): Route = when {
    item.isVideo -> Route.EditVideo(item)
    item.mimeType.equals(GIF_MIME, ignoreCase = true) -> Route.EditGif(item)
    else -> Route.EditPhoto(item)
}
