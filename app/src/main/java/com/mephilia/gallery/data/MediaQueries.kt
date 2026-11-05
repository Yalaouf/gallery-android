package com.mephilia.gallery.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Pure list operations behind the gallery screens, kept free of Android so they can be unit tested. */
object MediaQueries {
    /** The [items] the [filter] lets through. */
    fun filter(items: List<MediaItem>, filter: MediaFilter) = when (filter) {
        MediaFilter.All -> items
        MediaFilter.Photos -> items.filter { !it.isVideo }
        MediaFilter.Videos -> items.filter { it.isVideo }
    }

    /** The [items] in the [sort] order. [seed] keeps the random order stable until the user picks "Random" again. */
    fun sort(items: List<MediaItem>, sort: MediaSort, seed: Long) = when (sort) {
        MediaSort.DateDesc -> items.sortedByDescending { it.dateTaken }
        MediaSort.DateAsc -> items.sortedBy { it.dateTaken }
        MediaSort.Random -> items.sortedBy { it.id }.shuffled(Random(seed))
        MediaSort.Name -> items.sortedBy { it.name.lowercase() }
        MediaSort.Size -> items.sortedByDescending { it.size }
    }

    /**
     * One album per folder, covered by its newest item, in the [sort] order. For names, [descending] means
     * A → Z, the more natural default.
     */
    fun albums(items: List<MediaItem>, sort: AlbumSort, descending: Boolean): List<Album> {
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
        return if (descending != (sort == AlbumSort.Name)) sorted.reversed() else sorted
    }

    /**
     * The [albums] with the pinned ones first, in the order of [pinned] (relative paths, in the order they were
     * pinned), then the others in their current order. Pinned paths without an album are ignored.
     */
    fun pinFirst(albums: List<Album>, pinned: List<String>): List<Album> {
        if (pinned.isEmpty()) return albums
        val byPath = albums.associateBy { it.relativePath }
        val first = pinned.mapNotNull { byPath[it] }
        val pinnedSet = pinned.toSet()
        return first + albums.filter { it.relativePath !in pinnedSet }
    }

    /** The [items] outside the [excluded] folders (relative paths). */
    fun withoutExcluded(items: List<MediaItem>, excluded: Set<String>): List<MediaItem> =
        if (excluded.isEmpty()) items else items.filter { it.relativePath !in excluded }

    /**
     * The new capture date of each of [items], by id, when the oldest one is set to [newDate]. With [keepGaps], the
     * others move by the same amount, keeping the time between them; otherwise they all get [newDate].
     */
    fun newDates(items: List<MediaItem>, newDate: Long, keepGaps: Boolean): Map<Long, Long> {
        val oldest = items.minOfOrNull { it.dateTaken } ?: return emptyMap()
        val shift = newDate - oldest
        return items.associate { it.id to if (keepGaps) it.dateTaken + shift else newDate }
    }

    /** The [items] taken on [today]'s month and day in previous years, newest first. */
    fun onThisDay(items: List<MediaItem>, today: LocalDate, zone: ZoneId): List<MediaItem> =
        items.filter {
            val day = Instant.ofEpochMilli(it.dateTaken).atZone(zone).toLocalDate()
            day.monthValue == today.monthValue && day.dayOfMonth == today.dayOfMonth && day.year < today.year
        }.sortedByDescending { it.dateTaken }
}
