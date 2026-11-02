package com.mephilia.gallery.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.mephilia.gallery.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MediaWriter(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun overwrite(target: Uri, source: File) = withContext(Dispatchers.IO) {
        writeSynced(target, source, "wt")
    }

    /** Syncs to storage so a save can't leave an empty file if the phone shuts down right after. */
    private fun writeSynced(target: Uri, source: File, mode: String) {
        resolver.openFileDescriptor(target, mode)!!.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).use { out ->
                source.inputStream().use { it.copyTo(out) }
                out.flush()
                out.fd.sync()
            }
        }
    }

    suspend fun insertCopy(
        original: MediaItem,
        source: File,
        displayName: String,
        mimeType: String,
    ): Uri = withContext(Dispatchers.IO) {
        // Based on the output type: a GIF made from a video goes to the images collection
        val isVideo = mimeType.startsWith("video/")
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val fallbackDir = if (isVideo) "Movies/Gallery/" else "Pictures/Gallery/"
        val uri = tryInsert(collection, displayName, mimeType, original.relativePath)
            ?: tryInsert(collection, displayName, mimeType, fallbackDir)
            ?: error(context.getString(R.string.error_create_file))
        try {
            writeSynced(uri, source, "w")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        uri
    }

    private fun tryInsert(collection: Uri, name: String, mime: String, relativePath: String): Uri? =
        try {
            resolver.insert(collection, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (relativePath.isNotBlank()) put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })
        } catch (_: Exception) {
            null
        }

    /**
     * Moves items to [relativePath] (e.g. "Pictures/Holidays/"). Needs write access (see MediaAccess):
     * throws SecurityException without it. The number of failed items is in [lastMoveFailures].
     */
    suspend fun move(items: List<MediaItem>, relativePath: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
        lastMoveFailures = items.count { item ->
            try {
                resolver.update(item.uri, values, null, null) == 0
            } catch (e: SecurityException) {
                throw e
            } catch (_: Exception) {
                // e.g. an image into a folder that doesn't accept images (Movies/, Music/…)
                true
            }
        }
    }

    var lastMoveFailures = 0
        private set

    /** Copies items to [relativePath]. Returns the number of failures. Needs no write access. */
    suspend fun copy(items: List<MediaItem>, relativePath: String): Int = withContext(Dispatchers.IO) {
        items.count { item ->
            val isVideo = item.isVideo
            val collection = if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = tryInsert(collection, item.name, item.mimeType, relativePath) ?: return@count true
            try {
                resolver.openInputStream(item.uri)!!.use { input ->
                    resolver.openFileDescriptor(uri, "w")!!.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { out ->
                            input.copyTo(out)
                            out.flush()
                            out.fd.sync()
                        }
                    }
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                false
            } catch (_: Exception) {
                resolver.delete(uri, null, null)
                true
            }
        }
    }

    /** Renames a file, keeping its extension. Throws SecurityException without write access. */
    suspend fun rename(item: MediaItem, newBaseName: String) = withContext(Dispatchers.IO) {
        val ext = item.name.substringAfterLast('.', "")
        val name = if (ext.isEmpty()) newBaseName else "$newBaseName.$ext"
        val updated = resolver.update(
            item.uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }, null, null,
        )
        if (updated == 0) error(context.getString(R.string.error_rename))
    }

    fun tempFile(extension: String): File =
        File(context.cacheDir, "edit_${System.currentTimeMillis()}.$extension")

    companion object {
        fun copyName(original: String, extension: String): String {
            val base = original.substringBeforeLast('.', original)
            return "${base}_edit_${System.currentTimeMillis() / 1000}.$extension"
        }
    }
}
