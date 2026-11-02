package com.mephilia.gallery.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.mephilia.gallery.media
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import android.provider.MediaStore
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The locked folder holds the only copy of hidden files: it must never lose one. */
@RunWith(RobolectricTestRunner::class)
class LockedStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val lockedDir = File(context.filesDir, "locked")

    /** A source file standing in for a gallery photo or video. */
    private fun source(name: String, bytes: ByteArray, video: Boolean = false): MediaItem {
        val f = File(context.cacheDir, name).apply { writeBytes(bytes) }
        return media(id = name.hashCode().toLong(), name = name, video = video, taken = 1_700_000_000_000, path = "DCIM/Camera/", uri = Uri.fromFile(f))
    }

    @Test
    fun addedItemsAreCopiedAndListed() = runBlocking {
        val store = LockedStore(context)
        val bytes = ByteArray(5000) { (it % 251).toByte() }
        val added = store.add(source("beach.jpg", bytes))

        assertTrue(added.locked)
        assertTrue(added.id < 0)
        assertArrayEquals(bytes, File(added.uri.path!!).readBytes())
        val listed = store.load().single()
        assertEquals("beach.jpg", listed.name)
        assertEquals(1_700_000_000_000, listed.dateTaken)
        assertEquals("DCIM/Camera/", listed.relativePath)
        assertTrue(listed.locked)
    }

    @Test
    fun survivesAppRestart() = runBlocking {
        LockedStore(context).add(source("a.jpg", byteArrayOf(1)))
        LockedStore(context).add(source("b.mp4", byteArrayOf(2), video = true))
        val reloaded = LockedStore(context).load()
        assertEquals(setOf("a.jpg", "b.mp4"), reloaded.map { it.name }.toSet())
        assertTrue(reloaded.single { it.name == "b.mp4" }.isVideo)
    }

    @Test
    fun manyItemsGetDistinctIdsAndFiles() = runBlocking {
        val store = LockedStore(context)
        val added = (1..30).map { store.add(source("p$it.jpg", byteArrayOf(it.toByte()))) }
        assertEquals(30, added.map { it.id }.toSet().size)
        assertEquals(30, added.map { it.uri }.toSet().size)
        assertEquals(30, store.load().size)
    }

    @Test
    fun discardDeletesTheFile() = runBlocking {
        val store = LockedStore(context)
        val keep = store.add(source("keep.jpg", byteArrayOf(1)))
        val drop = store.add(source("drop.jpg", byteArrayOf(2)))
        store.discard(listOf(drop.id))
        assertFalse(File(drop.uri.path!!).exists())
        assertEquals(listOf(keep.id), store.load().map { it.id })
    }

    @Test
    fun damagedIndexDoesNotHideFiles() = runBlocking {
        val store = LockedStore(context)
        val a = store.add(source("a.jpg", byteArrayOf(1)))
        val b = store.add(source("clip.mp4", byteArrayOf(2), video = true))
        File(lockedDir, "index.json").writeText("{ not json")

        // Files are still listed (recovered from the folder itself), with their type
        val recovered = LockedStore(context).load()
        assertEquals(setOf(a.id, b.id), recovered.map { it.id }.toSet())
        assertTrue(recovered.single { it.id == b.id }.isVideo)

        // And the next change writes them back into a valid index
        val c = store.add(source("c.jpg", byteArrayOf(3)))
        assertEquals(setOf(a.id, b.id, c.id), LockedStore(context).load().map { it.id }.toSet())
    }

    @Test
    fun damagedIndexKeepsOriginalFoldersFromTheBackup() = runBlocking {
        val store = LockedStore(context)
        val a = store.add(source("a.jpg", byteArrayOf(1)))
        val b = store.add(source("b.jpg", byteArrayOf(2)))
        store.add(source("c.jpg", byteArrayOf(3)))
        File(lockedDir, "index.json").writeText("{ not json")

        // The backup is one change behind: a and b come back with their folder and date, c as a recovered file
        val recovered = LockedStore(context).load().associateBy { it.name }
        for (item in listOf(a, b)) {
            assertEquals("DCIM/Camera/", recovered.getValue(item.name).relativePath)
            assertEquals(1_700_000_000_000, recovered.getValue(item.name).dateTaken)
        }
        assertEquals(3, recovered.size)
    }

    @Test
    fun backupDoesNotBringBackDeletedItems() = runBlocking {
        val store = LockedStore(context)
        val a = store.add(source("a.jpg", byteArrayOf(1)))
        val b = store.add(source("b.jpg", byteArrayOf(2)))
        store.discard(listOf(b.id))
        // The backup still lists b, but its file is gone
        File(lockedDir, "index.json").delete()
        assertEquals(listOf(a.id), LockedStore(context).load().map { it.id })
    }

    @Test
    fun missingFilesAreSkippedNotFatal() = runBlocking {
        val store = LockedStore(context)
        val gone = store.add(source("gone.jpg", byteArrayOf(1)))
        val kept = store.add(source("kept.jpg", byteArrayOf(2)))
        File(gone.uri.path!!).delete()
        assertEquals(listOf(kept.id), store.load().map { it.id })
    }

    @Test
    fun restorePutsTheFileBackWhereItWas() = runBlocking {
        val mediaStore = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
        val store = LockedStore(context)
        val bytes = ByteArray(3000) { (it * 7).toByte() }
        val item = store.add(source("holiday.jpg", bytes))

        assertEquals(0, store.restore(listOf(item)))

        val row = mediaStore.rows.values.single()
        assertEquals("holiday.jpg", row.values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals("DCIM/Camera/", row.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals(1_700_000_000_000, row.values.getAsLong(MediaStore.MediaColumns.DATE_TAKEN))
        // Published (no longer pending) with the exact same content
        assertEquals(0, row.values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertArrayEquals(bytes, row.file.readBytes())
        // Only then is the locked copy removed
        assertFalse(File(item.uri.path!!).exists())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun restoreFallsBackWhenTheOriginalFolderIsRefused() = runBlocking {
        val mediaStore = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
        mediaStore.refusedPaths += "DCIM/Camera/"
        val store = LockedStore(context)
        val item = store.add(source("x.jpg", byteArrayOf(1, 2)))
        assertEquals(0, store.restore(listOf(item)))
        assertEquals("Pictures/Gallery/", mediaStore.rows.values.single().values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
    }

    @Test
    fun failedRestoreKeepsTheLockedFile() = runBlocking {
        val mediaStore = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
        mediaStore.refuseEverything = true
        val store = LockedStore(context)
        val item = store.add(source("precious.jpg", byteArrayOf(9, 9, 9)))

        assertEquals(1, store.restore(listOf(item)))
        assertTrue(File(item.uri.path!!).exists())
        assertEquals(listOf(item.id), store.load().map { it.id })
    }

    @Test
    fun failedWriteRemovesTheHalfMadeGalleryFile() = runBlocking {
        val mediaStore = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
        mediaStore.failWrites = true
        val store = LockedStore(context)
        val item = store.add(source("y.jpg", byteArrayOf(1)))

        assertEquals(1, store.restore(listOf(item)))
        // No empty file left in the gallery, and the locked copy is still there
        assertTrue(mediaStore.rows.isEmpty())
        assertTrue(File(item.uri.path!!).exists())
    }

    @Test
    fun unreadableSourceLeavesNothingBehind() = runBlocking {
        val store = LockedStore(context)
        val missing = media(1, name = "missing.jpg", uri = Uri.fromFile(File(context.cacheDir, "does-not-exist.jpg")))
        runCatching { store.add(missing) }
        assertTrue(store.load().isEmpty())
        assertTrue(lockedDir.listFiles().orEmpty().none { it.name.endsWith(".jpg") })
    }

    @Test
    fun failedIndexWriteLeavesNoOrphanCopy() = runBlocking {
        val store = LockedStore(context)
        val blocker = File(lockedDir, "index.json.tmp").apply { mkdirs() }
        try {
            val result = runCatching { store.add(source("street.jpg", ByteArray(800) { 7 })) }
            assertTrue(result.isFailure)
        } finally {
            blocker.deleteRecursively()
        }
        assertTrue(store.load().isEmpty())
        assertTrue(lockedDir.listFiles().orEmpty().none { it.name.endsWith(".jpg") })
    }
}
