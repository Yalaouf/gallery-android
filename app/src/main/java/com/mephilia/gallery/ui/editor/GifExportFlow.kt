package com.mephilia.gallery.ui.editor

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaEvents
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.components.formatDuration
import com.mephilia.gallery.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The longest sides offered for a GIF, in pixels. */
private val GIF_SIZES = listOf(320, 480, 720, 1080)

/**
 * The frame rates offered for a GIF. 50 fps is the GIF maximum: delays are stored in hundredths of a second, and
 * players slow down delays under 2 (60 fps would play about 4 times too slowly).
 */
private val GIF_FPS = listOf(10, 15, 30, 50)

/** Size and frame rate picked when the dialog opens: the second of each list. */
private const val DEFAULT_CHOICE_INDEX = 1

/** From this size on, a warning says the GIF will be heavy. */
private const val HEAVY_GIF_SIZE = 720

/** Gap between the summary and the heavy GIF warning. */
private val WARNING_GAP = 6.dp

/** The steps of the GIF export dialog. */
private sealed interface GifPhase {
    /** Choosing the size, frame rate and looping. */
    data object Options : GifPhase

    /** Creating the GIF, [progress] from 0 to 1. */
    data class Working(val progress: Float) : GifPhase

    /** The export failed, with the error [message]. */
    data class Failed(val message: String) : GifPhase
}

/**
 * Exports the part of a video between [startMs] and [endMs] as a GIF, with the video's [edits]: the user picks
 * the size, smoothness (frame rate) and looping, then follows the progress. The GIF is always saved as a new
 * image next to the video, and a message gives its name.
 */
@Composable
fun GifExportFlow(
    item: MediaItem,
    info: VideoInfo,
    startMs: Long,
    endMs: Long,
    edits: PhotoEdits,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<GifPhase>(GifPhase.Options) }
    var sizeIndex by rememberSaveable { mutableIntStateOf(DEFAULT_CHOICE_INDEX) }
    var fpsIndex by rememberSaveable { mutableIntStateOf(DEFAULT_CHOICE_INDEX) }
    var loop by rememberSaveable { mutableStateOf(true) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun start() {
        job = scope.launch {
            val writer = MediaWriter(context)
            val temp = writer.tempFile(GIF_EXTENSION)
            try {
                phase = GifPhase.Working(0f)
                exportGif(context, item.uri, info, startMs, endMs, edits, GIF_SIZES[sizeIndex], GIF_FPS[fpsIndex], loop, temp) {
                    phase = GifPhase.Working(it)
                }
                val name = MediaWriter.copyName(item.name, GIF_EXTENSION)
                writer.insertCopy(item, temp, name, GIF_MIME)
                MediaEvents.changed()
                Toast.makeText(context, context.getString(R.string.gif_saved, name), Toast.LENGTH_LONG).show()
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                phase = GifPhase.Failed(e.readableMessage())
            } finally {
                temp.delete()
            }
        }
    }

    when (val current = phase) {
        GifPhase.Options -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.GifBox, null) },
            title = { Text(stringResource(R.string.gif_title)) },
            text = {
                GifOptions(
                    durationMs = endMs - startMs,
                    sizeIndex = sizeIndex,
                    fpsIndex = fpsIndex,
                    loop = loop,
                    onSizeChange = { sizeIndex = it },
                    onFpsChange = { fpsIndex = it },
                    onLoopChange = { loop = it },
                )
            },
            confirmButton = { TextButton(onClick = ::start) { Text(stringResource(R.string.gif_export)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        is GifPhase.Working -> ProgressDialog(stringResource(R.string.gif_creating), current.progress) {
            job?.cancel()
            onDismiss()
        }
        is GifPhase.Failed -> FailureDialog(stringResource(R.string.gif_failed), current.message, onDismiss)
    }
}

/**
 * The GIF options: size, smoothness and looping, then a summary (size, duration, frame count)
 * and a warning when the GIF will be heavy.
 */
@Composable
private fun GifOptions(
    durationMs: Long,
    sizeIndex: Int,
    fpsIndex: Int,
    loop: Boolean,
    onSizeChange: (Int) -> Unit,
    onFpsChange: (Int) -> Unit,
    onLoopChange: (Boolean) -> Unit,
) {
    Column {
        Text(stringResource(R.string.gif_size), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(Spacing.s))
        Choices(GIF_SIZES.map { "$it" }, sizeIndex, onSizeChange)
        Spacer(Modifier.height(Spacing.l))
        Text(stringResource(R.string.gif_smoothness), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(Spacing.s))
        Choices(GIF_FPS.map { stringResource(R.string.gif_fps, it) }, fpsIndex, onFpsChange)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.s)
                .clickable { onLoopChange(!loop) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Repeat, null)
            Text(stringResource(R.string.gif_loop), Modifier.weight(1f).padding(start = Spacing.m), style = MaterialTheme.typography.labelLarge)
            Switch(checked = loop, onCheckedChange = onLoopChange)
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            stringResource(R.string.gif_summary, GIF_SIZES[sizeIndex], formatDuration(durationMs), gifFrameCount(durationMs, GIF_FPS[fpsIndex])),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (GIF_SIZES[sizeIndex] >= HEAVY_GIF_SIZE) {
            Spacer(Modifier.height(WARNING_GAP))
            Text(
                stringResource(R.string.gif_heavy_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** A row of segmented buttons, one per label, the one at [selected] highlighted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                icon = {},
            ) { Text(label, maxLines = 1) }
        }
    }
}
