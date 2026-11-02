package com.mephilia.gallery.ui.editor

import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.R
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.data.SaveMode
import com.mephilia.gallery.ui.components.AccessDeniedException
import com.mephilia.gallery.ui.components.rememberMediaAccess
import com.mephilia.gallery.ui.theme.Spacing
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Corner radius of the save choices. */
private val CHOICE_CORNER = 16.dp

/** MIME type some apps use for JPEG, equivalent to the standard one. */
private const val JPG_MIME = "image/jpg"

/** The steps of saving an edit. */
private sealed interface Phase {
    /** Nothing in progress. */
    data object Idle : Phase

    /** Asking whether to replace the original or save a copy. */
    data object Choosing : Phase

    /** Rendering or writing, with the [progress] from 0 to 1 when known. */
    data class Working(val progress: Float?) : Phase

    /** Saving failed, with the error [message]. */
    data class Failed(val message: String) : Phase
}

/**
 * Saves an edit once [active]: asks whether to replace the original or save a copy (unless a default is set in
 * the settings), asks for permission to change the file if needed, renders the edit, and writes it to the gallery.
 *
 * Only gallery files can be replaced; a file shared by another app can only be copied. When the output format
 * differs from the original's (e.g. a HEIC photo saved as JPEG), replacing writes a new file and deletes the
 * original.
 *
 * @param onCancel the user cancelled, declined the permission, or dismissed an error.
 * @param outputMime MIME type of the rendered file.
 * @param outputExtension file extension of the rendered file.
 * @param produce renders the edited file to a temporary file, reporting progress (0 to 1).
 * @param onSaved the edit is saved; a message has told where.
 */
@Composable
fun SaveFlow(
    item: MediaItem,
    active: Boolean,
    onCancel: () -> Unit,
    outputMime: String,
    outputExtension: String,
    produce: suspend (onProgress: (Float) -> Unit) -> File,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val writer = remember { MediaWriter(context) }
    val access = rememberMediaAccess()
    val currentProduce by rememberUpdatedState(produce)
    var phase by remember { mutableStateOf<Phase>(Phase.Idle) }
    val canReplace = item.uri.authority == MediaStore.AUTHORITY
    val sameFormat = normalizeMime(item.mimeType) == normalizeMime(outputMime)

    fun finish(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        phase = Phase.Idle
        onSaved()
    }

    fun cancel() {
        phase = Phase.Idle
        onCancel()
    }

    fun run(write: suspend (File) -> Unit) = scope.launch {
        var temp: File? = null
        try {
            phase = Phase.Working(null)
            temp = currentProduce { phase = Phase.Working(it) }
            phase = Phase.Working(null)
            write(temp)
        } catch (_: AccessDeniedException) {
            cancel()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            phase = Phase.Failed(e.readableMessage())
        } finally {
            temp?.delete()
        }
    }

    fun replace() = run { temp ->
        if (sameFormat) {
            access.write(listOf(item.uri)) { writer.overwrite(item.uri, temp) }
            MediaEvents.replaced(item.id)
            finish(context.getString(R.string.saved_replaced))
        } else {
            val name = item.name.substringBeforeLast('.', item.name) + ".$outputExtension"
            writer.insertCopy(item, temp, name, outputMime)
            val deleted = access.delete(listOf(item.uri))
            if (deleted) MediaEvents.removed(listOf(item.id))
            MediaEvents.changed()
            finish(context.getString(if (deleted) R.string.saved_replaced else R.string.saved_copy))
        }
    }

    fun copy() = run { temp ->
        writer.insertCopy(item, temp, MediaWriter.copyName(item.name, outputExtension), outputMime)
        MediaEvents.changed()
        finish(context.getString(R.string.saved_copy))
    }

    fun save(mode: SaveMode) {
        if (mode == SaveMode.Replace && canReplace) replace() else copy()
    }

    LaunchedEffect(active) {
        if (!active) {
            phase = Phase.Idle
            return@LaunchedEffect
        }
        if (phase != Phase.Idle) return@LaunchedEffect
        val mode = AppSettings.saveModeFor(item.isVideo)
        if (mode == SaveMode.Ask && canReplace) phase = Phase.Choosing else save(mode)
    }

    when (val current = phase) {
        Phase.Idle -> Unit
        Phase.Choosing -> SaveChoiceDialog(
            sameFormat = sameFormat,
            outputExtension = outputExtension,
            onCancel = onCancel,
            onChoose = { mode, remember ->
                if (remember) AppSettings.setSaveModeFor(item.isVideo, mode)
                save(mode)
            },
        )
        is Phase.Working -> ProgressDialog(stringResource(R.string.saving), current.progress)
        is Phase.Failed -> FailureDialog(stringResource(R.string.save_failed), current.message, ::cancel)
    }
}

/**
 * Asks whether to replace the original or save a copy, with a box to remember the choice.
 * A remembered choice only applies to this media type (photo or video).
 *
 * @param sameFormat whether the edit keeps the original's format; otherwise replacing converts it, which is said.
 * @param onChoose the chosen mode, and whether to remember it.
 */
@Composable
private fun SaveChoiceDialog(
    sameFormat: Boolean,
    outputExtension: String,
    onCancel: () -> Unit,
    onChoose: (SaveMode, Boolean) -> Unit,
) {
    var rememberChoice by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Rounded.Save, null) },
        title = { Text(stringResource(R.string.save_title)) },
        text = {
            Column {
                ChoiceRow(
                    Icons.Rounded.SwapHoriz,
                    stringResource(R.string.save_replace),
                    if (sameFormat) stringResource(R.string.save_replace_desc)
                    else stringResource(R.string.save_replace_convert_desc, outputExtension.uppercase()),
                ) { onChoose(SaveMode.Replace, rememberChoice) }
                Spacer(Modifier.height(Spacing.s))
                ChoiceRow(Icons.Rounded.ContentCopy, stringResource(R.string.save_copy), stringResource(R.string.save_copy_desc)) {
                    onChoose(SaveMode.Copy, rememberChoice)
                }
                Spacer(Modifier.height(Spacing.s))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Spacing.m))
                        .clickable { rememberChoice = !rememberChoice },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Column {
                        Text(stringResource(R.string.save_remember), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.save_remember_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A save choice: an [icon], a [title] and a [subtitle] explaining it. */
@Composable
private fun ChoiceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier
            .clip(RoundedCornerShape(CHOICE_CORNER))
            .clickable(onClick = onClick),
    )
}

/** [mime] in lowercase, with "image/jpg" read as the standard "image/jpeg". */
private fun normalizeMime(mime: String) = when (val lower = mime.lowercase()) {
    JPG_MIME -> JPEG_MIME
    else -> lower
}
