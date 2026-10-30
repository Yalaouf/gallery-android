package com.mephilia.gallery.data

import android.net.Uri
import androidx.annotation.StringRes
import com.mephilia.gallery.R

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val mimeType: String,
    /** Capture date (ms), or the date added when unknown. */
    val dateTaken: Long,
    val dateModified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
)

data class Album(
    val id: Long,
    val name: String,
    val relativePath: String,
    val cover: MediaItem,
    val count: Int,
    val lastModified: Long,
    /** Oldest item in the folder, used as an approximation of its creation date. */
    val created: Long,
)

enum class MediaSort(@StringRes val label: Int) {
    DateDesc(R.string.sort_newest),
    DateAsc(R.string.sort_oldest),
    Random(R.string.sort_random),
    Name(R.string.sort_name),
    Size(R.string.sort_size),
}

enum class MediaFilter(@StringRes val label: Int) {
    All(R.string.filter_all),
    Photos(R.string.filter_photos),
    Videos(R.string.filter_videos),
}

enum class AlbumSort(@StringRes val label: Int) {
    Modified(R.string.album_sort_modified),
    Created(R.string.album_sort_created),
    Name(R.string.album_sort_name),
}

enum class SaveMode(@StringRes val label: Int, @StringRes val buttonLabel: Int) {
    Ask(R.string.save_mode_ask, R.string.save_button_ask),
    Replace(R.string.save_mode_replace, R.string.save_button_replace),
    Copy(R.string.save_mode_copy, R.string.save_button_copy),
}
