package com.mephilia.gallery.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.ui.components.MediaAccess
import com.mephilia.gallery.ui.screens.TrashActionsBar
import com.mephilia.gallery.ui.theme.Dimens
import com.mephilia.gallery.ui.theme.Spacing

/** Opacity of the bottom of the gradient behind the action bar, which keeps the white labels readable. */
private const val ACTIONS_SCRIM_ALPHA = 0.7f

/**
 * The viewer's bottom bar, with the actions available in the [mode]. Its height is reported through
 * [onHeightChange], so the video controls can sit right above it instead of under it.
 *
 * @param onRestoreFromLocked takes a locked item back to the gallery.
 * @param onDelete moves a gallery item to the trash (asking first if the settings say so).
 * @param onShowDialog opens one of the viewer's dialogs (move, details, delete for good).
 */
@Composable
internal fun ViewerBottomBar(
    item: MediaItem,
    mode: ViewerMode,
    access: MediaAccess,
    onHeightChange: (Dp) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRestoreFromLocked: () -> Unit,
    onDelete: () -> Unit,
    onShowDialog: (ViewerDialog) -> Unit,
) {
    val density = LocalDensity.current
    when (mode) {
        ViewerMode.Trash -> TrashActionsBar(
            items = listOf(item),
            access = access,
            onSelectAll = null,
            onDone = {},
            modifier = Modifier
                .onSizeChanged { onHeightChange(with(density) { it.height.toDp() } + Dimens.floatingBarBottomMargin) }
                .navigationBarsPadding()
                .padding(bottom = Dimens.floatingBarBottomMargin),
        )
        ViewerMode.Locked -> ActionRow(onHeightChange) {
            ViewerAction(Icons.Rounded.LockOpen, stringResource(R.string.action_unlock_item), onRestoreFromLocked)
            ViewerAction(Icons.Rounded.Info, stringResource(R.string.action_info)) { onShowDialog(ViewerDialog.Info) }
            ViewerAction(Icons.Rounded.DeleteForever, stringResource(R.string.action_delete_forever)) {
                onShowDialog(ViewerDialog.ConfirmDeleteLocked)
            }
        }
        ViewerMode.Gallery -> ActionRow(onHeightChange) {
            ViewerAction(Icons.Rounded.Share, stringResource(R.string.action_share), onShare)
            ViewerAction(Icons.Rounded.Edit, stringResource(R.string.action_edit), onEdit)
            ViewerAction(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.action_move)) {
                onShowDialog(ViewerDialog.Move)
            }
            ViewerAction(Icons.Rounded.Info, stringResource(R.string.action_info)) { onShowDialog(ViewerDialog.Info) }
            ViewerAction(Icons.Rounded.Delete, stringResource(R.string.action_delete), onDelete)
        }
    }
}

/** A full-width row of [ViewerAction]s over a dark gradient, reporting its height through [onHeightChange]. */
@Composable
private fun ActionRow(onHeightChange: (Dp) -> Unit, actions: @Composable RowScope.() -> Unit) {
    val density = LocalDensity.current
    Row(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightChange(with(density) { it.height.toDp() }) }
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = ACTIONS_SCRIM_ALPHA))))
            .navigationBarsPadding()
            .padding(top = Spacing.xl, bottom = Spacing.s),
        horizontalArrangement = Arrangement.SpaceEvenly,
        content = actions,
    )
}

/** An action of the bottom bar: an [icon] above its [label]. */
@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White)
        Spacer(Modifier.height(Spacing.xs))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
