package com.mephilia.gallery.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.components.CropOverlay
import com.mephilia.gallery.ui.components.FullCrop
import com.mephilia.gallery.ui.components.aspectPresets
import com.mephilia.gallery.ui.components.centeredCrop
import com.mephilia.gallery.ui.components.rotatedBy

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)

@Composable
fun GifEditorScreen(item: MediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val info by produceState<GifInfo?>(null, item.uri) {
        value = runCatching { readGifInfo(context, item.uri) }.getOrNull()
    }
    var edits by remember { mutableStateOf(PhotoEdits()) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loop by remember { mutableStateOf(true) }
    LaunchedEffect(info) { info?.let { loop = it.loops } }
    var presetIndex by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    val sourceRatio = info?.ratio ?: 1f
    val ratio = if (edits.quarterTurns.mod(2) == 1) 1f / sourceRatio else sourceRatio
    val freeLabel = stringResource(R.string.aspect_free)
    val originalLabel = stringResource(R.string.aspect_original)
    val presets = remember(ratio, freeLabel) { aspectPresets(ratio, freeLabel, originalLabel) }
    val edited = edits != PhotoEdits() || speed != 1f || (info != null && loop != info!!.loops)

    fun rotate(delta: Int) {
        edits = edits.copy(
            quarterTurns = edits.quarterTurns + delta,
            crop = edits.crop.rotatedBy(if (edits.flipH) -delta else delta),
        )
        presetIndex = 0
    }

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
                    // The animated preview ignores the speed, which is only applied when saving
                    Straightened(ratio, edits.angle) {
                    Transformed(edits.quarterTurns, edits.flipH) {
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
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = loop,
                        onClick = { loop = !loop },
                        label = { Text(stringResource(R.string.gif_loop)) },
                        leadingIcon = { Icon(Icons.Rounded.Repeat, null) },
                        colors = editorChipColors(),
                        border = null,
                    )
                }
                items(SPEEDS) { s ->
                    FilterChip(
                        selected = speed == s,
                        onClick = { speed = s },
                        label = { Text(formatSpeed(s)) },
                        colors = editorChipColors(),
                        border = null,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton(Icons.AutoMirrored.Rounded.RotateLeft, stringResource(R.string.tool_left)) { rotate(-1) }
                ToolButton(Icons.AutoMirrored.Rounded.RotateRight, stringResource(R.string.tool_right)) { rotate(1) }
                ToolButton(Icons.Rounded.Flip, stringResource(R.string.tool_mirror), selected = edits.flipH) {
                    edits = edits.copy(
                        flipH = !edits.flipH,
                        angle = -edits.angle,
                        crop = edits.crop.let { c -> c.copy(left = 1f - c.right, right = 1f - c.left) },
                    )
                }
                ToolButton(Icons.Rounded.RestartAlt, stringResource(R.string.tool_reset)) {
                    edits = PhotoEdits()
                    speed = 1f
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
        outputMime = "image/gif",
        outputExtension = "gif",
        produce = { onProgress ->
            renderGif(context, item.uri, edits, speed, loop, MediaWriter(context).tempFile("gif"), onProgress)
        },
        onSaved = {
            saving = false
            onClose()
        },
    )
}

private fun formatSpeed(s: Float) =
    (if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString().trimEnd('0')) + "×"

@Composable
private fun editorChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = 0.08f),
    labelColor = Color.White,
    iconColor = Color.White,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)
