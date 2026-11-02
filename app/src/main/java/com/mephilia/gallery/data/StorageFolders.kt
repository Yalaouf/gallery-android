package com.mephilia.gallery.data

/** The standard folders of the shared storage the app writes to, as MediaStore relative paths. */
object StorageFolders {
    /** Top-level folder for pictures; it also accepts videos, so new albums go there. */
    const val PICTURES = "Pictures/"

    /** Top-level folder for videos; it doesn't accept pictures. */
    const val MOVIES = "Movies/"

    /** Name of the app's own subfolder, used when an item can't go back to its original folder. */
    private const val APP_FOLDER = "Gallery/"

    /** Fallback folder for pictures the app writes. */
    const val APP_PICTURES = PICTURES + APP_FOLDER

    /** Fallback folder for videos the app writes. */
    const val APP_MOVIES = MOVIES + APP_FOLDER

    /** The app's fallback folder for a picture, or for a video if [isVideo]. */
    fun appFolder(isVideo: Boolean) = if (isVideo) APP_MOVIES else APP_PICTURES
}
