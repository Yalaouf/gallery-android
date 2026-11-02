package com.mephilia.gallery.ui.screens.viewer

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaDetails
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.readDetails
import com.mephilia.gallery.ui.components.formatDuration
import com.mephilia.gallery.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Vertical padding around each row of details. */
private val INFO_ROW_PADDING = 6.dp

/** Format of the coordinates shown in the details: latitude, longitude, to about one meter. */
private const val COORDINATES_FORMAT = "%.5f, %.5f"

/**
 * Bottom sheet with the details of [item]: name, date, folder, size, type, then what its metadata tells
 * (camera, lens, exposure, frame rate, bitrate, location with a button to open it in a maps app).
 * The metadata is read in the background and shows up once ready.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaInfoSheet(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val details by produceState<MediaDetails?>(null, item.id, item.dateModified) {
        value = readDetails(context, item)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.xl).navigationBarsPadding().padding(bottom = Spacing.xl)) {
            Text(stringResource(R.string.details), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.l))
            InfoRow(stringResource(R.string.info_name), item.name)
            InfoRow(
                stringResource(R.string.info_date),
                DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(item.dateTaken)),
            )
            InfoRow(stringResource(R.string.info_folder), item.relativePath.ifBlank { item.bucketName })
            if (item.width > 0) InfoRow(stringResource(R.string.info_resolution), "${item.width} × ${item.height}")
            InfoRow(stringResource(R.string.info_size), Formatter.formatFileSize(context, item.size))
            if (item.isVideo) InfoRow(stringResource(R.string.info_duration), formatDuration(item.durationMs))
            InfoRow(stringResource(R.string.info_type), item.mimeType)
            details?.let { MetadataRows(it) }
        }
    }
}

/** The rows for what the file's metadata tells; each one only shows if that information is known. */
@Composable
private fun MetadataRows(details: MediaDetails) {
    val context = LocalContext.current
    details.camera?.let { InfoRow(stringResource(R.string.info_camera), it) }
    details.lens?.let { InfoRow(stringResource(R.string.info_lens), it) }
    details.exposure?.let { InfoRow(stringResource(R.string.info_exposure), it) }
    details.frameRate?.let { InfoRow(stringResource(R.string.info_frame_rate), it) }
    details.bitrate?.let { InfoRow(stringResource(R.string.info_bitrate), it) }
    val latitude = details.latitude
    val longitude = details.longitude
    if (details.hasLocation && latitude != null && longitude != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                InfoRow(stringResource(R.string.info_location), String.format(Locale.ROOT, COORDINATES_FORMAT, latitude, longitude))
            }
            TextButton(onClick = { openInMaps(context, latitude, longitude) }) {
                Text(stringResource(R.string.open_in_maps))
            }
        }
    }
}

/** A detail: its [label] in the accent color, above its [value]. */
@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(vertical = INFO_ROW_PADDING)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
