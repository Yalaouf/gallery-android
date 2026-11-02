package com.mephilia.gallery.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mephilia.gallery.R
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.ui.components.SystemBars
import com.mephilia.gallery.ui.theme.Spacing

/**
 * The layout shared by the photo, GIF and video editors, on a black background: a top bar (close, [title],
 * extra [actions] and the save button), the [preview] filling the middle, and the [tools] at the bottom.
 *
 * The save button reads "Save", "Save as copy"… depending on the save mode chosen in the settings,
 * separately for photos and videos ([isVideo]).
 */
@Composable
fun EditorScaffold(
    title: String,
    isVideo: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    preview: @Composable () -> Unit,
    tools: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val settings by AppSettings.flow.collectAsStateWithLifecycle()
    val saveMode = if (isVideo) settings.saveModeVideo else settings.saveModePhoto
    SystemBars(lightIcons = false)
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close), tint = Color.White) }
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            actions()
            Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.padding(end = Spacing.s)) {
                Text(stringResource(saveMode.buttonLabel))
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(Spacing.xl),
            contentAlignment = Alignment.Center,
        ) { preview() }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            content = tools,
        )
    }
}
