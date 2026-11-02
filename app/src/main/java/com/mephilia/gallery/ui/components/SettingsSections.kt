package com.mephilia.gallery.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings as SystemSettings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mephilia.gallery.R
import com.mephilia.gallery.data.AppIcon
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.data.SaveMode
import com.mephilia.gallery.data.Settings
import com.mephilia.gallery.data.ThemeMode
import com.mephilia.gallery.ui.theme.Spacing

/** Slideshow delays offered, in seconds. */
private val SLIDESHOW_SECONDS_RANGE = 2..10

/** JPEG qualities offered, in percent. */
private val JPEG_QUALITY_RANGE = 70..100

/** Step between the JPEG qualities offered. */
private const val JPEG_QUALITY_STEP = 5

/** Width of the "Photos" / "Videos" label before each save mode choice, so the two choices line up. */
private val SAVE_MODE_LABEL_WIDTH = 72.dp

/** Vertical padding around each save mode choice. */
private val SAVE_MODE_ROW_PADDING = 6.dp

/** The home tabs offered at startup, by their label, in the order of [Settings.startTab]. */
private val START_TABS = listOf(R.string.tab_photos, R.string.tab_albums, R.string.tab_collections)

/** Theme, wallpaper colors (Android 12+), pure black and the launcher icon. */
@Composable
internal fun AppearanceSettings(settings: Settings) {
    val context = LocalContext.current
    SettingsTitle(stringResource(R.string.settings_appearance))
    SegmentedChoice(ThemeMode.entries, settings.theme, { stringResource(it.label) }, { mode -> AppSettings.update { it.copy(theme = mode) } })
    Spacer(Modifier.height(Spacing.xs))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchRow(stringResource(R.string.dynamic_color), stringResource(R.string.dynamic_color_desc), settings.dynamicColor) { on ->
            AppSettings.update { it.copy(dynamicColor = on) }
        }
    }
    SwitchRow(stringResource(R.string.pure_black), stringResource(R.string.pure_black_desc), settings.pureBlack) { on ->
        AppSettings.update { it.copy(pureBlack = on) }
    }

    Spacer(Modifier.height(Spacing.s))
    Text(stringResource(R.string.app_icon), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(Spacing.s))
    var appIcon by remember { mutableStateOf(AppIcon.current(context)) }
    SegmentedChoice(AppIcon.entries, appIcon, { stringResource(it.label) }, { icon ->
        if (appIcon != icon) {
            AppIcon.set(context, icon)
            appIcon = icon
        }
    })
    Text(
        stringResource(R.string.app_icon_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

/** The tab shown at launch, and "on this day" at the top of the photos. */
@Composable
internal fun StartupSettings(settings: Settings) {
    SettingsTitle(stringResource(R.string.settings_startup))
    SegmentedChoice(START_TABS.indices.toList(), settings.startTab, { stringResource(START_TABS[it]) }, { tab ->
        AppSettings.update { it.copy(startTab = tab) }
    })
    SwitchRow(stringResource(R.string.on_this_day), stringResource(R.string.on_this_day_desc), settings.showOnThisDay) { on ->
        AppSettings.update { it.copy(showOnThisDay = on) }
    }
}

/** Removing the location from shared photos. */
@Composable
internal fun PrivacySettings(settings: Settings) {
    SettingsTitle(stringResource(R.string.settings_privacy))
    SwitchRow(
        stringResource(R.string.strip_location_share),
        stringResource(R.string.strip_location_share_desc),
        settings.stripLocationOnShare,
    ) { on -> AppSettings.update { it.copy(stripLocationOnShare = on) } }
}

/** How long each item shows in a slideshow. */
@Composable
internal fun SlideshowSettings(settings: Settings) {
    SettingsTitle(stringResource(R.string.action_slideshow))
    StepSlider(
        title = stringResource(R.string.slideshow_delay),
        valueText = stringResource(R.string.seconds_short, settings.slideshowSeconds),
        value = settings.slideshowSeconds,
        range = SLIDESHOW_SECONDS_RANGE,
        step = 1,
    ) { seconds -> AppSettings.update { it.copy(slideshowSeconds = seconds) } }
}

/** Autoplay, sound and looping of videos. */
@Composable
internal fun VideoSettings(settings: Settings) {
    SettingsTitle(stringResource(R.string.settings_videos))
    SwitchRow(stringResource(R.string.autoplay), stringResource(R.string.autoplay_desc), settings.autoplayVideos) { on ->
        AppSettings.update { it.copy(autoplayVideos = on) }
    }
    SwitchRow(stringResource(R.string.always_muted), stringResource(R.string.always_muted_desc), settings.alwaysMuted) { on ->
        AppSettings.update { it.copy(alwaysMuted = on) }
    }
    SwitchRow(stringResource(R.string.loop), null, settings.loopVideos) { on ->
        AppSettings.update { it.copy(loopVideos = on) }
    }
}

/** What saving an edit does (for photos and videos), the JPEG quality, and keeping the location. */
@Composable
internal fun EditingSettings(settings: Settings) {
    SettingsTitle(stringResource(R.string.settings_editing))
    Text(stringResource(R.string.save_button_setting), style = MaterialTheme.typography.bodyLarge)
    SaveModeRow(stringResource(R.string.save_for_photos), settings.saveModePhoto) { AppSettings.setSaveModeFor(false, it) }
    SaveModeRow(stringResource(R.string.save_for_videos), settings.saveModeVideo) { AppSettings.setSaveModeFor(true, it) }
    Spacer(Modifier.height(Spacing.s))
    StepSlider(
        title = stringResource(R.string.photo_quality),
        valueText = stringResource(R.string.percent, settings.jpegQuality),
        value = settings.jpegQuality,
        range = JPEG_QUALITY_RANGE,
        step = JPEG_QUALITY_STEP,
    ) { quality -> AppSettings.update { it.copy(jpegQuality = quality) } }
    Text(
        stringResource(R.string.photo_quality_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SwitchRow(stringResource(R.string.keep_location), stringResource(R.string.keep_location_desc), settings.keepLocation) { on ->
        AppSettings.update { it.copy(keepLocation = on) }
    }
}

/** Asking before deleting, and showing the trash. */
@Composable
internal fun DeletionSettings(settings: Settings) {
    SettingsTitle(stringResource(R.string.settings_deletion))
    SwitchRow(stringResource(R.string.confirm_delete), stringResource(R.string.confirm_delete_desc), settings.confirmDelete) { on ->
        AppSettings.update { it.copy(confirmDelete = on) }
    }
    SwitchRow(stringResource(R.string.show_trash), stringResource(R.string.show_trash_desc), settings.showTrash) { on ->
        AppSettings.update { it.copy(showTrash = on) }
    }
}

/**
 * The media management permission (Android 12+), which lets the app edit, move and delete without asking each
 * time. It's granted in the system settings, so it's checked again when coming back from there.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
internal fun MediaManagementSettings() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(MediaStore.canManageMedia(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = MediaStore.canManageMedia(context) }

    SettingsTitle(stringResource(R.string.settings_media_management))
    Text(
        stringResource(R.string.media_management_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.m))
    if (allowed) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.allowed), Modifier.padding(start = Spacing.s), color = MaterialTheme.colorScheme.primary)
        }
    } else {
        FilledTonalButton(onClick = {
            context.startActivity(Intent(SystemSettings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}")))
        }) { Text(stringResource(R.string.action_allow)) }
    }
}

/** What saving an edit does for one media type ([label]): ask, replace or copy. */
@Composable
private fun SaveModeRow(label: String, selected: SaveMode, onSelect: (SaveMode) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = SAVE_MODE_ROW_PADDING), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(SAVE_MODE_LABEL_WIDTH), style = MaterialTheme.typography.bodyMedium)
        SegmentedChoice(SaveMode.entries, selected, { stringResource(it.label) }, onSelect, Modifier.weight(1f), showCheck = false)
    }
}
