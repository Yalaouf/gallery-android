package com.mephilia.gallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mephilia.gallery.R
import com.mephilia.gallery.data.GifEncoder
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.components.formatDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import com.mephilia.gallery.data.MediaEvents

/** Longer GIFs get too heavy. */
const val GIF_MAX_DURATION_MS = 15_000L

private val GIF_SIZES = listOf(320, 480, 720, 1080)
// 50 fps is the GIF maximum: frame delays are stored in hundredths of a second,
// and players slow down delays under 2 cs (60 fps would play about 4× too slowly)
private val GIF_FPS = listOf(10, 15, 30, 50)

/**
 * Creates a GIF from [startMs]..[endMs] of the video with the same edits as the video export.
 * Frames are sampled at [fps] and shown for 1/[fps] each, so the GIF plays at the video's speed.
 * [maxSide] is the GIF's longest side in pixels.
 */
suspend fun exportGif(
    context: Context,
    uri: Uri,
    info: VideoInfo,
    startMs: Long,
    endMs: Long,
    edits: PhotoEdits,
    maxSide: Int,
    fps: Int,
    loop: Boolean,
    out: File,
    onProgress: (Float) -> Unit,
): File = withContext(Dispatchers.Default) {
    val count = max(1, ((endMs - startMs) * fps / 1000.0).roundToInt())
    // Request frames large enough for the cropped area to reach maxSide.
    // The crop is expressed in the rotated image, where width and height swap on a quarter turn.
    val turned = edits.quarterTurns.mod(2) == 1
    val shownW = if (turned) info.height else info.width
    val shownH = if (turned) info.width else info.height
    val cropLongest = max(edits.crop.width * shownW, edits.crop.height * shownH).coerceAtLeast(1f)
    val fullLongest = max(info.width, info.height)
    // Twice the needed size, so the final reduction can be done by sharp halving steps (see scaleDownSmooth)
    val request = (fullLongest * 2f * maxSide / cropLongest).roundToInt().coerceIn(1, fullLongest)

    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, uri)
        out.outputStream().buffered().use { os ->
            var encoder: GifEncoder? = null
            var outW = 0
            var outH = 0
            var pixels = IntArray(0)
            for (i in 0 until count) {
                ensureActive()
                val timeUs = (startMs * 1000) + i * 1_000_000L / fps
                val raw = retriever.getScaledFrameAtTime(
                    timeUs, MediaMetadataRetriever.OPTION_CLOSEST, request, request,
                ) ?: continue
                val oriented = orient(raw, info)
                val edited = oriented.applyEdits(edits)
                if (encoder == null) {
                    val scale = minOf(1f, maxSide.toFloat() / max(edited.width, edited.height))
                    outW = (edited.width * scale).roundToInt().coerceAtLeast(1)
                    outH = (edited.height * scale).roundToInt().coerceAtLeast(1)
                    encoder = GifEncoder(os, outW, outH, loop)
                    pixels = IntArray(outW * outH)
                }
                val scaled = edited.scaleDownSmooth(outW, outH)
                scaled.getPixels(pixels, 0, outW, 0, 0, outW, outH)
                // Cumulative rounding of the hundredths keeps the overall speed exact
                val delayCs = ((i + 1) * 100.0 / fps).roundToInt() - (i * 100.0 / fps).roundToInt()
                encoder.addFrame(pixels, delayCs)
                for (b in setOf(raw, oriented, edited, scaled)) b.recycle()
                onProgress((i + 1f) / count)
            }
            encoder?.close() ?: error("No frame could be read from the video")
        }
    }
    out
}

/**
 * High-quality reduction: halves the size while it's more than twice the target (each halving averages
 * 2×2 pixels), then a final bilinear step. A single bilinear step over a large ratio skips pixels and
 * loses fine details. May return this bitmap when it already has the target size.
 */
private fun Bitmap.scaleDownSmooth(targetW: Int, targetH: Int): Bitmap {
    var current = this
    while (current.width >= targetW * 2 && current.height >= targetH * 2) {
        val half = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
        if (current !== this) current.recycle()
        current = half
    }
    if (current.width == targetW && current.height == targetH) return current
    val result = Bitmap.createScaledBitmap(current, targetW, targetH, true)
    if (current !== this && current !== result) current.recycle()
    return result
}

/**
 * Rotates the frame upright if the retriever didn't: depending on the device,
 * extracted frames may or may not have the rotation metadata applied.
 */
private fun orient(frame: Bitmap, info: VideoInfo): Bitmap {
    val rotation = info.rotation.mod(360)
    if (rotation % 180 == 0) return frame
    val frameLandscape = frame.width > frame.height
    val displayLandscape = info.width > info.height
    if (frameLandscape == displayLandscape) return frame
    val m = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, m, true)
}

private sealed interface GifPhase {
    data object Options : GifPhase
    data class Working(val progress: Float) : GifPhase
    data class Failed(val message: String) : GifPhase
}

/** The GIF is always saved as a new image next to the video. */
@OptIn(ExperimentalMaterial3Api::class)
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
    var sizeIndex by rememberSaveable { mutableIntStateOf(1) }
    var fpsIndex by rememberSaveable { mutableIntStateOf(1) }
    var loop by rememberSaveable { mutableStateOf(true) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun start() {
        val fps = GIF_FPS[fpsIndex]
        job = scope.launch {
            val writer = MediaWriter(context)
            val temp = writer.tempFile("gif")
            try {
                phase = GifPhase.Working(0f)
                exportGif(context, item.uri, info, startMs, endMs, edits, GIF_SIZES[sizeIndex], fps, loop, temp) {
                    phase = GifPhase.Working(it)
                }
                val name = MediaWriter.copyName(item.name, "gif")
                writer.insertCopy(item, temp, name, "image/gif")
                MediaEvents.changed()
                Toast.makeText(context, context.getString(R.string.gif_saved, name), Toast.LENGTH_LONG).show()
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                phase = GifPhase.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                temp.delete()
            }
        }
    }

    when (val p = phase) {
        GifPhase.Options -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.GifBox, null) },
            title = { Text(stringResource(R.string.gif_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.gif_size), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        GIF_SIZES.forEachIndexed { i, px ->
                            SegmentedButton(
                                selected = sizeIndex == i,
                                onClick = { sizeIndex = i },
                                shape = SegmentedButtonDefaults.itemShape(i, GIF_SIZES.size),
                                icon = {},
                            ) { Text("$px", maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.gif_smoothness), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        GIF_FPS.forEachIndexed { i, fps ->
                            SegmentedButton(
                                selected = fpsIndex == i,
                                onClick = { fpsIndex = i },
                                shape = SegmentedButtonDefaults.itemShape(i, GIF_FPS.size),
                                icon = {},
                            ) { Text(stringResource(R.string.gif_fps, fps)) }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable { loop = !loop },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Repeat, null)
                        Text(
                            stringResource(R.string.gif_loop),
                            Modifier.weight(1f).padding(start = 12.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Switch(checked = loop, onCheckedChange = { loop = it })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.gif_summary,
                            GIF_SIZES[sizeIndex],
                            formatDuration(endMs - startMs),
                            max(1, ((endMs - startMs) * GIF_FPS[fpsIndex] / 1000.0).roundToInt()),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (GIF_SIZES[sizeIndex] >= 720) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.gif_heavy_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = ::start) { Text(stringResource(R.string.gif_export)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        is GifPhase.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.gif_creating)) },
            text = {
                Column {
                    LinearProgressIndicator(progress = { p.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.percent, (p.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    job?.cancel()
                    onDismiss()
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        is GifPhase.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.gif_failed)) },
            text = { Text(p.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
