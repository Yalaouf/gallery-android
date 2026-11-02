package com.mephilia.gallery.ui.screens.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.SystemBars
import com.mephilia.gallery.ui.components.isInMultiWindow
import com.mephilia.gallery.ui.components.rememberMediaAccess
import com.mephilia.gallery.ui.components.shareMedia
import com.mephilia.gallery.ui.components.toggleFavorite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Pages kept ready on each side of the current one, so swiping shows the next item right away. */
private const val PAGES_PRELOADED = 1

/**
 * The full-screen viewer: swipe between items, pinch to zoom photos, play videos, and act on the item shown.
 *
 * The items come from [items] if given, otherwise from the album [bucketId] (a folder or a pseudo album),
 * otherwise from the photos tab. The viewer closes by itself when there is nothing left to show.
 *
 * @param startId id of the item to open on.
 * @param onEdit opens the editor on an item.
 * @param items items to show instead of the gallery's, e.g. a file opened from another app.
 * @param trashMode shows trashed items: only restoring or deleting for good is offered.
 */
@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    bucketId: Long?,
    startId: Long,
    onBack: () -> Unit,
    onEdit: (MediaItem) -> Unit,
    items: List<MediaItem>? = null,
    trashMode: Boolean = false,
) {
    val source: Flow<List<MediaItem>> = remember(bucketId, items) {
        items?.let { flowOf(it) } ?: bucketId?.let { vm.albumMedia(it) } ?: vm.media
    }
    val shown by source.collectAsStateWithLifecycle(initialValue = null)
    val list = shown ?: return Box(Modifier.fillMaxSize().background(Color.Black))
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    ViewerContent(vm, list, startId, onBack, onEdit, trashMode)
}

/**
 * The viewer once its [list] is known and not empty: the pager, the bars above it and the dialogs.
 * Tapping the item shows or hides the bars; swiping it up or down closes the viewer.
 */
@Composable
private fun ViewerContent(
    vm: GalleryViewModel,
    list: List<MediaItem>,
    startId: Long,
    onBack: () -> Unit,
    onEdit: (MediaItem) -> Unit,
    trashMode: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val access = rememberMediaAccess()
    val albums by vm.albums.collectAsStateWithLifecycle()

    val pager = rememberPagerState(initialPage = list.indexOfFirst { it.id == startId }.coerceAtLeast(0)) { list.size }
    val currentId = rememberCurrentItemId(pager, list, startId)
    val current = list.getOrNull(pager.currentPage.coerceIn(0, list.lastIndex)) ?: list.first()
    val mode = when {
        trashMode -> ViewerMode.Trash
        current.locked -> ViewerMode.Locked
        else -> ViewerMode.Gallery
    }

    var showBars by remember { mutableStateOf(true) }
    var barsBeforeDrag by remember { mutableStateOf(true) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    var dialog by remember { mutableStateOf<ViewerDialog?>(null) }
    var slideshow by remember { mutableStateOf(false) }
    val multiWindow = isInMultiWindow()
    var muted by remember { mutableStateOf(multiWindow || AppSettings.current.alwaysMuted) }
    LaunchedEffect(multiWindow) { if (multiWindow) muted = true }

    SlideshowEffect(running = slideshow, pager = pager, onStart = { showBars = false })
    SystemBars(lightIcons = false, hidden = !showBars)

    val onPageTap = {
        if (slideshow) {
            slideshow = false
            showBars = true
        } else {
            showBars = !showBars
        }
    }
    val dismiss = rememberSwipeToDismissState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 1f - dismiss.fade))
            .swipeToDismiss(
                state = dismiss,
                onDragStart = {
                    barsBeforeDrag = showBars
                    showBars = false
                },
                onCancel = { showBars = barsBeforeDrag },
                onDismiss = onBack,
            ),
    ) {
        HorizontalPager(
            state = pager,
            key = { list.getOrNull(it)?.id ?: it },
            beyondViewportPageCount = PAGES_PRELOADED,
            userScrollEnabled = !dismiss.isDragged,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationY = dismiss.offset
                scaleX = dismiss.scale
                scaleY = dismiss.scale
            },
        ) { page ->
            val item = list[page]
            if (item.isVideo) {
                VideoPage(
                    item = item,
                    active = item.id == currentId,
                    muted = muted,
                    showControls = showBars,
                    controlsBottomPadding = bottomBarHeight,
                    onTap = onPageTap,
                    onAutoHide = { showBars = false },
                )
            } else {
                PhotoPage(item = item, onTap = onPageTap)
            }
        }

        AnimatedVisibility(showBars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            ViewerTopBar(
                item = current,
                mode = mode,
                muted = muted,
                canSlideshow = list.size > 1,
                onBack = onBack,
                onToggleMute = { muted = !muted },
                onToggleFavorite = { scope.launch { toggleFavorite(access, listOf(current)) } },
                onStartSlideshow = { slideshow = true },
                onSetAs = { setAs(context, current) },
                onShowDialog = { dialog = it },
            )
        }

        AnimatedVisibility(showBars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            ViewerBottomBar(
                item = current,
                mode = mode,
                access = access,
                onHeightChange = { bottomBarHeight = it },
                onShare = { shareMedia(context, listOf(current)) },
                onEdit = { onEdit(current) },
                onRestoreFromLocked = { scope.launch { restoreItemFromLocked(context, current) } },
                onDelete = {
                    if (AppSettings.current.confirmDelete) dialog = ViewerDialog.ConfirmTrash
                    else scope.launch { trashItem(access, current) }
                },
                onShowDialog = { dialog = it },
            )
        }
    }

    dialog?.let {
        ViewerDialogHost(
            dialog = it,
            item = current,
            access = access,
            albums = albums,
            scope = scope,
            onDismiss = { dialog = null },
        )
    }
}
