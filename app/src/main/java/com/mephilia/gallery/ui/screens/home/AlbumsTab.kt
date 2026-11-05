package com.mephilia.gallery.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.R
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.AlbumAction
import com.mephilia.gallery.ui.components.AlbumActionDialog
import com.mephilia.gallery.ui.components.AlbumMenuEntries
import com.mephilia.gallery.ui.components.AlbumOptionsSheet
import com.mephilia.gallery.ui.components.EmptyState
import com.mephilia.gallery.ui.components.canRenameFolder
import com.mephilia.gallery.ui.components.normalizeForSearch
import com.mephilia.gallery.ui.components.snappyItemModifier
import kotlinx.coroutines.flow.Flow

/**
 * The albums tab: every folder holding photos or videos, with search, sort and album creation.
 * A long-press on an album offers to pin it, hide it from the Photos tab, rename it or delete it.
 * Pinned albums come first, and pinned or hidden ones carry a badge.
 *
 * @param onNewAlbum starts creating an album with the given name.
 * @param scrollToTop emits when the grid should go back to the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumsTab(
    vm: GalleryViewModel,
    onOpen: (Album) -> Unit,
    onOpenSettings: () -> Unit,
    onNewAlbum: (String) -> Unit,
    scrollToTop: Flow<Unit>,
) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sort by vm.albumSort.collectAsStateWithLifecycle()
    val descending by vm.albumSortDescending.collectAsStateWithLifecycle()
    var showNewAlbum by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyGridState()
    val shown = remember(albums, query) { filterAlbums(albums, query) }
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val pinned by vm.pinnedAlbums.collectAsStateWithLifecycle()
    val excluded by vm.excludedAlbums.collectAsStateWithLifecycle()
    var menuAlbum by remember { mutableStateOf<Album?>(null) }
    var albumAction by remember { mutableStateOf<Pair<Album, AlbumAction>?>(null) }

    fun closeSearch() {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching, onBack = ::closeSearch)
    ScrollToTopOn(scrollToTop, gridState, scroll.state)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (searching) {
                AlbumSearchBar(query, onQueryChange = { query = it }, onClose = ::closeSearch)
            } else {
                LargeTopAppBar(
                    title = {
                        TabTitle(
                            title = stringResource(R.string.tab_albums),
                            subtitle = stringResource(
                                R.string.subtitle_with_sort,
                                pluralStringResource(R.plurals.folders_count, albums.size, albums.size),
                                stringResource(sort.label),
                            ),
                            collapsedFraction = scroll.state.collapsedFraction,
                        )
                    },
                    actions = {
                        IconButton(onClick = { showNewAlbum = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.new_album)) }
                        IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, stringResource(R.string.search_albums)) }
                        IconButton(onClick = { showOptions = true }) { Icon(Icons.Rounded.Tune, stringResource(R.string.action_sort)) }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.action_settings)) }
                    },
                    scrollBehavior = scroll,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                albums.isEmpty() -> EmptyState(Icons.Rounded.Folder, stringResource(R.string.empty_albums))
                shown.isEmpty() -> EmptyState(Icons.Rounded.Search, stringResource(R.string.no_album_match, query.trim()))
                else -> CardGrid(state = gridState) {
                    items(shown, key = { it.id }) { album ->
                        Box(snappyItemModifier()) {
                            val isPinned = album.relativePath in pinned
                            val isExcluded = album.relativePath in excluded
                            AlbumCard(album, pinned = isPinned, excluded = isExcluded, onLongClick = { menuAlbum = album }) { onOpen(album) }
                            DropdownMenu(expanded = menuAlbum == album, onDismissRequest = { menuAlbum = null }) {
                                AlbumMenuEntries(canRenameFolder(album.relativePath), isPinned, isExcluded) { action ->
                                    menuAlbum = null
                                    when (action) {
                                        AlbumAction.TogglePin -> vm.togglePinned(album.relativePath)
                                        AlbumAction.ToggleExcluded -> vm.toggleExcluded(album.relativePath)
                                        AlbumAction.Rename, AlbumAction.Delete -> albumAction = album to action
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    albumAction?.let { (album, action) ->
        val folderItems = remember(allItems, album.id) { allItems.filter { it.bucketId == album.id } }
        AlbumActionDialog(
            action = action,
            name = album.name,
            relativePath = album.relativePath,
            items = folderItems,
            albums = albums,
            onDismiss = { albumAction = null },
            onRenamed = { newPath -> vm.albumRenamed(album.relativePath, newPath) },
            onDone = {
                if (action == AlbumAction.Delete) vm.albumDeleted(album.relativePath)
                albumAction = null
            },
        )
    }

    if (showNewAlbum) {
        NewAlbumDialog(onDismiss = { showNewAlbum = false }) { name ->
            showNewAlbum = false
            onNewAlbum(name)
        }
    }

    if (showOptions) {
        AlbumOptionsSheet(
            sort = sort,
            descending = descending,
            onSort = vm::setAlbumSort,
            onDismiss = { showOptions = false },
        )
    }
}

/**
 * The [albums] whose name or path contains [query], ignoring case and accents.
 * A blank query matches every album.
 */
private fun filterAlbums(albums: List<Album>, query: String): List<Album> {
    val normalized = normalizeForSearch(query.trim())
    if (normalized.isEmpty()) return albums
    return albums.filter {
        normalizeForSearch(it.name).contains(normalized) || normalizeForSearch(it.relativePath).contains(normalized)
    }
}
