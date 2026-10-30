package com.mephilia.gallery.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.SaveMode
import com.mephilia.gallery.data.ThemeMode
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R
import androidx.compose.foundation.layout.width

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s by AppSettings.flow.collectAsStateWithLifecycle()
    fun canManage() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)
    var canManageMedia by remember { mutableStateOf(canManage()) }
    // Re-check when returning from the system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canManageMedia = canManage() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SettingsTitle(stringResource(R.string.settings_appearance))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = s.theme == mode,
                        onClick = { AppSettings.update { it.copy(theme = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(stringResource(mode.label)) }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(stringResource(R.string.dynamic_color), stringResource(R.string.dynamic_color_desc), s.dynamicColor) { v ->
                    AppSettings.update { it.copy(dynamicColor = v) }
                }
            }
            SwitchRow(stringResource(R.string.pure_black), stringResource(R.string.pure_black_desc), s.pureBlack) { v ->
                AppSettings.update { it.copy(pureBlack = v) }
            }

            SettingsTitle(stringResource(R.string.settings_startup))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(stringResource(R.string.start_photos), stringResource(R.string.start_albums)).forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = s.startTab == i,
                        onClick = { AppSettings.update { it.copy(startTab = i) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }

            SettingsTitle(stringResource(R.string.settings_videos))
            SwitchRow(stringResource(R.string.autoplay), stringResource(R.string.autoplay_desc), s.autoplayVideos) { v ->
                AppSettings.update { it.copy(autoplayVideos = v) }
            }
            SwitchRow(stringResource(R.string.always_muted), stringResource(R.string.always_muted_desc), s.alwaysMuted) { v ->
                AppSettings.update { it.copy(alwaysMuted = v) }
            }
            SwitchRow(stringResource(R.string.loop), null, s.loopVideos) { v ->
                AppSettings.update { it.copy(loopVideos = v) }
            }

            SettingsTitle(stringResource(R.string.settings_editing))
            Text(stringResource(R.string.save_button_setting), style = MaterialTheme.typography.bodyLarge)
            SaveModeRow(stringResource(R.string.save_for_photos), s.saveModePhoto) { AppSettings.setSaveModeFor(false, it) }
            SaveModeRow(stringResource(R.string.save_for_videos), s.saveModeVideo) { AppSettings.setSaveModeFor(true, it) }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.photo_quality), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.percent, s.jpegQuality), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = s.jpegQuality.toFloat(),
                onValueChange = { v -> AppSettings.update { it.copy(jpegQuality = (v / 5).roundToInt() * 5) } },
                valueRange = 70f..100f,
                steps = 5,
            )
            Text(
                stringResource(R.string.photo_quality_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow(
                stringResource(R.string.keep_location),
                stringResource(R.string.keep_location_desc),
                s.keepLocation,
            ) { v -> AppSettings.update { it.copy(keepLocation = v) } }

            SettingsTitle(stringResource(R.string.settings_deletion))
            SwitchRow(stringResource(R.string.confirm_delete), stringResource(R.string.confirm_delete_desc), s.confirmDelete) { v ->
                AppSettings.update { it.copy(confirmDelete = v) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsTitle(stringResource(R.string.settings_media_management))
                Text(
                    stringResource(R.string.media_management_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (canManageMedia) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.allowed), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    FilledTonalButton(onClick = {
                        context.startActivity(
                            Intent(SystemSettings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}")),
                        )
                    }) { Text(stringResource(R.string.action_allow)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveModeRow(label: String, selected: SaveMode, onSelect: (SaveMode) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            SaveMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, SaveMode.entries.size),
                    icon = {},
                ) { Text(stringResource(mode.label), maxLines = 1) }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
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

@Composable
private fun SettingsTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}
