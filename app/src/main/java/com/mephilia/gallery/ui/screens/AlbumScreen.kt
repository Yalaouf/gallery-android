package com.mephilia.gallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.R
import com.mephilia.gallery.data.FAVORITES_ID
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaSort
import com.mephilia.gallery.data.ON_THIS_DAY_ID
import com.mephilia.gallery.data.isPseudoAlbum
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.AlbumAction
import com.mephilia.gallery.ui.components.AlbumActionDialog
import com.mephilia.gallery.ui.components.AlbumMenuEntries
import com.mephilia.gallery.ui.components.EmptyState
import com.mephilia.gallery.ui.components.MediaGrid
import com.mephilia.gallery.ui.components.MediaOptionsSheet
import com.mephilia.gallery.ui.components.SelectionActionsBar
import com.mephilia.gallery.ui.components.SelectionTopBar
import com.mephilia.gallery.ui.components.ThemedSystemBars
import com.mephilia.gallery.ui.components.canRenameFolder
import com.mephilia.gallery.ui.components.rememberSelection
import com.mephilia.gallery.ui.theme.Dimens

/**
 * A folder, or a pseudo album (favorites, on this day, all photos, all videos), with selection mode.
 *
 * Folders follow the chosen sort and filter, which can be changed here. Favorites and on this day keep their own
 * order (newest first), so they offer no sort options and are always grouped by day. A real folder's menu offers
 * to pin it, hide it from the Photos tab, rename it or delete it (renaming and deleting act on all its items
 * whatever the filter shows, and close the screen once done).
 *
 * @param bucketId the folder's id, or a pseudo album's (such as [FAVORITES_ID]).
 * @param name the title shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    vm: GalleryViewModel,
    bucketId: Long,
    name: String,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
) {
    val itemsFlow = remember(bucketId) { vm.albumMedia(bucketId) }
    val items by itemsFlow.collectAsStateWithLifecycle(emptyList())
    val albums by vm.albums.collectAsStateWithLifecycle()
    val selection = rememberSelection(items)
    val sort by vm.mediaSort.collectAsStateWithLifecycle()
    val filter by vm.mediaFilter.collectAsStateWithLifecycle()
    val columns by vm.gridColumns.collectAsStateWithLifecycle()
    var showOptions by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val gridState = rememberLazyGridState()
    val ownOrder = bucketId == FAVORITES_ID || bucketId == ON_THIS_DAY_ID
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val folderItems = remember(allItems, bucketId) {
        if (isPseudoAlbum(bucketId)) emptyList() else allItems.filter { it.bucketId == bucketId }
    }
    val relativePath = folderItems.firstOrNull()?.relativePath
    val pinned by vm.pinnedAlbums.collectAsStateWithLifecycle()
    val excluded by vm.excludedAlbums.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var albumAction by remember { mutableStateOf<AlbumAction?>(null) }
    ThemedSystemBars()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection.ids.size, onClose = selection::clear, onSelectAll = { selection.selectAll(items) })
            } else TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
                },
                title = {
                    Column {
                        Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            pluralStringResource(R.plurals.items_count, items.size, items.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { selection.selectAll(items) }, enabled = items.isNotEmpty()) {
                        Icon(Icons.Rounded.Checklist, stringResource(R.string.action_select_all))
                    }
                    if (!ownOrder) {
                        IconButton(onClick = { showOptions = true }) { Icon(Icons.Rounded.Tune, stringResource(R.string.action_sort_filter)) }
                    }
                    if (relativePath != null) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            AlbumMenuEntries(canRenameFolder(relativePath), relativePath in pinned, relativePath in excluded) { action ->
                                menu = false
                                when (action) {
                                    AlbumAction.TogglePin -> vm.togglePinned(relativePath)
                                    AlbumAction.ToggleExcluded -> vm.toggleExcluded(relativePath)
                                    AlbumAction.Rename, AlbumAction.Delete -> albumAction = action
                                }
                            }
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (items.isEmpty()) {
                EmptyState(Icons.Rounded.Photo, stringResource(R.string.empty_items))
            } else {
                MediaGrid(
                    items = items,
                    columns = columns,
                    groupByDate = ownOrder || sort == MediaSort.DateDesc || sort == MediaSort.DateAsc,
                    state = gridState,
                    contentPadding = PaddingValues(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Dimens.actionsBarClearance,
                    ),
                    onClick = { if (selection.active) selection.toggle(it) else onOpenMedia(it) },
                    selection = selection,
                )
            }
            AnimatedVisibility(
                visible = selection.active,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                SelectionActionsBar(
                    items = selection.items(items),
                    albums = albums,
                    onDone = selection::clear,
                    onSelectAll = { selection.selectAll(items) },
                    modifier = Modifier.navigationBarsPadding().padding(bottom = Dimens.floatingBarBottomMargin),
                )
            }
        }
    }

    val action = albumAction
    if (action != null && relativePath != null) {
        AlbumActionDialog(
            action = action,
            name = name,
            relativePath = relativePath,
            items = folderItems,
            albums = albums,
            onDismiss = { albumAction = null },
            onRenamed = { newPath -> vm.albumRenamed(relativePath, newPath) },
            onDone = {
                if (action == AlbumAction.Delete) vm.albumDeleted(relativePath)
                albumAction = null
                onBack()
            },
        )
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
