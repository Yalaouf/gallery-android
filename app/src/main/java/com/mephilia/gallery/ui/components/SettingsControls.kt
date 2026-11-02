package com.mephilia.gallery.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.mephilia.gallery.ui.theme.Spacing
import kotlin.math.roundToInt

/** The title of a settings section, in the accent color. */
@Composable
internal fun SettingsTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s),
    )
}

/** A setting turned on or off, with an optional [subtitle] explaining it. The whole row can be tapped. */
@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.m))
            .clickable { onChange(!checked) }
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * A choice among [options], as a row of segmented buttons.
 *
 * @param label the text of each option.
 * @param showCheck shows a check mark on the selected option (Material's default); without it, labels have more room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    showCheck: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = if (showCheck) ({ SegmentedButtonDefaults.Icon(selected == option) }) else ({}),
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

/**
 * A setting picked on a slider: its [title] and [valueText] above the slider, which moves in [step]s
 * between the ends of [range].
 */
@Composable
internal fun StepSlider(title: String, valueText: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(valueText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange((it / step).roundToInt() * step) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / step - 1,
    )
}
