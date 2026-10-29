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
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaSort
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.MediaGrid
import com.mephilia.gallery.ui.components.MediaOptionsSheet
import com.mephilia.gallery.ui.components.SelectionActionsBar
import com.mephilia.gallery.ui.components.SelectionTopBar
import com.mephilia.gallery.ui.components.rememberSelection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.mephilia.gallery.R

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
    SystemBars(lightIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f)

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
                    IconButton(onClick = { showOptions = true }) { Icon(Icons.Rounded.Tune, stringResource(R.string.action_sort_filter)) }
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
                    groupByDate = sort == MediaSort.DateDesc || sort == MediaSort.DateAsc,
                    state = gridState,
                    contentPadding = PaddingValues(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp,
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
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp),
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
