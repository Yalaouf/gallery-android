package com.mephilia.gallery.ui.screens.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import java.util.Locale

/** Extra read by some apps handling "set as" to know the type of the picture. */
private const val EXTRA_MIME_TYPE = "mimeType"

/** Geo URI of a place, marked with a pin: latitude and longitude, to about ten centimeters. */
private const val GEO_URI_FORMAT = "geo:%.6f,%.6f?q=%.6f,%.6f"

/** Offers [item] as wallpaper, contact picture… through the apps that support it. */
internal fun setAs(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(item.uri, item.mimeType)
        putExtra(EXTRA_MIME_TYPE, item.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_set_as))) }
}

/**
 * Opens a maps app at the given place, or says that none is installed.
 * Only the coordinates are handed over: Gallery itself never goes online.
 */
internal fun openInMaps(context: Context, latitude: Double, longitude: Double) {
    val geo = String.format(Locale.ROOT, GEO_URI_FORMAT, latitude, longitude, latitude, longitude)
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geo))) }.onFailure {
        Toast.makeText(context, context.getString(R.string.no_maps_app), Toast.LENGTH_SHORT).show()
    }
}
