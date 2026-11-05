package com.mephilia.gallery.ui.components

import android.content.Context
import android.widget.Toast
import androidx.annotation.PluralsRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaQueries
import com.mephilia.gallery.data.MetadataWriter
import com.mephilia.gallery.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date

/**
 * Changes the capture date of [items]. The dialog shows the date of the oldest one; with several items, "Keep the
 * time between them" moves the others by the same amount, otherwise they all get the new date. Items whose file
 * type can't be changed are counted in the final message.
 *
 * The dialog stays open while the change runs, since Android may ask the user for permission.
 *
 * @param onDone the dates were changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDateDialog(items: List<MediaItem>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val access = rememberMediaAccess()
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val initial = remember(items) { items.minOfOrNull { it.dateTaken } ?: System.currentTimeMillis() }
    var newDate by remember { mutableLongStateOf(initial) }
    var keepGaps by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf<DatePart?>(null) }
    var working by remember { mutableStateOf(false) }
    val local = Instant.ofEpochMilli(newDate).atZone(zone).toLocalDateTime()

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { Icon(Icons.Rounded.CalendarMonth, null) },
        title = { Text(stringResource(R.string.edit_date_title)) },
        text = {
            Column {
                ValueRow(Icons.Rounded.Event, DateFormat.getDateInstance(DateFormat.FULL).format(Date(newDate))) { picking = DatePart.Date }
                ValueRow(Icons.Rounded.Schedule, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(newDate))) { picking = DatePart.Time }
                if (items.size > 1) {
                    Spacer(Modifier.height(Spacing.s))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Spacing.m)).clickable { keepGaps = !keepGaps },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.edit_date_keep_gaps), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(if (keepGaps) R.string.edit_date_keep_gaps_on else R.string.edit_date_keep_gaps_off),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = keepGaps, onCheckedChange = { keepGaps = it }, modifier = Modifier.padding(start = Spacing.m))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    working = true
                    scope.launch {
                        if (changeDates(context, access, items, newDate, keepGaps)) onDone() else working = false
                    }
                },
            ) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    when (picking) {
        DatePart.Date -> {
            val state = rememberDatePickerState(initialSelectedDateMillis = local.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { utcMidnight ->
                            val day = Instant.ofEpochMilli(utcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
                            newDate = toMillis(day, local.toLocalTime(), zone)
                        }
                        picking = null
                    }) { Text(stringResource(R.string.action_ok)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } },
            ) { DatePicker(state) }
        }
        DatePart.Time -> {
            val state = rememberTimePickerState(local.hour, local.minute, android.text.format.DateFormat.is24HourFormat(context))
            AlertDialog(
                onDismissRequest = { picking = null },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        newDate = toMillis(local.toLocalDate(), LocalTime.of(state.hour, state.minute, local.second), zone)
                        picking = null
                    }) { Text(stringResource(R.string.action_ok)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        null -> Unit
    }
}

/** The part of the date being picked in its own dialog. */
private enum class DatePart { Date, Time }

/** The moment of [day] at [time] in [zone], in milliseconds since 1970. */
private fun toMillis(day: LocalDate, time: LocalTime, zone: ZoneId): Long =
    LocalDateTime.of(day, time).atZone(zone).toInstant().toEpochMilli()

/** A tappable row showing an [icon] and a [value], which opens its picker. */
@Composable
private fun ValueRow(icon: ImageVector, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Spacing.m)).clickable(onClick = onClick).padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(value, Modifier.padding(start = Spacing.m), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Asks before removing the location from [items], which can't be undone, then removes it. Only JPEG, PNG and
 * WebP photos can be changed; the others are counted in the final message.
 *
 * @param onDone the location was removed.
 */
@Composable
fun RemoveLocationDialog(items: List<MediaItem>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val access = rememberMediaAccess()
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    ConfirmDialog(
        icon = Icons.Rounded.LocationOff,
        title = pluralStringResource(R.plurals.remove_location_title, items.size, items.size),
        text = stringResource(R.string.remove_location_text),
        confirmLabel = stringResource(R.string.action_remove),
        onDismiss = { if (!working) onDismiss() },
        onConfirm = {
            if (working) return@ConfirmDialog
            working = true
            scope.launch { if (removeLocations(context, access, items)) onDone() else onDismiss() }
        },
    )
}

/**
 * Sets the capture dates of [items] (see [MediaQueries.newDates]), after the user allows it if needed, and says
 * how it went. Returns false if the user declined.
 */
private suspend fun changeDates(context: Context, access: MediaAccess, items: List<MediaItem>, newDate: Long, keepGaps: Boolean): Boolean {
    val editable = items.filter(MetadataWriter::canEditDate)
    val targets = MediaQueries.newDates(items, newDate, keepGaps)
    val writer = MetadataWriter(context)
    var failed = 0
    if (editable.isNotEmpty()) {
        try {
            access.write(editable.map { it.uri }) {
                failed = editable.count { !writer.setDateTaken(it, targets.getValue(it.id)) }
            }
        } catch (_: AccessDeniedException) {
            return false
        }
    }
    failed += items.size - editable.size
    MediaEvents.changed()
    showResult(context, R.plurals.date_changed, items.size - failed, failed)
    return true
}

/** Removes the location from [items], after the user allows it if needed, and says how it went. Returns false if the user declined. */
private suspend fun removeLocations(context: Context, access: MediaAccess, items: List<MediaItem>): Boolean {
    val editable = items.filter(MetadataWriter::canRemoveLocation)
    val writer = MetadataWriter(context)
    var failed = 0
    if (editable.isNotEmpty()) {
        try {
            access.write(editable.map { it.uri }) {
                failed = editable.count { !writer.removeLocation(it) }
            }
        } catch (_: AccessDeniedException) {
            return false
        }
    }
    failed += items.size - editable.size
    MediaEvents.changed()
    showResult(context, R.plurals.location_removed, items.size - failed, failed)
    return true
}

/** Shows how many items were changed, and how many [failed] (unsupported file types) if any. */
private fun showResult(context: Context, @PluralsRes doneMessage: Int, done: Int, failed: Int) {
    val res = context.resources
    val message = buildString {
        if (done > 0) append(res.getQuantityString(doneMessage, done, done))
        if (failed > 0) {
            if (isNotEmpty()) append(" (")
            append(res.getQuantityString(R.plurals.metadata_failed, failed, failed))
            if (done > 0) append(")")
        }
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
