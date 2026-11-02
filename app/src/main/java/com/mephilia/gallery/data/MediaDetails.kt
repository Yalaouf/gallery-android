package com.mephilia.gallery.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** Technical details read from the file (EXIF for photos, metadata for videos). Missing values are null. */
data class MediaDetails(
    val camera: String? = null,
    val lens: String? = null,
    /** e.g. "f/1.8 · 1/120 s · ISO 100 · 26 mm" */
    val exposure: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val frameRate: String? = null,
    val bitrate: String? = null,
) {
    val hasLocation get() = latitude != null && longitude != null
}

suspend fun readDetails(context: Context, item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
    runCatching { if (item.isVideo) videoDetails(context, item) else photoDetails(context, item) }.getOrDefault(MediaDetails())
}

private fun photoDetails(context: Context, item: MediaItem): MediaDetails {
    // The original is needed for the location (Android removes it otherwise)
    val uri = if (item.locked) item.uri else runCatching { MediaStore.setRequireOriginal(item.uri) }.getOrDefault(item.uri)
    val exif = (runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        ?: context.contentResolver.openInputStream(item.uri))?.use { ExifInterface(it) } ?: return MediaDetails()

    val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
    val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
    // Models often repeat the brand ("Google Pixel 8")
    val camera = when {
        model.isEmpty() -> make
        make.isEmpty() || model.startsWith(make, ignoreCase = true) -> model
        else -> "$make $model"
    }.ifBlank { null }

    val parts = buildList {
        exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { add("f/" + trim(it)) }
        exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
            add(if (it >= 1) "${trim(it)} s" else "1/${(1 / it).roundToInt()} s")
        }
        exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let { add("ISO $it") }
        val f35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
        val f = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        when {
            f35 > 0 -> add("$f35 mm")
            f > 0 -> add("${trim(f)} mm")
        }
    }
    val latLong = exif.latLong
    return MediaDetails(
        camera = camera,
        lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifBlank { null },
        exposure = parts.joinToString(" · ").ifBlank { null },
        latitude = latLong?.get(0)?.takeIf { latLong[0] != 0.0 || latLong[1] != 0.0 },
        longitude = latLong?.get(1)?.takeIf { latLong[0] != 0.0 || latLong[1] != 0.0 },
    )
}

private fun videoDetails(context: Context, item: MediaItem): MediaDetails {
    val uri = if (item.locked) item.uri else runCatching { MediaStore.setRequireOriginal(item.uri) }.getOrDefault(item.uri)
    MediaMetadataRetriever().use { r ->
        runCatching { r.setDataSource(context, uri) }.onFailure { r.setDataSource(context, item.uri) }
        // ISO 6709, e.g. "+48.8577+002.2950/"
        val location = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { s ->
            Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(s)?.destructured?.let { (la, lo) ->
                la.toDoubleOrNull() to lo.toDoubleOrNull()
            }
        }
        val fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
            ?: run {
                val frames = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull()
                val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toFloatOrNull()
                if (frames != null && ms != null && ms > 0) frames * 1000 / ms else null
            }
        val bitrate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
        return MediaDetails(
            latitude = location?.first,
            longitude = location?.second,
            frameRate = fps?.takeIf { it > 0 }?.let { "${it.roundToInt()} fps" },
            bitrate = bitrate?.takeIf { it > 0 }?.let { String.format(Locale.ROOT, "%.1f Mb/s", it / 1_000_000.0) },
        )
    }
}

private fun trim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v)
