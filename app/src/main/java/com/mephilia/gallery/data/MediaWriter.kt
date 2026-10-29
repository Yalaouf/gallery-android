package com.mephilia.gallery.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.mephilia.gallery.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Écrit un fichier temporaire édité dans MediaStore : soit par-dessus l'original
 * (nécessite une autorisation obtenue via [MediaStore.createWriteRequest]),
 * soit comme nouvelle copie dans le même dossier.
 */
class MediaWriter(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun overwrite(target: Uri, source: File) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(target, "wt")!!.use { out ->
            source.inputStream().use { it.copyTo(out) }
        }
    }

    suspend fun insertCopy(
        original: MediaItem,
        source: File,
        displayName: String,
        mimeType: String,
    ): Uri = withContext(Dispatchers.IO) {
        val collection = if (original.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val fallbackDir = if (original.isVideo) "Movies/Gallery/" else "Pictures/Gallery/"
        val uri = tryInsert(collection, displayName, mimeType, original.relativePath)
            ?: tryInsert(collection, displayName, mimeType, fallbackDir)
            ?: error(context.getString(R.string.error_create_file))
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
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
     * Déplace les éléments vers [relativePath] (ex. "Pictures/Vacances/") en changeant leur
     * chemin dans MediaStore. Nécessite le droit d'écriture (voir MediaAccess) : sans lui,
     * lève une SecurityException. Le nombre d'échecs est dans [lastMoveFailures].
     */
    suspend fun move(items: List<MediaItem>, relativePath: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
        lastMoveFailures = items.count { item ->
            try {
                resolver.update(item.uri, values, null, null) == 0
            } catch (e: SecurityException) {
                throw e
            } catch (_: Exception) {
                // Ex. une image vers un dossier non autorisé pour les images (Movies/, Music/…)
                true
            }
        }
    }

    var lastMoveFailures = 0
        private set

    fun tempFile(extension: String): File =
        File(context.cacheDir, "edit_${System.currentTimeMillis()}.$extension")

    companion object {
        fun copyName(original: String, extension: String): String {
            val base = original.substringBeforeLast('.', original)
            return "${base}_edit_${System.currentTimeMillis() / 1000}.$extension"
        }
    }
}
