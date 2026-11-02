package com.mephilia.gallery.data

import com.mephilia.gallery.at
import com.mephilia.gallery.media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class MediaQueriesTest {
    private val a = media(1, taken = 300, name = "beta.jpg", size = 10)
    private val b = media(2, taken = 100, name = "Alpha.jpg", size = 30, video = true)
    private val c = media(3, taken = 200, name = "charlie.jpg", size = 20)
    private val all = listOf(a, b, c)

    @Test
    fun sortsByDateNameAndSize() {
        assertEquals(listOf(a, c, b), MediaQueries.sort(all, MediaSort.DateDesc, 0))
        assertEquals(listOf(b, c, a), MediaQueries.sort(all, MediaSort.DateAsc, 0))
        // Names ignore case: "Alpha" comes before "beta"
        assertEquals(listOf(b, a, c), MediaQueries.sort(all, MediaSort.Name, 0))
        assertEquals(listOf(b, c, a), MediaQueries.sort(all, MediaSort.Size, 0))
    }

    @Test
    fun randomOrderIsStableForASeed() {
        val many = (1L..50L).map { media(it, taken = it) }
        val first = MediaQueries.sort(many, MediaSort.Random, 42)
        // Same seed, same order, whatever the input order (refreshes must not reshuffle the grid)
        assertEquals(first, MediaQueries.sort(many.reversed(), MediaSort.Random, 42))
        assertNotEquals(first, MediaQueries.sort(many, MediaSort.Random, 43))
        assertEquals(many.toSet(), first.toSet())
    }

    @Test
    fun filtersPhotosAndVideos() {
        assertEquals(listOf(a, c), MediaQueries.filter(all, MediaFilter.Photos))
        assertEquals(listOf(b), MediaQueries.filter(all, MediaFilter.Videos))
        assertEquals(all, MediaQueries.filter(all, MediaFilter.All))
    }

    @Test
    fun groupsFoldersIntoAlbums() {
        val items = listOf(
            media(1, taken = 10, modified = 50, bucket = 1, bucketName = "Camera"),
            media(2, taken = 30, modified = 40, bucket = 1, bucketName = "Camera"),
            media(3, taken = 20, modified = 60, bucket = 2, bucketName = "screenshots"),
        )
        val albums = MediaQueries.albums(items, AlbumSort.Modified, descending = true)
        assertEquals(listOf(2L, 1L), albums.map { it.id })
        val camera = albums.single { it.id == 1L }
        assertEquals(2, camera.count)
        // Covered by the newest photo, created at the oldest date, modified at the latest
        assertEquals(2L, camera.cover.id)
        assertEquals(10L, camera.created)
        assertEquals(50L, camera.lastModified)
    }

    @Test
    fun albumNamesSortAToZByDefault() {
        val items = listOf(
            media(1, bucket = 1, bucketName = "zoo"),
            media(2, bucket = 2, bucketName = "Alps"),
            media(3, bucket = 3, bucketName = "beach"),
        )
        // "Descending" (the default) means A → Z for names
        assertEquals(listOf("Alps", "beach", "zoo"), MediaQueries.albums(items, AlbumSort.Name, true).map { it.name })
        assertEquals(listOf("zoo", "beach", "Alps"), MediaQueries.albums(items, AlbumSort.Name, false).map { it.name })
    }

    @Test
    fun onThisDayKeepsSameDateInPreviousYearsOnly() {
        val zone = ZoneId.of("Europe/Paris")
        val today = LocalDate.of(2026, 11, 2)
        val lastYear = media(1, taken = at(2025, 11, 2, zone = zone))
        val longAgo = media(2, taken = at(2019, 11, 2, hour = 8, zone = zone))
        val thisYear = media(3, taken = at(2026, 11, 2, zone = zone))
        val dayBefore = media(4, taken = at(2025, 11, 1, zone = zone))
        val result = MediaQueries.onThisDay(listOf(longAgo, thisYear, dayBefore, lastYear), today, zone)
        assertEquals(listOf(lastYear, longAgo), result)
    }

    @Test
    fun onThisDayUsesTheLocalDate() {
        // 23:30 in Paris on Nov 2 is already Nov 2 there, but 22:30 UTC: the local day must win
        val zone = ZoneId.of("Europe/Paris")
        val late = media(1, taken = at(2024, 11, 2, hour = 23, zone = zone))
        assertTrue(MediaQueries.onThisDay(listOf(late), LocalDate.of(2026, 11, 2), zone).isNotEmpty())
        assertTrue(MediaQueries.onThisDay(listOf(late), LocalDate.of(2026, 11, 3), zone).isEmpty())
    }
}
