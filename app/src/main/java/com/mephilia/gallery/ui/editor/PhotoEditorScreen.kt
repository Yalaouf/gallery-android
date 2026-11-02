package com.mephilia.gallery.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.components.CropOverlay
import com.mephilia.gallery.ui.components.aspectPresets
import com.mephilia.gallery.ui.components.centeredCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Longest side the photo is decoded at for editing; saving works on the full-size photo. */
private const val EDITING_MAX_SIDE_PX = 2048

/** Width of a new drawing stroke, as a share of the photo's width. */
private const val DEFAULT_PEN_SIZE = 0.012f

/** Width of a new hiding stroke, as a share of the photo's width. */
private const val DEFAULT_HIDE_SIZE = 0.05f

/** Pen color picked when the editor opens: the red of [PEN_COLORS]. */
private const val DEFAULT_PEN_COLOR_INDEX = 2

/**
 * Editor for photos, with four tools: crop (ratios, straighten, turn, mirror), adjust (light, contrast, color,
 * warmth), draw, and hide (pixelate faces, plates or text).
 *
 * The photo is decoded at a reduced size for a responsive preview. Adjust, draw and hide work on the cropped
 * result, which is computed when leaving the crop tool; the pixelated version used by hide is computed only when
 * needed.
 */
@Composable
fun PhotoEditorScreen(item: MediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val original by produceState<Bitmap?>(null, item.uri) {
        value = runCatching { decodeBitmap(context, item.uri, maxSide = EDITING_MAX_SIDE_PX) }.getOrNull()
    }
    var edits by remember { mutableStateOf(PhotoEdits()) }
    var tool by remember { mutableStateOf(PhotoTool.Crop) }
    var presetIndex by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var adjustKind by remember { mutableStateOf(AdjustKind.Brightness) }
    var penColor by remember { mutableStateOf(PEN_COLORS[DEFAULT_PEN_COLOR_INDEX]) }
    var penSize by remember { mutableFloatStateOf(DEFAULT_PEN_SIZE) }
    var hideSize by remember { mutableFloatStateOf(DEFAULT_HIDE_SIZE) }

    var turned by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(original, edits.quarterTurns, edits.flipH) {
        val source = original ?: return@LaunchedEffect
        turned = withContext(Dispatchers.Default) { source.transformed(edits) }
    }
    var cropped by remember { mutableStateOf<Bitmap?>(null) }
    var pixelated by remember { mutableStateOf<Bitmap?>(null) }
    val geometryKey = listOf(edits.quarterTurns, edits.flipH, edits.crop, edits.angle)
    LaunchedEffect(original, tool == PhotoTool.Crop, geometryKey) {
        val source = original ?: return@LaunchedEffect
        if (tool == PhotoTool.Crop) return@LaunchedEffect
        cropped = withContext(Dispatchers.Default) { source.applyEdits(edits) }
        pixelated = null
    }
    val needsMosaic = tool == PhotoTool.Hide || edits.marks.any { it.mosaic }
    LaunchedEffect(cropped, edits.adjust, needsMosaic) {
        val result = cropped ?: return@LaunchedEffect
        if (!needsMosaic) return@LaunchedEffect
        pixelated = withContext(Dispatchers.Default) { result.withEffects(edits.adjust, emptyList()).mosaic() }
    }

    val turnedRatio = turned?.let { it.width.toFloat() / it.height } ?: 1f
    val freeLabel = stringResource(R.string.aspect_free)
    val originalLabel = stringResource(R.string.aspect_original)
    val presets = remember(turnedRatio, freeLabel) { aspectPresets(turnedRatio, freeLabel, originalLabel) }
    val preset = presets[presetIndex]
    val undoLastMark = { edits = edits.copy(marks = edits.marks.dropLast(1)) }

    EditorScaffold(
        title = stringResource(R.string.edit_photo_title),
        isVideo = false,
        onClose = onClose,
        onSave = { saving = true },
        saveEnabled = turned != null && !saving,
        preview = {
            val turnedBitmap = turned
            val result = cropped
            when {
                turnedBitmap == null -> CircularProgressIndicator()
                tool == PhotoTool.Crop -> FitBox(turnedRatio) {
                    Straightened(turnedRatio, edits.angle) {
                        Image(
                            turnedBitmap.asImageBitmap(), null, Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                            colorFilter = if (edits.adjust.isIdentity) null else ColorFilter.colorMatrix(edits.adjust.composeMatrix()),
                        )
                    }
                    CropOverlay(
                        crop = edits.crop,
                        onCropChange = { edits = edits.copy(crop = it) },
                        ratio = preset.ratio,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                result == null -> CircularProgressIndicator()
                else -> FitBox(result.width.toFloat() / result.height) {
                    MarkCanvas(
                        image = result,
                        pixelated = pixelated,
                        edits = edits,
                        drawing = when (tool) {
                            PhotoTool.Draw -> Mark(emptyList(), penSize, penColor.toArgb(), mosaic = false)
                            PhotoTool.Hide -> Mark(emptyList(), hideSize, color = 0, mosaic = true)
                            else -> null
                        },
                        onMark = { edits = edits.copy(marks = edits.marks + it) },
                    )
                }
            }
        },
        tools = {
            when (tool) {
                PhotoTool.Crop -> CropTools(
                    presets = presets,
                    preset = preset,
                    edits = edits,
                    onPreset = {
                        presetIndex = presets.indexOf(it)
                        edits = edits.copy(crop = centeredCrop(it.ratio, turnedRatio))
                    },
                    onEditsChange = { edits = it },
                    onRotate = {
                        edits = edits.rotated(it)
                        presetIndex = 0
                    },
                    onReset = {
                        edits = PhotoEdits()
                        presetIndex = 0
                    },
                )
                PhotoTool.Adjust -> AdjustTools(
                    adjust = edits.adjust,
                    kind = adjustKind,
                    onKindChange = { adjustKind = it },
                    onAdjustChange = { edits = edits.copy(adjust = it) },
                )
                PhotoTool.Draw -> DrawTools(
                    color = penColor,
                    size = penSize,
                    canUndo = edits.marks.isNotEmpty(),
                    onColorChange = { penColor = it },
                    onSizeChange = { penSize = it },
                    onUndo = undoLastMark,
                )
                PhotoTool.Hide -> HideTools(
                    size = hideSize,
                    canUndo = edits.marks.isNotEmpty(),
                    onSizeChange = { hideSize = it },
                    onUndo = undoLastMark,
                )
            }
            PhotoToolTabs(tool) { tool = it }
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
