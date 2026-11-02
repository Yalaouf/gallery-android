package com.mephilia.gallery.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.R
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaSort
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.EmptyState
import com.mephilia.gallery.ui.components.MediaGrid
import com.mephilia.gallery.ui.components.MediaOptionsSheet
import com.mephilia.gallery.ui.components.MediaSelection
import com.mephilia.gallery.ui.components.SelectionActionsBar
import com.mephilia.gallery.ui.components.rememberSelection
import com.mephilia.gallery.ui.theme.Dimens
import kotlinx.coroutines.flow.Flow

/** Grid key of the banner asking for access to all photos. */
private const val PARTIAL_ACCESS_KEY = "partial"

/** Grid key of the "on this day" row. */
private const val ON_THIS_DAY_KEY = "on-this-day"

/**
 * The photos tab: every photo and video, grouped by day when sorted by date, with selection mode.
 *
 * @param onSelectingChange tells whether selection mode is on, so the navigation bar can hide.
 * @param onOpenOnThisDay opens an "on this day" item in the viewer.
 * @param scrollToTop emits when the grid should go back to the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotosTab(
    vm: GalleryViewModel,
    partialAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onSelectingChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOnThisDay: (MediaItem) -> Unit,
    scrollToTop: Flow<Unit>,
) {
    val media by vm.media.collectAsStateWithLifecycle()
    val onThisDay by vm.onThisDay.collectAsStateWithLifecycle()
    val settings by AppSettings.flow.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sort by vm.mediaSort.collectAsStateWithLifecycle()
    val filter by vm.mediaFilter.collectAsStateWithLifecycle()
    val columns by vm.gridColumns.collectAsStateWithLifecycle()
    val selection = rememberSelection(media)
    var showOptions by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyGridState()

    LaunchedEffect(selection.active) { onSelectingChange(selection.active) }
    DisposableEffect(Unit) { onDispose { onSelectingChange(false) } }
    ScrollToTopOn(scrollToTop, gridState, scroll.state)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            PhotosTopBar(
                media = media,
                sort = sort,
                selection = selection,
                scroll = scroll,
                onShowOptions = { showOptions = true },
                onOpenSettings = onOpenSettings,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                media.isEmpty() -> EmptyState(Icons.Rounded.Photo, stringResource(R.string.empty_items))
                else -> MediaGrid(
                    items = media,
                    columns = columns,
                    groupByDate = sort == MediaSort.DateDesc || sort == MediaSort.DateAsc,
                    state = gridState,
                    contentPadding = PaddingValues(bottom = Dimens.floatingBarClearance),
                    onClick = { if (selection.active) selection.toggle(it) else onOpen(it) },
                    selection = selection,
                    header = {
                        if (partialAccess) item(span = { GridItemSpan(maxLineSpan) }, key = PARTIAL_ACCESS_KEY) {
                            PartialAccessBanner(onRequestAccess)
                        }
                        if (settings.showOnThisDay && onThisDay.isNotEmpty() && !selection.active) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = ON_THIS_DAY_KEY) {
                                OnThisDayRow(onThisDay, onOpenOnThisDay)
                            }
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = selection.active,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                SelectionActionsBar(
                    items = selection.items(media),
                    albums = albums,
                    onDone = selection::clear,
                    onSelectAll = { selection.selectAll(media) },
                    modifier = Modifier.navigationBarsPadding().padding(bottom = Dimens.floatingBarBottomMargin),
                )
            }
        }
    }

    if (showOptions) {
        MediaOptionsSheet(
            sort = sort,
            filter = filter,
            columns = columns,
            onSort = vm::setMediaSort,
            onFilter = vm::setMediaFilter,
            onColumns = vm::setGridColumns,
            onDismiss = { showOptions = false },
        )
    }
}

/**
 * Top bar of the photos tab. In selection mode it keeps the same large bar (and so the same height), so the grid
 * doesn't jump under the finger; it then shows the selected count and the selection actions instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotosTopBar(
    media: List<MediaItem>,
    sort: MediaSort,
    selection: MediaSelection,
    scroll: TopAppBarScrollBehavior,
    onShowOptions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val count = selection.ids.size
    LargeTopAppBar(
        navigationIcon = {
            if (selection.active) {
                IconButton(onClick = selection::clear) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel_selection))
                }
            }
        },
        title = {
            TabTitle(
                title = if (count == 0) stringResource(R.string.tab_photos)
                else pluralStringResource(R.plurals.selected_count, count, count),
                subtitle = if (selection.active) stringResource(R.string.selection_hint)
                else stringResource(
                    R.string.subtitle_with_sort,
                    pluralStringResource(R.plurals.items_count, media.size, media.size),
                    stringResource(sort.label),
                ),
                collapsedFraction = scroll.state.collapsedFraction,
            )
        },
        actions = {
            if (selection.active) {
                IconButton(onClick = { selection.selectAll(media) }) {
                    Icon(Icons.Rounded.SelectAll, stringResource(R.string.action_select_all))
                }
            } else {
                IconButton(onClick = { selection.selectAll(media) }, enabled = media.isNotEmpty()) {
                    Icon(Icons.Rounded.Checklist, stringResource(R.string.action_select_all))
                }
                IconButton(onClick = onShowOptions) { Icon(Icons.Rounded.Tune, stringResource(R.string.action_sort_filter)) }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.action_settings)) }
            }
        },
        scrollBehavior = scroll,
    )
}
