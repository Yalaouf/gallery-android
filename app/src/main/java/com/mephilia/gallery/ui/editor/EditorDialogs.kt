package com.mephilia.gallery.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.mephilia.gallery.R
import com.mephilia.gallery.ui.theme.Spacing

/** Converts a progress fraction to a percentage. */
private const val PERCENT = 100

/**
 * A dialog showing the progress of a long task (saving, creating a GIF). It can't be dismissed by tapping
 * outside or pressing back, only with the cancel button when [onCancel] is given.
 *
 * @param progress from 0 to 1, or null when unknown (an indeterminate bar is shown).
 */
@Composable
fun ProgressDialog(title: String, progress: Float?, onCancel: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column {
                if (progress == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.s))
                    Text(stringResource(R.string.percent, (progress * PERCENT).toInt()), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = onCancel?.let { { TextButton(onClick = it) { Text(stringResource(R.string.action_cancel)) } } },
    )
}

/** A dialog telling that a task failed, with the error [message]. */
@Composable
fun FailureDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/** A readable message for an error, falling back to its type when it has no message. */
fun Throwable.readableMessage(): String = message ?: javaClass.simpleName
