package com.mephilia.gallery.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R

/** Asks before hiding [count] items in the locked folder, explaining what that means. */
@Composable
fun ConfirmLockDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        icon = Icons.Rounded.Lock,
        title = pluralStringResource(R.plurals.lock_title, count, count),
        text = stringResource(R.string.lock_text),
        confirmLabel = stringResource(R.string.action_move),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/** Asks before moving [count] items to the trash. */
@Composable
fun ConfirmTrashDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        icon = Icons.Rounded.Delete,
        title = pluralStringResource(R.plurals.trash_title, count, count),
        text = stringResource(R.string.trash_text),
        confirmLabel = stringResource(R.string.action_delete),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * Asks before deleting [count] items of the locked folder for good. They aren't in the system trash,
 * so this can't be undone.
 */
@Composable
fun ConfirmDeleteLockedDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        icon = Icons.Rounded.DeleteForever,
        title = pluralStringResource(R.plurals.delete_forever_title, count, count),
        text = stringResource(R.string.delete_locked_text),
        confirmLabel = stringResource(R.string.action_delete_forever),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/** A confirmation dialog: an [icon], a [title], an explanation [text], and the confirm and cancel buttons. */
@Composable
fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
