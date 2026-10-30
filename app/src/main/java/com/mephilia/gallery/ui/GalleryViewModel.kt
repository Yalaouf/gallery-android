package com.mephilia.gallery.ui

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

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val prefs = Prefs(app)

    private val all = MutableStateFlow<List<MediaItem>>(emptyList())
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
            val albums = items.groupBy { it.bucketId }.map { (id, list) ->
                val newest = list.maxBy { it.dateTaken }
                Album(
                    id = id,
                    name = newest.bucketName,
                    relativePath = newest.relativePath,
                    cover = newest,
                    count = list.size,
                    lastModified = list.maxOf { it.dateModified },
                    created = list.minOf { minOf(it.dateTaken, it.dateModified) },
                )
            }
            val sorted = when (sort) {
                AlbumSort.Modified -> albums.sortedBy { it.lastModified }
                AlbumSort.Created -> albums.sortedBy { it.created }
                AlbumSort.Name -> albums.sortedBy { it.name.lowercase() }
            }
            // For names, "descending" means A→Z, the more natural default
            if (desc != (sort == AlbumSort.Name)) sorted.reversed() else sorted
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun albumMedia(bucketId: Long): Flow<List<MediaItem>> =
        combine(all, mediaSort, mediaFilter, shuffleSeed) { items, sort, filter, seed ->
            sortMedia(filterMedia(items.filter { it.bucketId == bucketId }, filter), sort, seed)
        }

    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            reload()
            repo.changes().debounce(400).collect { reload() }
        }
    }

    fun reload() {
        viewModelScope.launch {
            all.value = repo.loadAll()
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

    private fun filterMedia(items: List<MediaItem>, filter: MediaFilter) = when (filter) {
        MediaFilter.All -> items
        MediaFilter.Photos -> items.filter { !it.isVideo }
        MediaFilter.Videos -> items.filter { it.isVideo }
    }

    private fun sortMedia(items: List<MediaItem>, sort: MediaSort, seed: Long) = when (sort) {
        MediaSort.DateDesc -> items.sortedByDescending { it.dateTaken }
        MediaSort.DateAsc -> items.sortedBy { it.dateTaken }
        MediaSort.Random -> items.sortedBy { it.id }.shuffled(Random(seed))
        MediaSort.Name -> items.sortedBy { it.name.lowercase() }
        MediaSort.Size -> items.sortedByDescending { it.size }
    }
}
