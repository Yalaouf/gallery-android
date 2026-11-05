package com.mephilia.gallery.ui.screens.viewer

import android.content.Context
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.ui.components.MediaAccess
import com.mephilia.gallery.ui.components.restoreFromLocked
import kotlinx.coroutines.delay

/** Milliseconds in a second, to turn the slideshow setting into a delay. */
private const val MILLIS_PER_SECOND = 1000L

/** What the viewer is showing, which decides the actions it offers. */
internal enum class ViewerMode {
    /** A regular gallery item: every action. */
    Gallery,

    /** An item in the trash: restore or delete for good. */
    Trash,

    /** An item of the locked folder: unhide, details or delete for good. */
    Locked,
}

/** The dialogs and sheets the viewer can open on the current item; only one is shown at a time. */
internal enum class ViewerDialog {
    /** The details sheet: name, date, camera, location… */
    Info,

    /** Pick a folder to move the item to. */
    Move,

    /** Pick a folder to copy the item to. */
    Copy,

    /** Give the item a new file name. */
    Rename,

    /** Confirm moving the item to the trash. */
    ConfirmTrash,

    /** Change the capture date. */
    EditDate,

    /** Confirm removing the location from the file. */
    RemoveLocation,

    /** Confirm hiding the item in the locked folder. */
    ConfirmLock,

    /** Confirm deleting a locked item for good. */
    ConfirmDeleteLocked,
}

/**
 * The id of the item being shown, which only changes when the user swipes to another page.
 *
 * Page numbers shift when items are added or removed elsewhere (e.g. a screenshot taken while viewing), and the
 * item shown must not change because of that: when [list] changes, the pager moves to wherever the current item
 * now is. If the current item is gone (deleted), the one now in its place becomes the current item.
 */
@Composable
internal fun rememberCurrentItemId(pager: PagerState, list: List<MediaItem>, startId: Long): Long {
    var currentId by remember { mutableLongStateOf(startId) }
    val latestList by rememberUpdatedState(list)
    LaunchedEffect(pager.settledPage) {
        latestList.getOrNull(pager.settledPage)?.let { currentId = it.id }
    }
    LaunchedEffect(list) {
        val index = list.indexOfFirst { it.id == currentId }
        when {
            index < 0 -> list.getOrNull(pager.currentPage.coerceIn(0, list.lastIndex))?.let { currentId = it.id }
            index != pager.currentPage -> pager.scrollToPage(index)
        }
    }
    return currentId
}

/**
 * While [running], moves to the next page every few seconds (as set in the settings) and keeps the screen on.
 * After the last page it starts over from the first one. [onStart] is called when the slideshow begins,
 * e.g. to hide the bars.
 */
@Composable
internal fun SlideshowEffect(running: Boolean, pager: PagerState, onStart: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        onStart()
        while (true) {
            delay(AppSettings.current.slideshowSeconds * MILLIS_PER_SECOND)
            val next = (pager.currentPage + 1) % pager.pageCount
            if (next == 0) pager.scrollToPage(0) else pager.animateScrollToPage(next)
        }
    }
}

/** Moves [item] to the trash, after the user allows it if needed, and removes it from the lists. */
internal suspend fun trashItem(access: MediaAccess, item: MediaItem) {
    if (access.trash(listOf(item.uri))) MediaEvents.removed(listOf(item.id))
}

/** Takes [item] out of the locked folder, back into the gallery. */
internal suspend fun restoreItemFromLocked(context: Context, item: MediaItem) {
    restoreFromLocked(context, listOf(item))
}
