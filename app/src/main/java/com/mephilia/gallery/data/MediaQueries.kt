package com.mephilia.gallery.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Pure list operations behind the gallery screens (kept free of Android so they can be unit tested). */
object MediaQueries {
    fun filter(items: List<MediaItem>, filter: MediaFilter) = when (filter) {
        MediaFilter.All -> items
        MediaFilter.Photos -> items.filter { !it.isVideo }
        MediaFilter.Videos -> items.filter { it.isVideo }
    }

    /** [seed] makes the random order stable until the user picks "Random" again. */
    fun sort(items: List<MediaItem>, sort: MediaSort, seed: Long) = when (sort) {
        MediaSort.DateDesc -> items.sortedByDescending { it.dateTaken }
        MediaSort.DateAsc -> items.sortedBy { it.dateTaken }
        MediaSort.Random -> items.sortedBy { it.id }.shuffled(Random(seed))
        MediaSort.Name -> items.sortedBy { it.name.lowercase() }
        MediaSort.Size -> items.sortedByDescending { it.size }
    }

    /** One album per folder, covered by its newest item. */
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
        // For names, "descending" means A→Z, the more natural default
        return if (descending != (sort == AlbumSort.Name)) sorted.reversed() else sorted
    }

    /** Items taken on [today]'s month and day in previous years, newest first. */
    fun onThisDay(items: List<MediaItem>, today: LocalDate, zone: ZoneId): List<MediaItem> =
        items.filter {
            val d = Instant.ofEpochMilli(it.dateTaken).atZone(zone).toLocalDate()
            d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth && d.year < today.year
        }.sortedByDescending { it.dateTaken }
}
