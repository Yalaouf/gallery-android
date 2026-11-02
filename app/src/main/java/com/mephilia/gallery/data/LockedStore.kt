package com.mephilia.gallery.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * The locked folder: files moved into the app's private storage, where other apps (and Android's media
 * index) can't see them. An index file keeps what's needed to show them and to put them back where they were.
 *
 * Private storage is removed when the app is uninstalled, and it's excluded from cloud backups.
 */
class LockedStore(context: Context) {
    private val resolver = context.contentResolver
    private val dir = File(context.filesDir, "locked").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    private data class Entry(
        val id: Long,
        val file: String,
        val name: String,
        val mime: String,
        val isVideo: Boolean,
        val dateTaken: Long,
        val relativePath: String,
        val width: Int,
        val height: Int,
        val durationMs: Long,
    )

    suspend fun load(): List<MediaItem> = withContext(Dispatchers.IO) {
        lock.withLock { readIndex() }.mapNotNull { e ->
            val f = File(dir, e.file)
            if (!f.exists()) return@mapNotNull null
            MediaItem(
                id = e.id, uri = Uri.fromFile(f), isVideo = e.isVideo, name = e.name, mimeType = e.mime,
                dateTaken = e.dateTaken, dateModified = f.lastModified(), size = f.length(),
                width = e.width, height = e.height, durationMs = e.durationMs,
                bucketId = LOCKED_ID, bucketName = "", relativePath = e.relativePath, locked = true,
            )
        }.sortedByDescending { it.dateTaken }
    }

    /**
     * Copies [item] into the locked folder. The caller then deletes the original;
     * if that's declined, [discard] removes the copy again.
     */
    suspend fun add(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        lock.withLock {
            val entries = readIndex()
            val id = -(System.currentTimeMillis() * 1000 + entries.size % 1000) - 10
            val ext = item.name.substringAfterLast('.', "").takeIf { it.length in 1..5 } ?: if (item.isVideo) "mp4" else "jpg"
            val file = File(dir, "${-id}.$ext")
            try {
                resolver.openInputStream(item.uri)!!.use { input ->
                    FileOutputStream(file).use { out ->
                        input.copyTo(out)
                        out.flush()
                        out.fd.sync()
                    }
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            val entry = Entry(
                id, file.name, item.name, item.mimeType, item.isVideo, item.dateTaken,
                item.relativePath, item.width, item.height, item.durationMs,
            )
            writeIndex(entries + entry)
            item.copy(id = id, uri = Uri.fromFile(file), locked = true, bucketId = LOCKED_ID)
        }
    }

    suspend fun discard(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val entries = readIndex()
            val (gone, kept) = entries.partition { it.id in ids }
            gone.forEach { File(dir, it.file).delete() }
            writeIndex(kept)
        }
    }

    /** Puts items back in the gallery, in their original folder when possible. Returns how many failed. */
    suspend fun restore(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        var failed = 0
        val restored = ArrayList<Long>()
        for (item in items) {
            val file = File(item.uri.path ?: continue)
            val fallback = if (item.isVideo) "Movies/Gallery/" else "Pictures/Gallery/"
            val uri = insert(item, item.relativePath, true) ?: insert(item, item.relativePath, false)
                ?: insert(item, fallback, false)
            if (uri == null) {
                failed++
                continue
            }
            try {
                resolver.openFileDescriptor(uri, "w")!!.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                        out.flush()
                        out.fd.sync()
                    }
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                restored += item.id
            } catch (_: Exception) {
                resolver.delete(uri, null, null)
                failed++
            }
        }
        discard(restored)
        failed
    }

    private fun insert(item: MediaItem, relativePath: String, withDate: Boolean): Uri? = try {
        val collection = if (item.isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            if (relativePath.isNotBlank()) put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            // Keeps the original date, so the item goes back to its place in the timeline
            if (withDate) put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        })
    } catch (_: Exception) {
        null
    }

    private fun readIndex(): List<Entry> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    o.getLong("id"), o.getString("file"), o.getString("name"), o.getString("mime"),
                    o.getBoolean("video"), o.getLong("taken"), o.optString("path"),
                    o.optInt("w"), o.optInt("h"), o.optLong("duration"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("file", e.file); put("name", e.name); put("mime", e.mime)
                put("video", e.isVideo); put("taken", e.dateTaken); put("path", e.relativePath)
                put("w", e.width); put("h", e.height); put("duration", e.durationMs)
            })
        }
        // Write then rename, so a crash can't leave a half-written index
        val tmp = File(dir, "index.json.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(arr.toString().toByteArray())
            out.fd.sync()
        }
        tmp.renameTo(indexFile)
    }

    companion object {
        private val lock = Mutex()
    }
}

/** Pseudo album ids for collections that aren't folders. */
const val LOCKED_ID = Long.MIN_VALUE
const val FAVORITES_ID = Long.MIN_VALUE + 1
const val ON_THIS_DAY_ID = Long.MIN_VALUE + 2
const val PHOTOS_ID = Long.MIN_VALUE + 3
const val VIDEOS_ID = Long.MIN_VALUE + 4
