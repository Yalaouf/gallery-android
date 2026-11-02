package com.mephilia.gallery.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.R
import com.mephilia.gallery.ui.components.AspectPreset
import com.mephilia.gallery.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt

/** Opacity of an unselected chip's background on the black editor. */
private const val CHIP_BACKGROUND_ALPHA = 0.08f

/** Opacity of secondary white text and icons on the black editor. */
const val EDITOR_SECONDARY_ALPHA = 0.8f

/** Opacity of a value label when the value is at its neutral setting. */
const val EDITOR_NEUTRAL_VALUE_ALPHA = 0.7f

/** The straighten slider goes from minus this angle to this angle, in degrees. */
private const val MAX_STRAIGHTEN_ANGLE = 45f

/** Angles closer to zero than this snap to zero, so the picture is easy to put back straight. */
private const val STRAIGHTEN_SNAP_ANGLE = 0.5f

/** Steps per degree of the straighten slider: the angle is rounded to a tenth of a degree. */
private const val STRAIGHTEN_STEPS_PER_DEGREE = 10f

/** Width of the angle label, wide enough for "-45.0°" so the slider doesn't jump. */
private val ANGLE_LABEL_WIDTH = 56.dp

/** Vertical padding of the angle label, which makes it easier to tap. */
private val ANGLE_LABEL_VERTICAL_PADDING = 6.dp

/** Size of the dot telling that a tool has a pending edit. */
private val ACTIVE_DOT_SIZE = 8.dp

/** Playback speed of an unchanged video or GIF. */
const val NORMAL_SPEED = 1f

/** Speed shown without decimals, e.g. "2×" rather than "2.0×". */
private const val SPEED_SUFFIX = "×"

/** Colors of the editors' chips: discreet on the black background, in the accent color when selected. */
@Composable
fun editorChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = CHIP_BACKGROUND_ALPHA),
    labelColor = Color.White,
    iconColor = Color.White,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)

/** A chip of the editors, with an optional [icon] before its [label]. */
@Composable
fun EditorChip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, null) } },
        colors = editorChipColors(),
        border = null,
    )
}

/** A scrolling row of chips, with the editors' usual padding and spacing. */
@Composable
fun ChipRow(content: LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        content = content,
    )
}

/** Chips to pick a crop aspect ratio among [presets]. */
@Composable
fun AspectChips(presets: List<AspectPreset>, selected: AspectPreset, onSelect: (AspectPreset) -> Unit) {
    ChipRow {
        items(presets, key = { it.label }) { preset ->
            EditorChip(preset.label, preset == selected, onClick = { onSelect(preset) })
        }
    }
}

/** Chips for the playback [speeds], [selected] one highlighted. */
fun LazyListScope.speedChips(speeds: List<Float>, selected: Float, onSelect: (Float) -> Unit) {
    items(speeds) { speed ->
        EditorChip(formatSpeed(speed), selected == speed, onClick = { onSelect(speed) })
    }
}

/** A speed factor as shown on the chips: "0.25×", "1×", "1.5×". */
fun formatSpeed(speed: Float): String {
    val number = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return number + SPEED_SUFFIX
}

/**
 * Slider for a fine rotation, from −45° to 45°. It snaps to 0 near the center,
 * and tapping the value puts it back to 0.
 */
@Composable
fun StraightenSlider(angle: Float, onAngleChange: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Straighten, stringResource(R.string.tool_straighten), tint = Color.White)
        Slider(
            value = angle,
            onValueChange = { value ->
                onAngleChange(
                    if (abs(value) < STRAIGHTEN_SNAP_ANGLE) 0f
                    else (value * STRAIGHTEN_STEPS_PER_DEGREE).roundToInt() / STRAIGHTEN_STEPS_PER_DEGREE,
                )
            },
            valueRange = -MAX_STRAIGHTEN_ANGLE..MAX_STRAIGHTEN_ANGLE,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.m),
        )
        Text(
            stringResource(R.string.angle_degrees, angle),
            color = if (angle == 0f) Color.White.copy(alpha = EDITOR_NEUTRAL_VALUE_ALPHA) else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .width(ANGLE_LABEL_WIDTH)
                .clip(RoundedCornerShape(Spacing.s))
                .clickable { onAngleChange(0f) }
                .padding(vertical = ANGLE_LABEL_VERTICAL_PADDING),
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A tool of the editors: an [icon] above its [label], in the accent color when [selected].
 * When [active], a dot tells that this tool has a pending edit.
 */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            IconButton(onClick = onClick) {
                Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.primary else Color.White)
            }
            if (active) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = Spacing.s, end = Spacing.s)
                        .size(ACTIVE_DOT_SIZE)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = EDITOR_SECONDARY_ALPHA),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * The turn left, turn right and mirror buttons, shared by every editor.
 *
 * @param onRotate called with -1 for a quarter turn to the left, 1 for one to the right.
 * @param mirrored highlights the mirror button while the picture is mirrored.
 */
@Composable
fun TurnButtons(mirrored: Boolean, onRotate: (Int) -> Unit, onMirror: () -> Unit) {
    ToolButton(Icons.AutoMirrored.Rounded.RotateLeft, stringResource(R.string.tool_left)) { onRotate(-1) }
    ToolButton(Icons.AutoMirrored.Rounded.RotateRight, stringResource(R.string.tool_right)) { onRotate(1) }
    ToolButton(Icons.Rounded.Flip, stringResource(R.string.tool_mirror), selected = mirrored, onClick = onMirror)
}
