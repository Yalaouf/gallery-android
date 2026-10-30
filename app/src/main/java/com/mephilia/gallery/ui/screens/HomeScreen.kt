package com.mephilia.gallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaSort
import com.mephilia.gallery.data.Thumb
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.AlbumOptionsSheet
import com.mephilia.gallery.ui.components.MediaGrid
import com.mephilia.gallery.ui.components.MediaOptionsSheet
import com.mephilia.gallery.ui.components.SelectionActionsBar
import com.mephilia.gallery.ui.components.SettingsSheet
import com.mephilia.gallery.ui.components.rememberSelection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.mephilia.gallery.R
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.mephilia.gallery.ui.components.normalizeForSearch
import com.mephilia.gallery.ui.components.snappyItemModifier
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.ui.draw.alpha
import com.mephilia.gallery.data.AppSettings

@Composable
fun HomeScreen(
    vm: GalleryViewModel,
    tab: Int,
    onTabChange: (Int) -> Unit,
    partialAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenTrash: () -> Unit,
) {
    SystemBars(lightIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f)
    // Keeps each tab's state (scroll position…) when switching tabs
    val tabStates = rememberSaveableStateHolder()
    var selecting by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
    Box(Modifier.fillMaxSize()) {
        tabStates.SaveableStateProvider(tab) {
            when (tab) {
                0 -> PhotosTab(
                    vm, partialAccess, onRequestAccess, onOpenMedia,
                    onSelectingChange = { selecting = it },
                    onOpenSettings = { showSettings = true },
                )
                else -> AlbumsTab(vm, onOpenAlbum, onOpenTrash, onOpenSettings = { showSettings = true })
            }
        }
        AnimatedVisibility(
            visible = !selecting,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FloatingNavBar(
                selected = tab,
                onSelect = onTabChange,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun bottomPadding() = PaddingValues(bottom = 104.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotosTab(
    vm: GalleryViewModel,
    partialAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onSelectingChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val media by vm.media.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val selection = rememberSelection(media)
    LaunchedEffect(selection.active) { onSelectingChange(selection.active) }
    DisposableEffect(Unit) { onDispose { onSelectingChange(false) } }
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sort by vm.mediaSort.collectAsStateWithLifecycle()
    val filter by vm.mediaFilter.collectAsStateWithLifecycle()
    val columns by vm.gridColumns.collectAsStateWithLifecycle()
    var showOptions by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyGridState()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // Same bar (same height) in selection mode, so the grid doesn't jump under the finger
            LargeTopAppBar(
                navigationIcon = {
                    if (selection.active) {
                        IconButton(onClick = selection::clear) { Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel_selection)) }
                    }
                },
                title = {
                    Column {
                        val count = selection.ids.size
                        Text(
                            if (count == 0) stringResource(R.string.tab_photos)
                            else pluralStringResource(R.plurals.selected_count, count, count),
                            fontWeight = FontWeight.Bold,
                        )
                        if (scroll.state.collapsedFraction < 0.5f) {
                            Text(
                                if (selection.active) stringResource(R.string.selection_hint)
                                else stringResource(
                                    R.string.subtitle_with_sort,
                                    pluralStringResource(R.plurals.items_count, media.size, media.size),
                                    stringResource(sort.label),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (selection.active) {
                        IconButton(onClick = { selection.selectAll(media) }) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.action_select_all)) }
                    } else {
                        IconButton(onClick = { selection.selectAll(media) }, enabled = media.isNotEmpty()) {
                            Icon(Icons.Rounded.Checklist, stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = { showOptions = true }) { Icon(Icons.Rounded.Tune, stringResource(R.string.action_sort_filter)) }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.action_settings)) }
                    }
                },
                scrollBehavior = scroll,
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
                    contentPadding = bottomPadding(),
                    onClick = { if (selection.active) selection.toggle(it) else onOpen(it) },
                    selection = selection,
                    header = if (partialAccess) ({
                        item(span = { GridItemSpan(maxLineSpan) }, key = "partial") {
                            PartialAccessBanner(onRequestAccess)
                        }
                    }) else null,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumsTab(vm: GalleryViewModel, onOpen: (Album) -> Unit, onOpenTrash: () -> Unit, onOpenSettings: () -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val settings by AppSettings.flow.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sort by vm.albumSort.collectAsStateWithLifecycle()
    val desc by vm.albumSortDescending.collectAsStateWithLifecycle()
    var showOptions by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyGridState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(albums, query) {
        val q = normalizeForSearch(query.trim())
        if (q.isEmpty()) albums
        else albums.filter { normalizeForSearch(it.name).contains(q) || normalizeForSearch(it.relativePath).contains(q) }
    }
    fun closeSearch() {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching, onBack = ::closeSearch)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (searching) {
                AlbumSearchBar(query, onQueryChange = { query = it }, onClose = ::closeSearch)
            } else LargeTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tab_albums), fontWeight = FontWeight.Bold)
                        if (scroll.state.collapsedFraction < 0.5f) {
                            Text(
                                stringResource(
                                    R.string.subtitle_with_sort,
                                    pluralStringResource(R.plurals.folders_count, albums.size, albums.size),
                                    stringResource(sort.label),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, stringResource(R.string.search_albums)) }
                    IconButton(onClick = { showOptions = true }) { Icon(Icons.Rounded.Tune, stringResource(R.string.action_sort)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.action_settings)) }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                albums.isEmpty() -> EmptyState(Icons.Rounded.Folder, stringResource(R.string.empty_albums))
                shown.isEmpty() -> EmptyState(Icons.Rounded.Search, stringResource(R.string.no_album_match, query.trim()))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    state = gridState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(shown, key = { it.id }) { album ->
                        AlbumCard(album, snappyItemModifier()) { onOpen(album) }
                    }
                    if (settings.showTrash && query.isBlank()) {
                        item(key = "trash") { TrashCard(trash, snappyItemModifier(), onOpenTrash) }
                    }
                }
            }
        }
    }

    if (showOptions) {
        AlbumOptionsSheet(
            sort = sort,
            descending = desc,
            onSort = vm::setAlbumSort,
            onDismiss = { showOptions = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_albums)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_clear)) }
            }
        },
    )
}

@Composable
private fun TrashCard(trash: List<MediaItem>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            trash.firstOrNull()?.let { cover ->
                AsyncImage(
                    model = Thumb(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.35f),
                )
            }
            Icon(
                Icons.Rounded.Delete, null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.trash_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            "${trash.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AlbumCard(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = Thumb(album.cover),
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            "${album.count}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun FloatingNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.shadow(16.dp, CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NavPill(Icons.Rounded.Collections, stringResource(R.string.tab_photos), selected == 0) { onSelect(0) }
            NavPill(Icons.Rounded.Folder, stringResource(R.string.tab_albums), selected == 1) { onSelect(1) }
        }
    }
}

@Composable
private fun NavPill(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "bg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "fg",
    )
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = fg)
        AnimatedVisibility(selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(label, color = fg, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PartialAccessBanner(onRequest: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.padding(8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.partial_access),
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRequest) { Text(stringResource(R.string.partial_access_button)) }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

