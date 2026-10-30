package com.mephilia.gallery.ui.editor

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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.SaveMode
import com.mephilia.gallery.ui.components.AccessDeniedException
import com.mephilia.gallery.ui.components.rememberMediaAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R
import android.provider.MediaStore

private sealed interface Phase {
    data object Idle : Phase
    data object Choosing : Phase
    data class Working(val progress: Float?) : Phase
    data class Failed(val message: String) : Phase
}

/**
 * Saves an edit: replace-or-copy choice (or the saved default), permission request,
 * rendering and writing to MediaStore.
 *
 * @param produce renders the edited file to a temporary file, reporting progress (0..1).
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
    val produceFn by rememberUpdatedState(produce)
    var phase by remember { mutableStateOf<Phase>(Phase.Idle) }
    var rememberChoice by remember { mutableStateOf(false) }

    // Only MediaStore files can be replaced; a file shared by another app can only be copied
    val canReplace = item.uri.authority == MediaStore.AUTHORITY

    val sameFormat = normalizeMime(item.mimeType) == normalizeMime(outputMime)

    fun finish(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        phase = Phase.Idle
        onSaved()
    }

    fun run(block: suspend (File) -> Unit) {
        scope.launch {
            var temp: File? = null
            try {
                phase = Phase.Working(null)
                temp = produceFn { p -> phase = Phase.Working(p) }
                phase = Phase.Working(null)
                block(temp)
            } catch (_: AccessDeniedException) {
                phase = Phase.Idle
                onCancel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                phase = Phase.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                temp?.delete()
            }
        }
    }

    fun replace() = run { temp ->
        if (sameFormat) {
            access.write(listOf(item.uri)) { writer.overwrite(item.uri, temp) }
            finish(context.getString(R.string.saved_replaced))
        } else {
            // Format we can't re-encode (e.g. HEIC): write a new file, then delete the original
            val name = item.name.substringBeforeLast('.', item.name) + ".$outputExtension"
            writer.insertCopy(item, temp, name, outputMime)
            finish(context.getString(if (access.delete(listOf(item.uri))) R.string.saved_replaced else R.string.saved_copy))
        }
    }

    fun copy() = run { temp ->
        writer.insertCopy(item, temp, MediaWriter.copyName(item.name, outputExtension), outputMime)
        finish(context.getString(R.string.saved_copy))
    }

    LaunchedEffect(active) {
        if (!active) {
            phase = Phase.Idle
            return@LaunchedEffect
        }
        if (phase != Phase.Idle) return@LaunchedEffect
        when (AppSettings.saveModeFor(item.isVideo)) {
            SaveMode.Ask -> if (canReplace) phase = Phase.Choosing else copy()
            SaveMode.Replace -> if (canReplace) replace() else copy()
            SaveMode.Copy -> copy()
        }
    }

    fun choose(mode: SaveMode) {
        // The remembered choice only applies to this media type (photo or video)
        if (rememberChoice) AppSettings.setSaveModeFor(item.isVideo, mode)
        if (mode == SaveMode.Replace) replace() else copy()
    }

    when (val p = phase) {
        Phase.Idle -> Unit
        Phase.Choosing -> AlertDialog(
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
                    ) { choose(SaveMode.Replace) }
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow(Icons.Rounded.ContentCopy, stringResource(R.string.save_copy), stringResource(R.string.save_copy_desc)) {
                        choose(SaveMode.Copy)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
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
        is Phase.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.saving)) },
            text = {
                Column {
                    if (p.progress != null) {
                        LinearProgressIndicator(progress = { p.progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.percent, (p.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
        )
        is Phase.Failed -> AlertDialog(
            onDismissRequest = { phase = Phase.Idle; onCancel() },
            title = { Text(stringResource(R.string.save_failed)) },
            text = { Text(p.message) },
            confirmButton = { TextButton(onClick = { phase = Phase.Idle; onCancel() }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    )
}

private fun normalizeMime(mime: String) = when (mime.lowercase()) {
    "image/jpg" -> "image/jpeg"
    else -> mime.lowercase()
}
