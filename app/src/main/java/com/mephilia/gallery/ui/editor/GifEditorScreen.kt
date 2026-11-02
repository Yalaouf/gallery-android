package com.mephilia.gallery.ui.editor

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.components.CropOverlay
import com.mephilia.gallery.ui.components.aspectPresets
import com.mephilia.gallery.ui.components.centeredCrop

/** Playback speeds offered for a GIF. */
private val GIF_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)

/**
 * Editor for animated GIFs: crop, straighten, turn and mirror like a photo, plus the playback speed and
 * whether it loops. The animated preview shows the geometry but not the speed, which is only applied when saving.
 * If the GIF can't be read, a message says so and the editor closes.
 */
@Composable
fun GifEditorScreen(item: MediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val info by produceState<GifInfo?>(null, item.uri) {
        value = runCatching { readGifInfo(context, item.uri) }.getOrNull()
        if (value == null) {
            Toast.makeText(context, R.string.gif_open_failed, Toast.LENGTH_LONG).show()
            onClose()
        }
    }
    var edits by remember { mutableStateOf(PhotoEdits()) }
    var speed by remember { mutableFloatStateOf(NORMAL_SPEED) }
    var loop by remember { mutableStateOf(true) }
    var presetIndex by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(info) { info?.let { loop = it.loops } }

    val ratio = turnedRatio(info?.ratio ?: 1f, edits.quarterTurns)
    val freeLabel = stringResource(R.string.aspect_free)
    val originalLabel = stringResource(R.string.aspect_original)
    val presets = remember(ratio, freeLabel) { aspectPresets(ratio, freeLabel, originalLabel) }
    val loopChanged = info?.let { loop != it.loops } ?: false
    val edited = edits != PhotoEdits() || speed != NORMAL_SPEED || loopChanged

    EditorScaffold(
        title = stringResource(R.string.edit_gif_title),
        isVideo = false,
        onClose = onClose,
        onSave = { saving = true },
        saveEnabled = info != null && edited && !saving,
        preview = {
            if (info == null) {
                CircularProgressIndicator()
            } else {
                FitBox(ratio) {
                    Straightened(ratio, edits.angle) {
                        Transformed(edits.quarterTurns, edits.flipH) { AnimatedGif(item) }
                    }
                    CropOverlay(
                        crop = edits.crop,
                        onCropChange = { edits = edits.copy(crop = it) },
                        ratio = presets[presetIndex].ratio,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        tools = {
            AspectChips(presets, presets[presetIndex]) {
                presetIndex = presets.indexOf(it)
                edits = edits.copy(crop = centeredCrop(it.ratio, ratio))
            }
            StraightenSlider(edits.angle) { edits = edits.copy(angle = it) }
            ChipRow {
                item { EditorChip(stringResource(R.string.gif_loop), loop, onClick = { loop = !loop }, icon = Icons.Rounded.Repeat) }
                speedChips(GIF_SPEEDS, speed) { speed = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TurnButtons(
                    mirrored = edits.flipH,
                    onRotate = {
                        edits = edits.rotated(it)
                        presetIndex = 0
                    },
                    onMirror = { edits = edits.mirrored() },
                )
                ToolButton(Icons.Rounded.RestartAlt, stringResource(R.string.tool_reset)) {
                    edits = PhotoEdits()
                    speed = NORMAL_SPEED
                    loop = info?.loops ?: true
                    presetIndex = 0
                }
            }
        },
    )

    SaveFlow(
        item = item,
        active = saving,
        onCancel = { saving = false },
        outputMime = GIF_MIME,
        outputExtension = GIF_EXTENSION,
        produce = { onProgress ->
            renderGif(context, item.uri, edits, speed, loop, MediaWriter(context).tempFile(GIF_EXTENSION), onProgress)
        },
        onSaved = {
            saving = false
            onClose()
        },
    )
}

/** The GIF of [item], playing, stretched to fill its box (the box already has the right ratio). */
@Composable
private fun AnimatedGif(item: MediaItem) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(item.uri, item.dateModified) {
            ImageRequest.Builder(context)
                .data(item.uri)
                .memoryCacheKey("gif:${item.uri}@${item.dateModified}")
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize(),
    )
}
