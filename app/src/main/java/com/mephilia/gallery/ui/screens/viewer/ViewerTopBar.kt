package com.mephilia.gallery.ui.screens.viewer

import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

/** Color of the heart of a favorite item. */
private val FAVORITE_COLOR = Color(0xFFFF6B6B)

/** Opacity of the file name, dimmer than the date above it. */
private const val FILE_NAME_ALPHA = 0.7f

/** Opacity of the top of the gradient behind the bar, which keeps the white icons readable on light photos. */
internal const val BAR_SCRIM_ALPHA = 0.6f

/**
 * Whether [item] is a gallery file that can be favorited, renamed, copied or locked
 * (and not, say, a file opened from another app).
 */
internal fun isGalleryFile(item: MediaItem) = item.uri.authority == MediaStore.AUTHORITY

/**
 * The viewer's top bar: back, the item's date and file name, then the sound, favorite and "more" buttons
 * that apply to the [mode] and the [item].
 *
 * @param canSlideshow whether there is more than one item, so a slideshow makes sense.
 * @param onSetAs offers the item as wallpaper or contact picture.
 * @param onShowDialog opens one of the viewer's dialogs (copy, rename, lock).
 */
@Composable
internal fun ViewerTopBar(
    item: MediaItem,
    mode: ViewerMode,
    muted: Boolean,
    canSlideshow: Boolean,
    onBack: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStartSlideshow: () -> Unit,
    onSetAs: () -> Unit,
    onShowDialog: (ViewerDialog) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = BAR_SCRIM_ALPHA), Color.Transparent)))
            .statusBarsPadding()
            .padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.dateTaken)),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                item.name,
                color = Color.White.copy(alpha = FILE_NAME_ALPHA),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.isVideo) {
            IconButton(onClick = onToggleMute) {
                Icon(
                    if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    stringResource(if (muted) R.string.sound_on else R.string.sound_off),
                    tint = Color.White,
                )
            }
        }
        if (mode == ViewerMode.Gallery && isGalleryFile(item)) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (item.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(if (item.favorite) R.string.action_unfavorite else R.string.action_favorite),
                    tint = if (item.favorite) FAVORITE_COLOR else Color.White,
                )
            }
        }
        if (mode != ViewerMode.Trash) {
            MoreMenu(item, mode, canSlideshow, onStartSlideshow, onSetAs, onShowDialog)
        }
    }
}

/** The "more" button and its menu: slideshow, set as, copy, rename and hide in the locked folder. */
@Composable
private fun MoreMenu(
    item: MediaItem,
    mode: ViewerMode,
    canSlideshow: Boolean,
    onStartSlideshow: () -> Unit,
    onSetAs: () -> Unit,
    onShowDialog: (ViewerDialog) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun Entry(icon: ImageVector, label: Int, onClick: () -> Unit) = DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null) },
        onClick = {
            expanded = false
            onClick()
        },
    )

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_more), tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val inGallery = mode == ViewerMode.Gallery
            if (canSlideshow) Entry(Icons.Rounded.Slideshow, R.string.action_slideshow, onStartSlideshow)
            if (inGallery && !item.isVideo) Entry(Icons.Rounded.Wallpaper, R.string.action_set_as, onSetAs)
            if (inGallery && isGalleryFile(item)) {
                Entry(Icons.Rounded.FileCopy, R.string.action_copy_to) { onShowDialog(ViewerDialog.Copy) }
                Entry(Icons.Rounded.DriveFileRenameOutline, R.string.action_rename) { onShowDialog(ViewerDialog.Rename) }
                Entry(Icons.Rounded.Lock, R.string.action_move_to_locked) { onShowDialog(ViewerDialog.ConfirmLock) }
            }
        }
    }
}
