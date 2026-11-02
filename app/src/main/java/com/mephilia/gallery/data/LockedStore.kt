package com.mephilia.gallery.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Name of the locked folder in the app's private storage. */
private const val LOCKED_DIR = "locked"

/** Id slots per millisecond, so items locked in the same millisecond still get different ids. */
private const val ID_SLOTS_PER_MILLI = 1000

/** Keeps locked ids clear of the small negative numbers. */
private const val ID_OFFSET = 10

/** Longest extension kept from the original name; longer ones are probably not extensions. */
private const val MAX_EXTENSION_LENGTH = 5

/** Extension of a locked video whose name has none. */
private const val DEFAULT_VIDEO_EXTENSION = "mp4"

/** Extension of a locked photo whose name has none. */
private const val DEFAULT_PHOTO_EXTENSION = "jpg"

/**
 * The locked folder: files moved into the app's private storage, where other apps (and Android's media index)
 * can't see them. Its [LockedIndex] keeps what's needed to show them and to put them back where they were.
 *
 * Locked items have negative ids, so they never collide with MediaStore ids. Private storage is removed when
 * the app is uninstalled, and it's excluded from cloud backups.
 */
class LockedStore(context: Context) {
    /** Reads the originals and writes the restored files. */
    private val resolver = context.contentResolver

    /** The locked folder in private storage. */
    private val dir = File(context.filesDir, LOCKED_DIR).apply { mkdirs() }

    /** What the locked folder holds. */
    private val index = LockedIndex(dir)

    /** The hidden items, newest first. */
    suspend fun load(): List<MediaItem> = withContext(Dispatchers.IO) {
        lock.withLock { index.read() }.mapNotNull { entry ->
            val file = File(dir, entry.file)
            if (!file.exists()) return@mapNotNull null
            MediaItem(
                id = entry.id, uri = Uri.fromFile(file), isVideo = entry.isVideo, name = entry.name, mimeType = entry.mime,
                dateTaken = entry.dateTaken, dateModified = file.lastModified(), size = file.length(),
                width = entry.width, height = entry.height, durationMs = entry.durationMs,
                bucketId = LOCKED_ID, bucketName = "", relativePath = entry.relativePath, locked = true,
            )
        }.sortedByDescending { it.dateTaken }
    }

    /**
     * Copies [item] into the locked folder and returns it as a locked item. The caller then deletes the original;
     * if that's declined, [discard] removes the copy again. If the copy or the index can't be written, the copy is
     * removed and the error is thrown, so the caller keeps the original and the item never shows in both places.
     */
    suspend fun add(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        lock.withLock {
            val entries = index.read()
            val id = -(System.currentTimeMillis() * ID_SLOTS_PER_MILLI + entries.size % ID_SLOTS_PER_MILLI) - ID_OFFSET
            val file = File(dir, "${-id}.${extensionOf(item)}")
            try {
                checkNotNull(resolver.openInputStream(item.uri)).use { input ->
                    FileOutputStream(file).use { input.copyToSynced(it) }
                }
                val entry = LockedEntry(
                    id, file.name, item.name, item.mimeType, item.isVideo, item.dateTaken,
                    item.relativePath, item.width, item.height, item.durationMs,
                )
                index.write(entries + entry)
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            item.copy(id = id, uri = Uri.fromFile(file), locked = true, bucketId = LOCKED_ID)
        }
    }

    /** Deletes the locked items with [ids] for good. */
    suspend fun discard(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val (gone, kept) = index.read().partition { it.id in ids }
            gone.forEach { File(dir, it.file).delete() }
            index.write(kept)
        }
    }

    /**
     * Puts [items] back in the gallery, in their original folder with their original date when possible
     * (so they go back to their place in the timeline), otherwise in the app's folder. Returns how many failed.
     */
    suspend fun restore(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        var failed = 0
        val restored = ArrayList<Long>()
        for (item in items) {
            val file = File(item.uri.path ?: continue)
            val uri = insertRestored(item)
            if (uri == null) {
                failed++
                continue
            }
            try {
                file.inputStream().use { resolver.writeSynced(uri, MODE_WRITE, it) }
                resolver.publish(uri)
                restored += item.id
            } catch (_: Exception) {
                resolver.delete(uri, null, null)
                failed++
            }
        }
        discard(restored)
        failed
    }

    /**
     * A new pending gallery file for [item]: in its original folder with its date, then without the date,
     * then in the app's folder; null if all of these are refused.
     */
    private fun insertRestored(item: MediaItem): Uri? {
        val collection = mediaCollection(item.isVideo)
        fun insert(folder: String, dateTaken: Long?) = resolver.insertPending(collection, item.name, item.mimeType, folder, dateTaken)
        return insert(item.relativePath, item.dateTaken)
            ?: insert(item.relativePath, null)
            ?: insert(StorageFolders.appFolder(item.isVideo), null)
    }

    /** The extension of the locked copy of [item]: its own, or a default one by type. */
    private fun extensionOf(item: MediaItem): String =
        item.name.substringAfterLast('.', "").takeIf { it.length in 1..MAX_EXTENSION_LENGTH }
            ?: if (item.isVideo) DEFAULT_VIDEO_EXTENSION else DEFAULT_PHOTO_EXTENSION

    private companion object {
        /** Serializes changes to the index, across every instance. */
        val lock = Mutex()
    }
}
