package com.mephilia.gallery.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.MediaWriter
import com.mephilia.gallery.ui.components.CropOverlay
import com.mephilia.gallery.ui.components.aspectPresets
import com.mephilia.gallery.ui.components.centeredCrop
import com.mephilia.gallery.ui.components.rotatedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class PhotoTool(val label: Int) {
    Crop(R.string.tool_crop),
    Adjust(R.string.tool_adjust),
    Draw(R.string.tool_draw),
    Hide(R.string.tool_hide),
}

private enum class AdjustKind(val label: Int) {
    Brightness(R.string.adjust_brightness),
    Contrast(R.string.adjust_contrast),
    Saturation(R.string.adjust_saturation),
    Warmth(R.string.adjust_warmth),
}

private val penColors = listOf(
    Color.White, Color.Black, Color(0xFFE53935), Color(0xFFFFC107), Color(0xFF43A047), Color(0xFF1E88E5),
)

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
    var presetIndex by remember { mutableIntStateOf(0) }
    val preset = presets[presetIndex]
    var saving by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(PhotoTool.Crop) }

    // Adjust, Draw and Hide work on the cropped result
    var cropped by remember { mutableStateOf<Bitmap?>(null) }
    var pixelated by remember { mutableStateOf<Bitmap?>(null) }
    val geometryKey = listOf(edits.quarterTurns, edits.flipH, edits.crop, edits.angle)
    LaunchedEffect(original, tool == PhotoTool.Crop, geometryKey) {
        val src = original ?: return@LaunchedEffect
        if (tool == PhotoTool.Crop) return@LaunchedEffect
        cropped = withContext(Dispatchers.Default) { src.applyEdits(edits) }
        pixelated = null
    }
    LaunchedEffect(cropped, edits.adjust, tool == PhotoTool.Hide || edits.marks.any { it.mosaic }) {
        val c = cropped ?: return@LaunchedEffect
        if (tool != PhotoTool.Hide && edits.marks.none { it.mosaic }) return@LaunchedEffect
        pixelated = withContext(Dispatchers.Default) { c.withEffects(edits.adjust, emptyList()).mosaic() }
    }

    var adjustKind by remember { mutableStateOf(AdjustKind.Brightness) }
    var penColor by remember { mutableStateOf(penColors[2]) }
    var penSize by remember { mutableStateOf(0.012f) }
    var hideSize by remember { mutableStateOf(0.05f) }

    fun rotate(delta: Int) {
        // The crop turns with the photo; the ratio resets to free since a fixed one no longer fits.
        // When mirrored, the rotation looks reversed on screen, hence the opposite direction.
        edits = edits.copy(quarterTurns = edits.quarterTurns + delta, crop = edits.crop.rotatedBy(if (edits.flipH) -delta else delta))
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
            val result = cropped
            when {
                bmp == null -> CircularProgressIndicator()
                tool == PhotoTool.Crop -> FitBox(previewRatio) {
                    Straightened(previewRatio, edits.angle) {
                        Image(
                            bmp.asImageBitmap(), null, Modifier.fillMaxSize(),
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
                            PhotoTool.Hide -> Mark(emptyList(), hideSize, 0, mosaic = true)
                            else -> null
                        },
                        onMark = { edits = edits.copy(marks = edits.marks + it) },
                    )
                }
            }
        },
        tools = {
            when (tool) {
                PhotoTool.Crop -> {
                    AspectChips(presets, preset) {
                        presetIndex = presets.indexOf(it)
                        edits = edits.copy(crop = centeredCrop(it.ratio, previewRatio))
                    }
                    StraightenSlider(edits.angle) { edits = edits.copy(angle = it) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ToolButton(Icons.AutoMirrored.Rounded.RotateLeft, stringResource(R.string.tool_left)) { rotate(-1) }
                        ToolButton(Icons.AutoMirrored.Rounded.RotateRight, stringResource(R.string.tool_right)) { rotate(1) }
                        ToolButton(Icons.Rounded.Flip, stringResource(R.string.tool_mirror)) {
                            // Mirroring reverses a tilt, so the angle flips sign to keep the image straight
                            edits = edits.copy(
                                flipH = !edits.flipH,
                                angle = -edits.angle,
                                crop = edits.crop.let { c -> c.copy(left = 1f - c.right, right = 1f - c.left) },
                            )
                        }
                        ToolButton(Icons.Rounded.RestartAlt, stringResource(R.string.tool_reset)) {
                            edits = PhotoEdits()
                            presetIndex = 0
                        }
                    }
                }
                PhotoTool.Adjust -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        AdjustKind.entries.forEach { k ->
                            val v = edits.adjust.valueOf(k)
                            FilterChip(
                                selected = adjustKind == k,
                                onClick = { adjustKind = k },
                                label = { Text(stringResource(k.label) + if (v != 0f) " •" else "") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    labelColor = Color.White,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                border = null,
                            )
                        }
                    }
                    val value = edits.adjust.valueOf(adjustKind)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = value,
                            onValueChange = { v ->
                                val snapped = if (kotlin.math.abs(v) < 0.03f) 0f else v
                                edits = edits.copy(adjust = edits.adjust.with(adjustKind, snapped))
                            },
                            valueRange = -1f..1f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%+d".format(value.asPercent()),
                            color = if (value == 0f) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .width(52.dp)
                                .clickable { edits = edits.copy(adjust = edits.adjust.with(adjustKind, 0f)) },
                        )
                    }
                }
                PhotoTool.Draw -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        penColors.forEach { c ->
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(c, CircleShape)
                                    .border(
                                        if (c == penColor) 3.dp else 1.dp,
                                        if (c == penColor) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                        CircleShape,
                                    )
                                    .clickable { penColor = c },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        UndoButton(edits.marks.isNotEmpty()) { edits = edits.copy(marks = edits.marks.dropLast(1)) }
                    }
                    SizeSlider(penSize, 0.004f..0.04f) { penSize = it }
                }
                PhotoTool.Hide -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.hide_hint),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        UndoButton(edits.marks.isNotEmpty()) { edits = edits.copy(marks = edits.marks.dropLast(1)) }
                    }
                    SizeSlider(hideSize, 0.02f..0.15f) { hideSize = it }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                PhotoTool.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = tool == t,
                        onClick = { tool = t },
                        shape = SegmentedButtonDefaults.itemShape(i, PhotoTool.entries.size),
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = Color.White,
                        ),
                    ) { Text(stringResource(t.label), maxLines = 1) }
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

private fun Adjustments.valueOf(k: AdjustKind) = when (k) {
    AdjustKind.Brightness -> brightness
    AdjustKind.Contrast -> contrast
    AdjustKind.Saturation -> saturation
    AdjustKind.Warmth -> warmth
}

private fun Adjustments.with(k: AdjustKind, v: Float) = when (k) {
    AdjustKind.Brightness -> copy(brightness = v)
    AdjustKind.Contrast -> copy(contrast = v)
    AdjustKind.Saturation -> copy(saturation = v)
    AdjustKind.Warmth -> copy(warmth = v)
}

@Composable
private fun UndoButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.action_undo),
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun SizeSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.brush_size), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

/**
 * The cropped photo with its adjustments and marks. When [drawing] is set, a finger draws a new mark
 * with its settings (pen color or mosaic, width).
 */
@Composable
private fun MarkCanvas(
    image: Bitmap,
    pixelated: Bitmap?,
    edits: PhotoEdits,
    drawing: Mark?,
    onMark: (Mark) -> Unit,
) {
    val imageBitmap = remember(image) { image.asImageBitmap() }
    val mosaicBrush = remember(pixelated) { pixelated?.let { ShaderBrush(ImageShader(it.asImageBitmap())) } }
    val current = remember { mutableStateListOf<Offset>() }
    val colorFilter = if (edits.adjust.isIdentity) null else ColorFilter.colorMatrix(edits.adjust.composeMatrix())
    Box(Modifier.fillMaxSize()) {
        Image(imageBitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, colorFilter = colorFilter)
        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (drawing == null) Modifier else Modifier.pointerInput(drawing) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            current.clear()
                            current += Offset(down.position.x / size.width, down.position.y / size.height)
                            down.consume()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                current += Offset(
                                    (ch.position.x / size.width).coerceIn(0f, 1f),
                                    (ch.position.y / size.height).coerceIn(0f, 1f),
                                )
                            }
                            if (current.isNotEmpty()) onMark(drawing.copy(points = current.toList()))
                            current.clear()
                        }
                    },
                ),
        ) {
            // The mosaic shader is sized to the image: scale it to the preview
            fun drawMark(m: Mark) {
                val path = strokePath(m.points, size.width, size.height).asComposePath()
                val stroke = Stroke(width = (m.width * size.width).coerceAtLeast(1f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                if (m.mosaic) {
                    val brush = mosaicBrush ?: return
                    val sx = size.width / image.width
                    val sy = size.height / image.height
                    // Draw in image coordinates so the shader lines up with the photo
                    val scaled = android.graphics.Path(path.asAndroidPath()).apply {
                        transform(android.graphics.Matrix().apply { setScale(1 / sx, 1 / sy) })
                    }.asComposePath()
                    drawContext.canvas.save()
                    drawContext.canvas.scale(sx, sy)
                    drawPath(scaled, brush, style = Stroke(width = stroke.width / sx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawContext.canvas.restore()
                } else {
                    drawPath(path, Color(m.color), style = stroke)
                }
            }
            edits.marks.forEach(::drawMark)
            if (drawing != null && current.isNotEmpty()) drawMark(drawing.copy(points = current.toList()))
        }
    }
}
