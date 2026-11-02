package com.mephilia.gallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.AlbumSort
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.FAVORITES_ID
import com.mephilia.gallery.data.LOCKED_ID
import com.mephilia.gallery.data.LatestLoader
import com.mephilia.gallery.data.LockedStore
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaFilter
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaQueries
import com.mephilia.gallery.data.MediaRepository
import com.mephilia.gallery.data.MediaSort
import com.mephilia.gallery.data.ON_THIS_DAY_ID
import com.mephilia.gallery.data.PHOTOS_ID
import com.mephilia.gallery.data.Prefs
import com.mephilia.gallery.data.VIDEOS_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Changes in the shared storage closer together than this are handled with a single reload. */
private const val CHANGE_DEBOUNCE_MS = 150L

/**
 * The gallery's state, shared by every screen: all photos and videos, the lists derived from them (sorted and
 * filtered photos, albums, favorites, on this day), the trash and the locked folder, and the view choices.
 *
 * The lists are recomputed in the background whenever the items or the view choices change. Each list keeps only
 * its latest load, so an older, slower query can't overwrite a newer result. Changes made by the app itself
 * ([MediaEvents]) are applied right away, without waiting for MediaStore's notification and the reload after it.
 */
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    /** Reads the photos and videos. */
    private val repo = MediaRepository(app)

    /** Remembers the view choices. */
    private val prefs = Prefs(app)

    /** The locked folder. */
    val lockedStore = LockedStore(app)

    /** The locked folder's items, only loaded while it's unlocked. */
    val locked = MutableStateFlow<List<MediaItem>>(emptyList())

    /** Whether the locked folder is unlocked. */
    val lockedUnlocked = MutableStateFlow(false)

    /** Set while the unlock prompt is shown: its PIN screen hides the app, which must not relock it. */
    @Volatile var authenticating = false

    /** Every photo and video, as loaded. */
    private val all = MutableStateFlow<List<MediaItem>>(emptyList())

    /** Every photo and video, unsorted and unfiltered. */
    val allItems: StateFlow<List<MediaItem>> = all

    /** The items in the trash, if it's shown. */
    val trash = MutableStateFlow<List<MediaItem>>(emptyList())

    /** Whether the first load is still running. */
    val loading = MutableStateFlow(true)

    /** When the app last replaced each file, by id, more precisely than MediaStore's dates. */
    private val replacedAt = HashMap<Long, Long>()

    /** How the photos are sorted. */
    val mediaSort = MutableStateFlow(prefs.mediaSort)

    /** Which items the photos tab shows. */
    val mediaFilter = MutableStateFlow(prefs.mediaFilter)

    /** How the albums are sorted. */
    val albumSort = MutableStateFlow(prefs.albumSort)

    /** Whether the albums are sorted in descending order. */
    val albumSortDescending = MutableStateFlow(prefs.albumSortDescending)

    /** Number of columns of the photo grid. */
    val gridColumns = MutableStateFlow(prefs.gridColumns)

    /** Seed of the random order, changed each time "Random" is picked. */
    private val shuffleSeed = MutableStateFlow(Random.nextLong())

    /** The photos tab's items: filtered and sorted. */
    val media: StateFlow<List<MediaItem>> =
        combine(all, mediaSort, mediaFilter, shuffleSeed) { items, sort, filter, seed ->
            MediaQueries.sort(MediaQueries.filter(items, filter), sort, seed)
        }.inBackground()

    /** The folders, sorted. */
    val albums: StateFlow<List<Album>> =
        combine(all, albumSort, albumSortDescending) { items, sort, descending ->
            MediaQueries.albums(items, sort, descending)
        }.inBackground()

    /** The favorites, newest first. */
    val favorites: StateFlow<List<MediaItem>> =
        all.map { items -> items.filter { it.favorite }.sortedByDescending { it.dateTaken } }.inBackground()

    /** Photos and videos taken on today's date in previous years, newest year first. */
    val onThisDay: StateFlow<List<MediaItem>> =
        all.map { items ->
            val zone = ZoneId.systemDefault()
            MediaQueries.onThisDay(items, LocalDate.now(zone), zone)
        }.inBackground()

    /**
     * Loads every item. MediaStore dates have a one-second precision: a file the app replaced keeps its more
     * precise replace time unless it changed again since, otherwise the image cache key would flip and the
     * picture reload (a black flash).
     */
    private val mediaLoader = LatestLoader(
        viewModelScope,
        load = { repo.loadAll() },
        apply = { items ->
            all.value = items.map { item ->
                val replaced = replacedAt[item.id]
                if (replaced != null && replaced > item.dateModified) item.copy(dateModified = replaced) else item
            }
            loading.value = false
        },
    )

    /** Loads the trash, or empties it when it's hidden in the settings. */
    private val trashLoader = LatestLoader(
        viewModelScope,
        load = { if (AppSettings.current.showTrash) repo.loadTrash() else emptyList() },
        apply = { trash.value = it },
    )

    /** Loads the locked folder. If it was relocked during the load, the hidden items must not come back into memory. */
    private val lockedLoader = LatestLoader(
        viewModelScope,
        load = { lockedStore.load() },
        apply = { if (lockedUnlocked.value) locked.value = it },
    )

    /** Whether [start] was called. */
    private var started = false

    init {
        viewModelScope.launch {
            AppSettings.flow.map { it.showTrash }.distinctUntilChanged().drop(1).collect { reloadTrash() }
        }
        viewModelScope.launch { MediaEvents.events.collect(::onEvent) }
    }

    /**
     * The items of a folder, or of a pseudo album (favorites, on this day, locked folder, all photos, all videos).
     * All photos and all videos follow the chosen sort, but not the photos / videos filter.
     */
    fun albumMedia(bucketId: Long): Flow<List<MediaItem>> = when (bucketId) {
        FAVORITES_ID -> favorites
        ON_THIS_DAY_ID -> onThisDay
        LOCKED_ID -> locked
        PHOTOS_ID, VIDEOS_ID -> combine(all, mediaSort, shuffleSeed) { items, sort, seed ->
            MediaQueries.sort(items.filter { it.isVideo == (bucketId == VIDEOS_ID) }, sort, seed)
        }.flowOn(Dispatchers.Default)
        else -> combine(all, mediaSort, mediaFilter, shuffleSeed) { items, sort, filter, seed ->
            MediaQueries.sort(MediaQueries.filter(items.filter { it.bucketId == bucketId }, filter), sort, seed)
        }.flowOn(Dispatchers.Default)
    }

    /** Unlocks the locked folder and loads its items. */
    fun unlock() {
        lockedUnlocked.value = true
        reloadLocked()
    }

    /** Locks the locked folder again and forgets its items. */
    fun relock() {
        lockedUnlocked.value = false
        lockedLoader.cancel()
        locked.value = emptyList()
    }

    /** Reloads the locked folder, if it's unlocked. */
    fun reloadLocked() {
        if (lockedUnlocked.value) lockedLoader.reload()
    }

    /** Loads the gallery and keeps it up to date with the shared storage. Only the first call does anything. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            reload()
            repo.changes().debounce(CHANGE_DEBOUNCE_MS).collect { reload() }
        }
    }

    /**
     * Reloads when the app comes back to the foreground. While in the background, the app may be frozen by
     * Android, and MediaStore's change notifications (e.g. files deleted from another gallery) are then lost.
     */
    fun refresh() {
        if (started) reload()
    }

    /** Reloads the trash. */
    fun reloadTrash() = trashLoader.reload()

    /** Reloads every item and the trash. */
    fun reload() {
        reloadTrash()
        mediaLoader.reload()
    }

    /** Sorts the photos by [sort]; picking "Random" again shuffles them anew. */
    fun setMediaSort(sort: MediaSort) {
        if (sort == MediaSort.Random) shuffleSeed.value = Random.nextLong()
        mediaSort.value = sort
        prefs.mediaSort = sort
    }

    /** Shows the items [filter] lets through in the photos tab. */
    fun setMediaFilter(filter: MediaFilter) {
        mediaFilter.value = filter
        prefs.mediaFilter = filter
    }

    /** Sorts the albums by [sort]; picking the current sort again reverses the order. */
    fun setAlbumSort(sort: AlbumSort) {
        if (albumSort.value == sort) {
            albumSortDescending.value = !albumSortDescending.value
            prefs.albumSortDescending = albumSortDescending.value
        } else {
            albumSort.value = sort
            prefs.albumSort = sort
        }
    }

    /** Shows the photo grid with [columns] columns. */
    fun setGridColumns(columns: Int) {
        gridColumns.value = columns
        prefs.gridColumns = columns
    }

    /**
     * Applies a change made by the app right away. A load started before the change would bring the old state
     * back, so it's restarted. A replaced file gets a new date, which changes its image cache keys so the new
     * content shows at once.
     */
    private fun onEvent(event: MediaEvents.Event) {
        when (event) {
            is MediaEvents.Event.Favorited -> {
                all.update { items -> items.map { if (it.id in event.ids) it.copy(favorite = event.favorite) else it } }
                mediaLoader.restartIfLoading()
            }
            is MediaEvents.Event.Removed -> {
                locked.update { items -> items.filter { it.id !in event.ids } }
                all.update { items -> items.filter { it.id !in event.ids } }
                trash.update { items -> items.filter { it.id !in event.ids } }
                mediaLoader.restartIfLoading()
                lockedLoader.restartIfLoading()
                reloadTrash()
            }
            is MediaEvents.Event.Replaced -> {
                val now = System.currentTimeMillis()
                replacedAt[event.id] = now
                all.update { items -> items.map { if (it.id == event.id) it.copy(dateModified = now) else it } }
                mediaLoader.restartIfLoading()
            }
            MediaEvents.Event.Changed -> {
                reload()
                reloadLocked()
            }
        }
    }

    /** Computes this flow in the background and keeps its latest value, starting with an empty list. */
    private fun <T> Flow<List<T>>.inBackground(): StateFlow<List<T>> =
        flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
