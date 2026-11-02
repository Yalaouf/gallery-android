package com.mephilia.gallery.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.ON_THIS_DAY_ID
import com.mephilia.gallery.ui.GalleryViewModel
import com.mephilia.gallery.ui.components.SettingsSheet
import com.mephilia.gallery.ui.components.ThemedSystemBars
import com.mephilia.gallery.ui.theme.Dimens
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** The three tabs of the home screen, in the order of the navigation bar. */
enum class HomeTab {
    Photos,
    Albums,
    Collections;

    companion object {
        /** The tab at [index] (as stored in the settings), or the first one if the index is unknown. */
        fun fromIndex(index: Int): HomeTab = entries.getOrElse(index) { Photos }
    }
}

/**
 * The app's main screen: the photos, albums and collections tabs, with a floating navigation bar.
 *
 * Each tab keeps its state (scroll position, search…) while another one is shown. Tapping the tab that is already
 * selected scrolls it back to the top. The navigation bar hides while items are being selected, to make room
 * for the selection actions.
 *
 * @param tab the tab shown.
 * @param onTabChange called when another tab is picked in the navigation bar.
 * @param partialAccess whether the user only gave access to some photos, which shows a banner to ask for more.
 * @param onRequestAccess asks again for access to all photos.
 * @param onOpenMedia opens the viewer on an item of the photos tab.
 * @param onOpenAlbum opens a folder.
 * @param onOpenTrash opens the trash.
 * @param onOpenLocked opens the locked folder.
 * @param onNewAlbum starts creating an album with the given name.
 * @param onOpenInCollection opens a pseudo album (favorites, on this day…), on [MediaItem] in the viewer if given.
 */
@Composable
fun HomeScreen(
    vm: GalleryViewModel,
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    partialAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenLocked: () -> Unit,
    onNewAlbum: (String) -> Unit,
    onOpenInCollection: (collectionId: Long, name: String, item: MediaItem?) -> Unit,
) {
    ThemedSystemBars()
    val tabStates = rememberSaveableStateHolder()
    val reselected = remember { MutableSharedFlow<HomeTab>(extraBufferCapacity = 1) }
    val photosToTop = remember(reselected) { reselected.filter { it == HomeTab.Photos }.map { } }
    val albumsToTop = remember(reselected) { reselected.filter { it == HomeTab.Albums }.map { } }
    var selecting by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val openSettings = { showSettings = true }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
    Box(Modifier.fillMaxSize()) {
        tabStates.SaveableStateProvider(tab) {
            when (tab) {
                HomeTab.Photos -> PhotosTab(
                    vm = vm,
                    partialAccess = partialAccess,
                    onRequestAccess = onRequestAccess,
                    onOpen = onOpenMedia,
                    onSelectingChange = { selecting = it },
                    onOpenSettings = openSettings,
                    onOpenOnThisDay = { onOpenInCollection(ON_THIS_DAY_ID, "", it) },
                    scrollToTop = photosToTop,
                )
                HomeTab.Albums -> AlbumsTab(
                    vm = vm,
                    onOpen = onOpenAlbum,
                    onOpenSettings = openSettings,
                    onNewAlbum = onNewAlbum,
                    scrollToTop = albumsToTop,
                )
                HomeTab.Collections -> CollectionsTab(
                    vm = vm,
                    onOpenSettings = openSettings,
                    onOpenTrash = onOpenTrash,
                    onOpenLocked = onOpenLocked,
                    onOpenCollection = { id, name -> onOpenInCollection(id, name, null) },
                )
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
                onSelect = { if (it == tab) reselected.tryEmit(it) else onTabChange(it) },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = Dimens.floatingBarBottomMargin),
            )
        }
    }
}
