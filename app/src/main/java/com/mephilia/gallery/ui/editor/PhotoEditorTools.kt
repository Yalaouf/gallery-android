package com.mephilia.gallery.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.R
import com.mephilia.gallery.ui.components.AspectPreset
import com.mephilia.gallery.ui.theme.Spacing
import kotlin.math.abs

/** The colors offered for drawing. */
internal val PEN_COLORS = listOf(
    Color.White, Color.Black, Color(0xFFE53935), Color(0xFFFFC107), Color(0xFF43A047), Color(0xFF1E88E5),
)

/** Range of the drawing stroke width, as a share of the photo's width. */
private val PEN_SIZE_RANGE = 0.004f..0.04f

/** Range of the hiding stroke width, as a share of the photo's width. */
private val HIDE_SIZE_RANGE = 0.02f..0.15f

/** Range of every adjustment: from −100 % to +100 %. */
private val ADJUST_RANGE = -1f..1f

/** Adjustment values closer to zero than this snap to zero, so they're easy to put back to neutral. */
private const val ADJUST_SNAP = 0.03f

/** Marker added to an adjustment's chip when it's not neutral. */
private const val ADJUSTED_MARKER = " •"

/** Width of the adjustment value label, wide enough for "+100" so the slider doesn't jump. */
private val ADJUST_LABEL_WIDTH = 52.dp

/** Gap between the color swatches. */
private val SWATCH_GAP = 10.dp

/** Size of a color swatch. */
private val SWATCH_SIZE = 30.dp

/** Outline of the selected swatch. */
private val SELECTED_SWATCH_BORDER = 3.dp

/** Outline of the other swatches, which keeps black and white visible on the black background. */
private val SWATCH_BORDER = 1.dp

/** Opacity of the other swatches' outline. */
private const val SWATCH_BORDER_ALPHA = 0.4f

/** Opacity of the undo button when there is nothing to undo. */
private const val DISABLED_ALPHA = 0.3f

/** The tools of the photo editor, in the order of the tabs. */
internal enum class PhotoTool(@StringRes val label: Int) {
    Crop(R.string.tool_crop),
    Adjust(R.string.tool_adjust),
    Draw(R.string.tool_draw),
    Hide(R.string.tool_hide),
}

/** The adjustments of the adjust tool, one slider shown at a time. */
internal enum class AdjustKind(@StringRes val label: Int) {
    Brightness(R.string.adjust_brightness),
    Contrast(R.string.adjust_contrast),
    Saturation(R.string.adjust_saturation),
    Warmth(R.string.adjust_warmth),
}

/** The value of the adjustment [kind]. */
private fun Adjustments.valueOf(kind: AdjustKind) = when (kind) {
    AdjustKind.Brightness -> brightness
    AdjustKind.Contrast -> contrast
    AdjustKind.Saturation -> saturation
    AdjustKind.Warmth -> warmth
}

/** These adjustments with [kind] set to [value]. */
private fun Adjustments.with(kind: AdjustKind, value: Float) = when (kind) {
    AdjustKind.Brightness -> copy(brightness = value)
    AdjustKind.Contrast -> copy(contrast = value)
    AdjustKind.Saturation -> copy(saturation = value)
    AdjustKind.Warmth -> copy(warmth = value)
}

/** The tabs to switch between the photo editor's tools. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoToolTabs(selected: PhotoTool, onSelect: (PhotoTool) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
        PhotoTool.entries.forEachIndexed { index, tool ->
            SegmentedButton(
                selected = selected == tool,
                onClick = { onSelect(tool) },
                shape = SegmentedButtonDefaults.itemShape(index, PhotoTool.entries.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = Color.White,
                ),
            ) { Text(stringResource(tool.label), maxLines = 1) }
        }
    }
}

/**
 * The crop tool: aspect ratios, the straighten slider, turn, mirror and reset.
 *
 * @param onRotate turns the photo by the given quarter turns; the ratio goes back to free, since a fixed one
 * no longer fits.
 * @param onReset undoes every edit, not only the crop.
 */
@Composable
internal fun CropTools(
    presets: List<AspectPreset>,
    preset: AspectPreset,
    edits: PhotoEdits,
    onPreset: (AspectPreset) -> Unit,
    onEditsChange: (PhotoEdits) -> Unit,
    onRotate: (Int) -> Unit,
    onReset: () -> Unit,
) {
    AspectChips(presets, preset, onPreset)
    StraightenSlider(edits.angle) { onEditsChange(edits.copy(angle = it)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TurnButtons(mirrored = edits.flipH, onRotate = onRotate, onMirror = { onEditsChange(edits.mirrored()) })
        ToolButton(Icons.Rounded.RestartAlt, stringResource(R.string.tool_reset), onClick = onReset)
    }
}

/**
 * The adjust tool: a chip per adjustment (marked when not neutral), and the slider of the selected one.
 * The value snaps to zero near the center, and tapping it puts it back to zero.
 */
@Composable
internal fun AdjustTools(
    adjust: Adjustments,
    kind: AdjustKind,
    onKindChange: (AdjustKind) -> Unit,
    onAdjustChange: (Adjustments) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
    ) {
        AdjustKind.entries.forEach { entry ->
            val marker = if (adjust.valueOf(entry) != 0f) ADJUSTED_MARKER else ""
            EditorChip(stringResource(entry.label) + marker, kind == entry, onClick = { onKindChange(entry) })
        }
    }
    val value = adjust.valueOf(kind)
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = { onAdjustChange(adjust.with(kind, if (abs(it) < ADJUST_SNAP) 0f else it)) },
            valueRange = ADJUST_RANGE,
            modifier = Modifier.weight(1f),
        )
        Text(
            "%+d".format(value.asPercent()),
            color = if (value == 0f) Color.White.copy(alpha = EDITOR_NEUTRAL_VALUE_ALPHA) else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(ADJUST_LABEL_WIDTH)
                .clickable { onAdjustChange(adjust.with(kind, 0f)) },
        )
    }
}

/** The draw tool: the pen colors, undo, and the stroke width. */
@Composable
internal fun DrawTools(
    color: Color,
    size: Float,
    canUndo: Boolean,
    onColorChange: (Color) -> Unit,
    onSizeChange: (Float) -> Unit,
    onUndo: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PEN_COLORS.forEach { swatch ->
            val selected = swatch == color
            Box(
                Modifier
                    .size(SWATCH_SIZE)
                    .background(swatch, CircleShape)
                    .border(
                        if (selected) SELECTED_SWATCH_BORDER else SWATCH_BORDER,
                        if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = SWATCH_BORDER_ALPHA),
                        CircleShape,
                    )
                    .clickable { onColorChange(swatch) },
            )
        }
        Spacer(Modifier.weight(1f))
        UndoButton(canUndo, onUndo)
    }
    SizeSlider(size, PEN_SIZE_RANGE, onSizeChange)
}

/** The hide tool: a hint, undo, and the stroke width. */
@Composable
internal fun HideTools(size: Float, canUndo: Boolean, onSizeChange: (Float) -> Unit, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.hide_hint),
            color = Color.White.copy(alpha = EDITOR_SECONDARY_ALPHA),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        UndoButton(canUndo, onUndo)
    }
    SizeSlider(size, HIDE_SIZE_RANGE, onSizeChange)
}

/** Removes the last drawn or hidden stroke. */
@Composable
private fun UndoButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.action_undo),
            tint = if (enabled) Color.White else Color.White.copy(alpha = DISABLED_ALPHA),
        )
    }
}

/** Slider for the width of the next stroke, within [range]. */
@Composable
private fun SizeSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.brush_size),
            color = Color.White.copy(alpha = EDITOR_SECONDARY_ALPHA),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).padding(start = Spacing.m))
    }
}
