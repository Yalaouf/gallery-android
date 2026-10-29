package com.mephilia.gallery.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.components.CropOverlay
import com.mephilia.gallery.ui.components.FullCrop
import com.mephilia.gallery.ui.components.aspectPresets
import com.mephilia.gallery.ui.components.centeredCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R

@Composable
fun PhotoEditorScreen(item: MediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val original by produceState<Bitmap?>(null, item.uri) {
        value = runCatching { decodeBitmap(context, item.uri, maxSide = 2048) }.getOrNull()
    }
    var edits by remember { mutableStateOf(PhotoEdits()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(original, edits.quarterTurns, edits.flipH) {
        val src = original ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) { src.transformed(edits) }
    }
    val previewRatio = preview?.let { it.width.toFloat() / it.height } ?: 1f
    val freeLabel = stringResource(R.string.aspect_free)
    val originalLabel = stringResource(R.string.aspect_original)
    val presets = remember(previewRatio, freeLabel) { aspectPresets(previewRatio, freeLabel, originalLabel) }
    var presetIndex by remember { mutableStateOf(0) }
    val preset = presets[presetIndex]
    var saving by remember { mutableStateOf(false) }

    fun rotate(delta: Int) {
        edits = edits.copy(quarterTurns = edits.quarterTurns + delta, crop = FullCrop)
        presetIndex = 0
    }

    EditorScaffold(
        title = stringResource(R.string.edit_photo_title),
        isVideo = false,
        onClose = onClose,
        onSave = { saving = true },
        saveEnabled = preview != null && !saving,
        preview = {
            val bmp = preview
            if (bmp == null) {
                CircularProgressIndicator()
            } else {
                FitBox(previewRatio) {
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    CropOverlay(
                        crop = edits.crop,
                        onCropChange = { edits = edits.copy(crop = it) },
                        ratio = preset.ratio,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        tools = {
            AspectChips(presets, preset) {
                presetIndex = presets.indexOf(it)
                edits = edits.copy(crop = centeredCrop(it.ratio, previewRatio))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton(Icons.AutoMirrored.Rounded.RotateLeft, stringResource(R.string.tool_left)) { rotate(-1) }
                ToolButton(Icons.AutoMirrored.Rounded.RotateRight, stringResource(R.string.tool_right)) { rotate(1) }
                ToolButton(Icons.Rounded.Flip, stringResource(R.string.tool_mirror)) {
                    edits = edits.copy(
                        flipH = !edits.flipH,
                        crop = edits.crop.let { c -> c.copy(left = 1f - c.right, right = 1f - c.left) },
                    )
                }
                ToolButton(Icons.Rounded.RestartAlt, stringResource(R.string.tool_reset)) {
                    edits = PhotoEdits()
                    presetIndex = 0
                }
            }
        },
    )

    val format = remember(item.mimeType) { outputFormatFor(item.mimeType) }
    SaveFlow(
        item = item,
        active = saving,
        onCancel = { saving = false },
        outputMime = format.mime,
        outputExtension = format.extension,
        produce = { renderPhoto(context, item, edits, MediaWriter(context).tempFile(format.extension)) },
        onSaved = {
            saving = false
            onClose()
        },
    )
}
