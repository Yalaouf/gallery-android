package com.mephilia.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class MediaRepository(context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    /** Émet à chaque changement dans MediaStore. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val items = ArrayList<MediaItem>()
        resolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection, selection, args,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(projection[0])
            val typeCol = c.getColumnIndexOrThrow(projection[1])
            val nameCol = c.getColumnIndexOrThrow(projection[2])
            val mimeCol = c.getColumnIndexOrThrow(projection[3])
            val takenCol = c.getColumnIndexOrThrow(projection[4])
            val addedCol = c.getColumnIndexOrThrow(projection[5])
            val modCol = c.getColumnIndexOrThrow(projection[6])
            val sizeCol = c.getColumnIndexOrThrow(projection[7])
            val wCol = c.getColumnIndexOrThrow(projection[8])
            val hCol = c.getColumnIndexOrThrow(projection[9])
            val durCol = c.getColumnIndexOrThrow(projection[10])
            val bucketCol = c.getColumnIndexOrThrow(projection[11])
            val bucketNameCol = c.getColumnIndexOrThrow(projection[12])
            val pathCol = c.getColumnIndexOrThrow(projection[13])
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val modified = c.getLong(modCol) * 1000
                val added = c.getLong(addedCol) * 1000
                val taken = c.getLong(takenCol).takeIf { it > 0 } ?: added.takeIf { it > 0 } ?: modified
                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    isVideo = isVideo,
                    name = c.getString(nameCol) ?: "",
                    mimeType = c.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*",
                    dateTaken = taken,
                    dateModified = modified,
                    size = c.getLong(sizeCol),
                    width = c.getInt(wCol),
                    height = c.getInt(hCol),
                    durationMs = c.getLong(durCol),
                    bucketId = c.getLong(bucketCol),
                    bucketName = c.getString(bucketNameCol) ?: "Stockage",
                    relativePath = c.getString(pathCol) ?: "",
                )
            }
        }
        items
    }
}
