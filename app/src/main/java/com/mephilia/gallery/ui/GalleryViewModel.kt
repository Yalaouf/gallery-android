package com.mephilia.gallery.ui

import com.mephilia.gallery.data.MediaQueries
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.AlbumSort
import com.mephilia.gallery.data.MediaFilter
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaRepository
import com.mephilia.gallery.data.MediaSort
import com.mephilia.gallery.data.Prefs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.mephilia.gallery.data.MediaEvents
import kotlinx.coroutines.flow.update
import com.mephilia.gallery.data.AppSettings
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import com.mephilia.gallery.data.FAVORITES_ID
import com.mephilia.gallery.data.LOCKED_ID
import com.mephilia.gallery.data.LockedStore
import com.mephilia.gallery.data.ON_THIS_DAY_ID
import com.mephilia.gallery.data.PHOTOS_ID
import com.mephilia.gallery.data.VIDEOS_ID
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    val lockedStore = LockedStore(app)
    /** Locked folder content, only loaded while it's unlocked. */
    val locked = MutableStateFlow<List<MediaItem>>(emptyList())
    val lockedUnlocked = MutableStateFlow(false)
    /** While the unlock prompt is shown (the PIN screen hides the app, which must not relock it). */
    @Volatile var authenticating = false
    private val prefs = Prefs(app)

    private val all = MutableStateFlow<List<MediaItem>>(emptyList())
    /** Everything, unsorted and unfiltered. */
    val allItems: StateFlow<List<MediaItem>> = all
    val trash = MutableStateFlow<List<MediaItem>>(emptyList())
    /** When the app last replaced each file, by id. */
    private val replacedAt = HashMap<Long, Long>()
    val loading = MutableStateFlow(true)

    val mediaSort = MutableStateFlow(prefs.mediaSort)
    val mediaFilter = MutableStateFlow(prefs.mediaFilter)
    val albumSort = MutableStateFlow(prefs.albumSort)
    val albumSortDescending = MutableStateFlow(prefs.albumSortDescending)
    val gridColumns = MutableStateFlow(prefs.gridColumns)
    private val shuffleSeed = MutableStateFlow(Random.nextLong())

    val media: StateFlow<List<MediaItem>> =
        combine(all, mediaSort, mediaFilter, shuffleSeed) { items, sort, filter, seed ->
            sortMedia(filterMedia(items, filter), sort, seed)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> =
        combine(all, albumSort, albumSortDescending) { items, sort, desc ->
            MediaQueries.albums(items, sort, desc)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<MediaItem>> =
        all.map { list -> list.filter { it.favorite }.sortedByDescending { it.dateTaken } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Photos and videos taken on today's date in previous years, newest year first. */
    val onThisDay: StateFlow<List<MediaItem>> =
        all.map { list ->
            val zone = ZoneId.systemDefault()
            MediaQueries.onThisDay(list, LocalDate.now(zone), zone)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** A folder, or one of the pseudo albums (favorites, on this day, locked folder). */
    fun albumMedia(bucketId: Long): Flow<List<MediaItem>> = when (bucketId) {
        FAVORITES_ID -> favorites
        ON_THIS_DAY_ID -> onThisDay
        LOCKED_ID -> locked
        // All photos or all videos, in the chosen sort (the photos/videos filter doesn't apply here)
        PHOTOS_ID, VIDEOS_ID -> combine(all, mediaSort, shuffleSeed) { items, sort, seed ->
            sortMedia(items.filter { it.isVideo == (bucketId == VIDEOS_ID) }, sort, seed)
        }
        else -> combine(all, mediaSort, mediaFilter, shuffleSeed) { items, sort, filter, seed ->
            sortMedia(filterMedia(items.filter { it.bucketId == bucketId }, filter), sort, seed)
        }
    }

    fun unlock() {
        lockedUnlocked.value = true
        reloadLocked()
    }

    fun relock() {
        lockedUnlocked.value = false
        locked.value = emptyList()
    }

    fun reloadLocked() {
        if (!lockedUnlocked.value) return
        viewModelScope.launch { locked.value = lockedStore.load() }
    }

    private var started = false

    init {
        viewModelScope.launch {
            AppSettings.flow.map { it.showTrash }.distinctUntilChanged().drop(1).collect { reloadTrash() }
        }
        viewModelScope.launch {
            MediaEvents.events.collect { event ->
                when (event) {
                    is MediaEvents.Event.Favorited -> all.update { list ->
                        list.map { if (it.id in event.ids) it.copy(favorite = event.favorite) else it }
                    }
                    is MediaEvents.Event.Removed -> {
                        locked.update { list -> list.filter { it.id !in event.ids } }
                        all.update { list -> list.filter { it.id !in event.ids } }
                        trash.update { list -> list.filter { it.id !in event.ids } }
                        reloadTrash()
                    }
                    // A new dateModified changes the image cache keys, so the new content is loaded right away
                    is MediaEvents.Event.Replaced -> {
                        val now = System.currentTimeMillis()
                        replacedAt[event.id] = now
                        all.update { list -> list.map { if (it.id == event.id) it.copy(dateModified = now) else it } }
                    }
                    MediaEvents.Event.Changed -> {
                        reload()
                        reloadLocked()
                    }
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            reload()
            repo.changes().debounce(150).collect { reload() }
        }
    }

    /**
     * Called when the app comes back to the foreground. While in the background the app may be frozen by Android,
     * and MediaStore's change notifications (e.g. files deleted from another gallery) are then lost.
     */
    fun refresh() {
        if (started) reload()
    }

    fun reloadTrash() {
        viewModelScope.launch {
            trash.value = if (AppSettings.current.showTrash) repo.loadTrash() else emptyList()
        }
    }

    fun reload() {
        reloadTrash()
        viewModelScope.launch {
            all.value = repo.loadAll().map { item ->
                // MediaStore dates have 1 s precision: keep our more precise replace time unless the file
                // changed again since, otherwise the image cache key flips and the picture reloads (black flash)
                val replaced = replacedAt[item.id]
                if (replaced != null && replaced > item.dateModified) item.copy(dateModified = replaced) else item
            }
            loading.value = false
        }
    }

    fun setMediaSort(sort: MediaSort) {
        if (sort == MediaSort.Random) shuffleSeed.value = Random.nextLong()
        mediaSort.value = sort
        prefs.mediaSort = sort
    }

    fun setMediaFilter(filter: MediaFilter) {
        mediaFilter.value = filter
        prefs.mediaFilter = filter
    }

    fun setAlbumSort(sort: AlbumSort) {
        if (albumSort.value == sort) {
            albumSortDescending.value = !albumSortDescending.value
            prefs.albumSortDescending = albumSortDescending.value
        } else {
            albumSort.value = sort
            prefs.albumSort = sort
        }
    }

    fun setGridColumns(columns: Int) {
        gridColumns.value = columns
        prefs.gridColumns = columns
    }

    private fun filterMedia(items: List<MediaItem>, filter: MediaFilter) = MediaQueries.filter(items, filter)

    private fun sortMedia(items: List<MediaItem>, sort: MediaSort, seed: Long) = MediaQueries.sort(items, sort, seed)
}
