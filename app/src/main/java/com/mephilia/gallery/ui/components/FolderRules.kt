package com.mephilia.gallery.ui.components

import androidx.annotation.StringRes
import com.mephilia.gallery.R
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.MediaItem
import java.text.Normalizer

/** Characters that aren't allowed in a file or folder name on Android's shared storage. */
private const val FORBIDDEN_NAME_CHARACTERS = "/\\:*?\"<>|"

/** Names starting with this are hidden files, which galleries don't show. */
private const val HIDDEN_NAME_PREFIX = "."

/** Combining marks (accents) left as separate characters by the NFD normalization. */
private val COMBINING_MARKS = Regex("\\p{M}+")

/** Top-level folders where MediaStore only accepts audio files. */
private val AUDIO_FOLDERS = setOf("Music", "Podcasts", "Ringtones", "Alarms", "Notifications", "Audiobooks", "Recordings")

/** Top-level folder where MediaStore only accepts videos. */
private const val VIDEO_ONLY_FOLDER = "Movies"

/** Folder new albums are created in: it accepts both images and videos. */
const val NEW_FOLDER_PARENT = "Pictures/"

/** Lowercase without accents, so that searching "tele" matches "Télé". */
internal fun normalizeForSearch(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

/** Whether [name] can be used as a file or folder name: not blank, no forbidden character, and not hidden. */
internal fun isValidFolderName(name: String) =
    name.isNotBlank() && name.none { it in FORBIDDEN_NAME_CHARACTERS } && !name.startsWith(HIDDEN_NAME_PREFIX)

/** The relative path of a new folder called [name], created in [NEW_FOLDER_PARENT]. */
internal fun newFolderPath(name: String) = "$NEW_FOLDER_PARENT${name.trim()}/"

/** The display name of a folder from its relative path, e.g. "Pictures/Trips/" → "Trips". */
internal fun folderName(relativePath: String) = relativePath.trimEnd('/').substringAfterLast('/')

/**
 * The relative path of the folder at [relativePath] once renamed to [newName], next to the old one
 * (e.g. "DCIM/Camera/" → "DCIM/Holidays/"), or null if it can't be renamed: top-level folders such as
 * "Pictures/" are Android's own.
 */
internal fun renamedFolderPath(relativePath: String, newName: String): String? {
    val parent = relativePath.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")
    if (parent.isEmpty()) return null
    return "$parent/${newName.trim()}/"
}

/**
 * Whether a folder at [relativePath] already exists among [albums]. Shared storage ignores case,
 * so "Trips" and "trips" are the same folder.
 */
internal fun isFolderTaken(relativePath: String, albums: List<Album>) =
    albums.any { it.relativePath.equals(relativePath, ignoreCase = true) }

/**
 * Why [items] can't go to the folder at [relativePath], as a string resource, or null if they can.
 * MediaStore only accepts images in some top-level folders (not Movies/ nor audio folders),
 * and videos anywhere but in audio folders.
 */
@StringRes
internal fun refusalReason(relativePath: String, items: List<MediaItem>): Int? {
    val top = relativePath.substringBefore('/')
    return when {
        top in AUDIO_FOLDERS -> R.string.folder_audio_only
        top == VIDEO_ONLY_FOLDER && items.any { !it.isVideo } -> R.string.folder_no_photos
        else -> null
    }
}
