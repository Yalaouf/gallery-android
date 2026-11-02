package com.mephilia.gallery.data

import android.net.Uri
import androidx.annotation.StringRes
import com.mephilia.gallery.R

/**
 * A photo or video.
 *
 * @param id MediaStore id; negative for items of the locked folder.
 * @param dateTaken capture date in milliseconds, or the date added when unknown.
 * @param dateModified last change in milliseconds; part of the image cache keys, so an edited file is reloaded.
 * @param size file size in bytes.
 * @param durationMs length of a video; 0 for a photo.
 * @param bucketId id of the folder.
 * @param bucketName name of the folder.
 * @param relativePath path of the folder, e.g. "DCIM/Camera/".
 * @param expiresAt for trashed items, when Android deletes them for good (milliseconds); 0 otherwise.
 * @param locked stored in the app's locked folder (private storage, not in MediaStore).
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val mimeType: String,
    val dateTaken: Long,
    val dateModified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val expiresAt: Long = 0,
    val favorite: Boolean = false,
    val locked: Boolean = false,
)

/**
 * A folder holding photos or videos.
 *
 * @param cover the most recent item, shown on the album's card.
 * @param count how many items it holds.
 * @param lastModified the latest change among its items, in milliseconds.
 * @param created the date of its oldest item, as an approximation of its creation date.
 */
data class Album(
    val id: Long,
    val name: String,
    val relativePath: String,
    val cover: MediaItem,
    val count: Int,
    val lastModified: Long,
    val created: Long,
)

/** How the photos are sorted, with the label shown in the sort options. */
enum class MediaSort(@StringRes val label: Int) {
    DateDesc(R.string.sort_newest),
    DateAsc(R.string.sort_oldest),
    Random(R.string.sort_random),
    Name(R.string.sort_name),
    Size(R.string.sort_size),
}

/** Which items the photos tab shows, with the label shown in the options. */
enum class MediaFilter(@StringRes val label: Int) {
    All(R.string.filter_all),
    Photos(R.string.filter_photos),
    Videos(R.string.filter_videos),
}

/** How the albums are sorted, with the label shown in the sort options. */
enum class AlbumSort(@StringRes val label: Int) {
    Modified(R.string.album_sort_modified),
    Created(R.string.album_sort_created),
    Name(R.string.album_sort_name),
}

/**
 * What saving an edit does, with its label in the settings and on the editor's save button.
 * [Ask] asks each time whether to replace the original or save a copy.
 */
enum class SaveMode(@StringRes val label: Int, @StringRes val buttonLabel: Int) {
    Ask(R.string.save_mode_ask, R.string.save_button_ask),
    Replace(R.string.save_mode_replace, R.string.save_button_replace),
    Copy(R.string.save_mode_copy, R.string.save_button_copy),
}
