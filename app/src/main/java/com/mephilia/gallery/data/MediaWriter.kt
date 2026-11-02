package com.mephilia.gallery.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.mephilia.gallery.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Milliseconds in a second. */
private const val MILLIS_PER_SECOND = 1000

/**
 * Writes to the shared storage through MediaStore: replaces files with their edited version, adds copies,
 * moves, copies and renames items. Everything runs on the IO dispatcher.
 */
class MediaWriter(private val context: Context) {
    /** Writes through MediaStore. */
    private val resolver = context.contentResolver

    /** How many items the last [move] couldn't move. */
    var lastMoveFailures = 0
        private set

    /** Replaces the content of [target] with [source]. Needs write access (see MediaAccess). */
    suspend fun overwrite(target: Uri, source: File) = withContext(Dispatchers.IO) {
        source.inputStream().use { resolver.writeSynced(target, MODE_WRITE_TRUNCATE, it) }
    }

    /**
     * Adds [source] as a new file named [displayName], in the folder of [original] if it accepts the type,
     * otherwise in the app's own folder. The collection follows the output type: a GIF made from a video
     * goes to the images.
     *
     * @throws IllegalStateException if the file can't be created.
     */
    suspend fun insertCopy(original: MediaItem, source: File, displayName: String, mimeType: String): Uri =
        withContext(Dispatchers.IO) {
            val isVideo = isVideoMime(mimeType)
            val collection = mediaCollection(isVideo)
            val uri = resolver.insertPending(collection, displayName, mimeType, original.relativePath)
                ?: resolver.insertPending(collection, displayName, mimeType, StorageFolders.appFolder(isVideo))
                ?: error(context.getString(R.string.error_create_file))
            try {
                source.inputStream().use { resolver.writeSynced(uri, MODE_WRITE, it) }
                resolver.publish(uri)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            uri
        }

    /**
     * Moves [items] to the folder [relativePath] (e.g. "Pictures/Holidays/"). Items the folder refuses
     * (an image into Movies/ or Music/…) are counted in [lastMoveFailures].
     *
     * @throws SecurityException without write access (see MediaAccess).
     */
    suspend fun move(items: List<MediaItem>, relativePath: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
        lastMoveFailures = items.count { item ->
            try {
                resolver.update(item.uri, values, null, null) == 0
            } catch (e: SecurityException) {
                throw e
            } catch (_: Exception) {
                true
            }
        }
    }

    /** Copies [items] to the folder [relativePath]. Needs no write access. Returns how many failed. */
    suspend fun copy(items: List<MediaItem>, relativePath: String): Int = withContext(Dispatchers.IO) {
        items.count { item ->
            val uri = resolver.insertPending(mediaCollection(item.isVideo), item.name, item.mimeType, relativePath)
                ?: return@count true
            try {
                checkNotNull(resolver.openInputStream(item.uri)).use { resolver.writeSynced(uri, MODE_WRITE, it) }
                resolver.publish(uri)
                false
            } catch (_: Exception) {
                resolver.delete(uri, null, null)
                true
            }
        }
    }

    /**
     * Renames [item] to [newBaseName], keeping its extension.
     *
     * @throws SecurityException without write access (see MediaAccess).
     * @throws IllegalStateException if MediaStore refused the new name.
     */
    suspend fun rename(item: MediaItem, newBaseName: String) = withContext(Dispatchers.IO) {
        val extension = item.name.substringAfterLast('.', "")
        val name = if (extension.isEmpty()) newBaseName else "$newBaseName.$extension"
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
        if (resolver.update(item.uri, values, null, null) == 0) error(context.getString(R.string.error_rename))
    }

    /** A new temporary file in the cache, for rendering an edit before it's saved. */
    fun tempFile(extension: String): File = File(context.cacheDir, "edit_${System.currentTimeMillis()}.$extension")

    companion object {
        /** The name of an edited copy of [original]: its name with "_edit_" and the time, and the new [extension]. */
        fun copyName(original: String, extension: String): String {
            val base = original.substringBeforeLast('.', original)
            return "${base}_edit_${System.currentTimeMillis() / MILLIS_PER_SECOND}.$extension"
        }
    }
}
