package com.mephilia.gallery.ui.screens.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.R
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.FAVORITES_ID
import com.mephilia.gallery.data.ON_THIS_DAY_ID
import com.mephilia.gallery.data.PHOTOS_ID
import com.mephilia.gallery.data.VIDEOS_ID
import com.mephilia.gallery.ui.GalleryViewModel

/**
 * The collections tab: groups of items that aren't folders. On this day, favorites, all photos, all videos,
 * the trash (if enabled in the settings) and the locked folder. Albums hidden from Photos are left out of the
 * photos and videos counts and covers.
 *
 * @param onOpenCollection opens a pseudo album by its id (such as [FAVORITES_ID]), with its display name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionsTab(
    vm: GalleryViewModel,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenLocked: () -> Unit,
    onOpenCollection: (id: Long, name: String) -> Unit,
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val onThisDay by vm.onThisDay.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val all by vm.visibleItems.collectAsStateWithLifecycle()
    val settings by AppSettings.flow.collectAsStateWithLifecycle()
    val (videos, photos) = remember(all) { all.partition { it.isVideo } }
    val photoCover = remember(photos) { photos.maxByOrNull { it.dateTaken } }
    val videoCover = remember(videos) { videos.maxByOrNull { it.dateTaken } }
    val onThisDayName = stringResource(R.string.on_this_day)
    val favoritesName = stringResource(R.string.favorites_title)
    val photosName = stringResource(R.string.filter_photos)
    val videosName = stringResource(R.string.filter_videos)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_collections), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.action_settings)) }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        CardGrid(Modifier.padding(padding).fillMaxSize()) {
            item(key = "on-this-day") {
                CollectionCard(
                    Icons.Rounded.History, onThisDayName,
                    if (onThisDay.isEmpty()) stringResource(R.string.on_this_day_none) else "${onThisDay.size}",
                    onThisDay.firstOrNull(),
                ) { onOpenCollection(ON_THIS_DAY_ID, onThisDayName) }
            }
            item(key = "favorites") {
                CollectionCard(Icons.Rounded.Favorite, favoritesName, "${favorites.size}", favorites.firstOrNull()) {
                    onOpenCollection(FAVORITES_ID, favoritesName)
                }
            }
            item(key = "photos") {
                CollectionCard(Icons.Rounded.Photo, photosName, "${photos.size}", photoCover) {
                    onOpenCollection(PHOTOS_ID, photosName)
                }
            }
            item(key = "videos") {
                CollectionCard(Icons.Rounded.Videocam, videosName, "${videos.size}", videoCover) {
                    onOpenCollection(VIDEOS_ID, videosName)
                }
            }
            if (settings.showTrash) {
                item(key = "trash") { TrashCard(trash, onClick = onOpenTrash) }
            }
            item(key = "locked") {
                CollectionCard(Icons.Rounded.Lock, stringResource(R.string.locked_title), null, null, onClick = onOpenLocked)
            }
        }
    }
}
