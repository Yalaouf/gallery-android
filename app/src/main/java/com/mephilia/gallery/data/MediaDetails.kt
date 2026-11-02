package com.mephilia.gallery.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** Milliseconds in a second. */
private const val MILLIS_PER_SECOND = 1000f

/** Bits per second in a megabit per second. */
private const val BITS_PER_MEGABIT = 1_000_000.0

/** Separator between the parts of the exposure. */
private const val EXPOSURE_SEPARATOR = " · "

/** Latitude then longitude in an ISO 6709 string, each a signed decimal number. */
private val ISO_6709 = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)")

/**
 * Technical details read from a file: EXIF for photos, metadata for videos. Missing values are null.
 *
 * @param exposure aperture, shutter speed, ISO and focal length, e.g. "f/1.8 · 1/120 s · ISO 100 · 26 mm".
 * @param frameRate e.g. "30 fps".
 * @param bitrate e.g. "12.3 Mb/s".
 */
data class MediaDetails(
    val camera: String? = null,
    val lens: String? = null,
    val exposure: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val frameRate: String? = null,
    val bitrate: String? = null,
) {
    /** Whether the location is known. */
    val hasLocation get() = latitude != null && longitude != null
}

/** Reads the details of [item]; nothing is known if the file can't be read. */
suspend fun readDetails(context: Context, item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
    runCatching { if (item.isVideo) videoDetails(context, item) else photoDetails(context, item) }.getOrDefault(MediaDetails())
}

/**
 * The URI to read [item]'s metadata from: the original file, since Android removes the location from what
 * apps read otherwise. Locked items are private files, read as they are.
 */
private fun originalUri(item: MediaItem): Uri =
    if (item.locked) item.uri else runCatching { MediaStore.setRequireOriginal(item.uri) }.getOrDefault(item.uri)

/** Camera, lens, exposure and location of a photo, from its EXIF data. */
private fun photoDetails(context: Context, item: MediaItem): MediaDetails {
    val resolver = context.contentResolver
    val exif = (runCatching { resolver.openInputStream(originalUri(item)) }.getOrNull() ?: resolver.openInputStream(item.uri))
        ?.use { ExifInterface(it) } ?: return MediaDetails()
    val latLong = exif.latLong?.takeIf { (latitude, longitude) -> latitude != 0.0 || longitude != 0.0 }
    return MediaDetails(
        camera = cameraName(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL)),
        lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifBlank { null },
        exposure = formatExposure(
            fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0),
            exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0),
            iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0),
            focal35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0),
            focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0),
        ),
        latitude = latLong?.get(0),
        longitude = latLong?.get(1),
    )
}

/**
 * The camera's name from its [make] and [model]. Models often repeat the brand ("Google" "Google Pixel 8"),
 * in which case the model alone is used. Null if neither is known.
 */
private fun cameraName(make: String?, model: String?): String? {
    val brand = make?.trim().orEmpty()
    val name = model?.trim().orEmpty()
    return when {
        name.isEmpty() -> brand
        brand.isEmpty() || name.startsWith(brand, ignoreCase = true) -> name
        else -> "$brand $name"
    }.ifBlank { null }
}

/** Location, frame rate and bitrate of a video, from its metadata. */
private fun videoDetails(context: Context, item: MediaItem): MediaDetails {
    MediaMetadataRetriever().use { retriever ->
        runCatching { retriever.setDataSource(context, originalUri(item)) }.onFailure { retriever.setDataSource(context, item.uri) }
        fun metadata(key: Int) = retriever.extractMetadata(key)
        val location = metadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let(::parseIso6709)
        val fps = metadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: run {
            val frames = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull()
            val durationMs = metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toFloatOrNull()
            if (frames != null && durationMs != null && durationMs > 0) frames * MILLIS_PER_SECOND / durationMs else null
        }
        val bitrate = metadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
        return MediaDetails(
            latitude = location?.first,
            longitude = location?.second,
            frameRate = fps?.takeIf { it > 0 }?.let { "${it.roundToInt()} fps" },
            bitrate = bitrate?.takeIf { it > 0 }?.let { String.format(Locale.ROOT, "%.1f Mb/s", it / BITS_PER_MEGABIT) },
        )
    }
}

/**
 * The exposure as shown in the details, e.g. "f/1.8 · 1/120 s · ISO 100 · 26 mm", skipping unknown (zero) values;
 * null if nothing is known. The 35 mm equivalent focal length is preferred, since it's what people compare.
 */
internal fun formatExposure(fNumber: Double, exposureTime: Double, iso: Int, focal35: Int, focal: Double): String? =
    buildList {
        if (fNumber > 0) add("f/" + trimDecimals(fNumber))
        if (exposureTime > 0) add(if (exposureTime >= 1) "${trimDecimals(exposureTime)} s" else "1/${(1 / exposureTime).roundToInt()} s")
        if (iso > 0) add("ISO $iso")
        when {
            focal35 > 0 -> add("$focal35 mm")
            focal > 0 -> add("${trimDecimals(focal)} mm")
        }
    }.joinToString(EXPOSURE_SEPARATOR).ifBlank { null }

/** Latitude and longitude from an ISO 6709 string as stored in videos, e.g. "+48.8577+002.2950/". */
internal fun parseIso6709(s: String): Pair<Double, Double>? =
    ISO_6709.find(s)?.destructured?.let { (latitude, longitude) ->
        (latitude.toDoubleOrNull() ?: return null) to (longitude.toDoubleOrNull() ?: return null)
    }

/** [value] without decimals if it's whole, otherwise with one. */
private fun trimDecimals(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.ROOT, "%.1f", value)
