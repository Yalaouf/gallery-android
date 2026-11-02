package com.mephilia.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** MediaStore keeps most dates in seconds; the app works in milliseconds. */
private const val MILLIS_PER_SECOND = 1000L

/** Name of a received file whose URI tells none. */
private const val DEFAULT_NAME = "media"

/** Ending of a wildcard MIME type (any image, any video), which tells nothing precise. */
private const val WILDCARD_SUFFIX = "*"

/** Wildcard MIME type of an image whose type is missing. */
private const val ANY_IMAGE = "image/*"

/** Wildcard MIME type of a video whose type is missing. */
private const val ANY_VIDEO = "video/*"

/** The columns read for each photo or video of the gallery. */
private val GALLERY_COLUMNS = arrayOf(
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
    MediaStore.MediaColumns.DATE_EXPIRES,
    MediaStore.MediaColumns.IS_FAVORITE,
)

/** The columns read for a file received from another app that is in MediaStore. */
private val RECEIVED_COLUMNS = arrayOf(
    MediaStore.MediaColumns._ID,
    MediaStore.MediaColumns.DISPLAY_NAME,
    MediaStore.MediaColumns.SIZE,
    MediaStore.MediaColumns.DATE_TAKEN,
    MediaStore.MediaColumns.DATE_MODIFIED,
    MediaStore.MediaColumns.RELATIVE_PATH,
    MediaStore.MediaColumns.BUCKET_ID,
    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
)

/** Reads the photos and videos of the shared storage from MediaStore, and watches it for changes. */
class MediaRepository(context: Context) {
    /** Queries MediaStore. */
    private val resolver: ContentResolver = context.contentResolver

    /** Emits each time something changes in the shared storage, whichever app made the change. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /**
     * Describes a file received from another app (screenshot, file manager…), or null if its type is unknown.
     * MediaStore files keep their folder so they can be replaced; other files can only be saved as a copy.
     *
     * @param typeHint the type given with the file, used unless it ends with a wildcard.
     */
    suspend fun resolve(uri: Uri, typeHint: String?): MediaItem? = withContext(Dispatchers.IO) {
        val mime = typeHint?.takeIf { '/' in it && !it.endsWith(WILDCARD_SUFFIX) }
            ?: runCatching { resolver.getType(uri) }.getOrNull()
            ?: return@withContext null
        val now = System.currentTimeMillis()
        val item = MediaItem(
            id = uri.hashCode().toLong(), uri = uri, isVideo = isVideoMime(mime),
            name = uri.lastPathSegment ?: DEFAULT_NAME, mimeType = mime,
            dateTaken = now, dateModified = now, size = 0, width = 0, height = 0, durationMs = 0,
            bucketId = 0, bucketName = "", relativePath = "",
        )
        runCatching { if (uri.authority == MediaStore.AUTHORITY) withMediaStoreDetails(item) else withOpenableDetails(item) }
            .getOrNull() ?: item
    }

    /** [item] completed with what MediaStore knows about it, or [item] as it is if MediaStore has nothing. */
    private fun withMediaStoreDetails(item: MediaItem): MediaItem =
        resolver.query(item.uri, RECEIVED_COLUMNS, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use item
            val modified = cursor.long(MediaStore.MediaColumns.DATE_MODIFIED) * MILLIS_PER_SECOND
            item.copy(
                id = cursor.long(MediaStore.MediaColumns._ID),
                name = cursor.stringOrNull(MediaStore.MediaColumns.DISPLAY_NAME) ?: item.name,
                size = cursor.long(MediaStore.MediaColumns.SIZE),
                dateTaken = cursor.long(MediaStore.MediaColumns.DATE_TAKEN).takeIf { it > 0 } ?: modified,
                dateModified = modified,
                relativePath = cursor.stringOrNull(MediaStore.MediaColumns.RELATIVE_PATH).orEmpty(),
                bucketId = cursor.long(MediaStore.MediaColumns.BUCKET_ID),
                bucketName = cursor.stringOrNull(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME).orEmpty(),
            )
        } ?: item

    /** [item] completed with the name and size any shared file tells. */
    private fun withOpenableDetails(item: MediaItem): MediaItem =
        resolver.query(item.uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use item
            item.copy(
                name = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME) ?: item.name,
                size = cursor.long(OpenableColumns.SIZE),
            )
        } ?: item

    /** Every photo and video outside the trash, most recently changed first. */
    suspend fun loadAll(): List<MediaItem> = load(trashed = false)

    /** The items in Android's trash, soonest to expire first. */
    suspend fun loadTrash(): List<MediaItem> = load(trashed = true).sortedBy { it.expiresAt }

    /** The photos and videos in the trash if [trashed], otherwise those outside it. */
    private suspend fun load(trashed: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)")
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                ),
            )
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
        }
        val items = ArrayList<MediaItem>()
        resolver.query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), GALLERY_COLUMNS, queryArgs, null)?.use { cursor ->
            val columns = GalleryColumns(cursor)
            while (cursor.moveToNext()) items += columns.read(cursor)
        }
        items
    }

    /** The positions of [GALLERY_COLUMNS] in a cursor, looked up once for the whole query. */
    private class GalleryColumns(cursor: Cursor) {
        private val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        private val mediaType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        private val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        private val mime = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        private val taken = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
        private val added = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        private val modified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        private val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        private val width = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        private val height = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        private val duration = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
        private val bucket = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
        private val bucketName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        private val path = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        private val expires = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
        private val favorite = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)

        /**
         * The item at the cursor's position. Without a capture date, the date added stands in (then the last
         * change); without a folder name, the last part of the path does.
         */
        fun read(cursor: Cursor): MediaItem {
            val itemId = cursor.getLong(id)
            val isVideo = cursor.getInt(mediaType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val modifiedMs = cursor.getLong(modified) * MILLIS_PER_SECOND
            val addedMs = cursor.getLong(added) * MILLIS_PER_SECOND
            val relativePath = cursor.getString(path)
            return MediaItem(
                id = itemId,
                uri = ContentUris.withAppendedId(base, itemId),
                isVideo = isVideo,
                name = cursor.getString(name).orEmpty(),
                mimeType = cursor.getString(mime) ?: if (isVideo) ANY_VIDEO else ANY_IMAGE,
                dateTaken = cursor.getLong(taken).takeIf { it > 0 } ?: addedMs.takeIf { it > 0 } ?: modifiedMs,
                dateModified = modifiedMs,
                size = cursor.getLong(size),
                width = cursor.getInt(width),
                height = cursor.getInt(height),
                durationMs = cursor.getLong(duration),
                bucketId = cursor.getLong(bucket),
                bucketName = cursor.getString(bucketName) ?: relativePath?.trimEnd('/')?.substringAfterLast('/').orEmpty(),
                relativePath = relativePath.orEmpty(),
                expiresAt = cursor.getLong(expires) * MILLIS_PER_SECOND,
                favorite = cursor.getInt(favorite) == 1,
            )
        }
    }
}

/** The long in the column [name]. */
private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))

/** The string in the column [name], or null. */
private fun Cursor.stringOrNull(name: String): String? = getString(getColumnIndexOrThrow(name))
