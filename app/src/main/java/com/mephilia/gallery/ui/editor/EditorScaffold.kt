package com.mephilia.gallery.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.data.AppSettings
import com.mephilia.gallery.ui.components.AspectPreset
import com.mephilia.gallery.ui.screens.SystemBars
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R
import androidx.compose.foundation.shape.CircleShape

/** Squelette commun des éditeurs : barre du haut, zone d'aperçu, outils en bas. */
@Composable
fun EditorScaffold(
    title: String,
    isVideo: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    preview: @Composable () -> Unit,
    tools: @Composable ColumnScope.() -> Unit,
) {
    val settings by AppSettings.flow.collectAsStateWithLifecycle()
    val saveLabel = stringResource(if (isVideo) settings.saveModeVideo.buttonLabel else settings.saveModePhoto.buttonLabel)
    SystemBars(lightIcons = false)
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close), tint = Color.White) }
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.padding(end = 8.dp)) {
                // Le libellé reflète le réglage d'enregistrement (demander / remplacer / copie)
                Text(saveLabel)
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) { preview() }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = tools,
        )
    }
}

/** Boîte qui occupe la plus grande taille possible au ratio [ratio] dans l'espace disponible. */
@Composable
fun FitBox(ratio: Float, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val containerRatio = maxWidth / maxHeight
        val (w, h) = if (ratio > containerRatio) maxWidth to maxWidth / ratio else maxHeight * ratio to maxHeight
        Box(Modifier.size(w, h)) { content() }
    }
}

@Composable
fun AspectChips(presets: List<AspectPreset>, selected: AspectPreset, onSelect: (AspectPreset) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(presets, key = { it.label }) { p ->
            FilterChip(
                selected = p == selected,
                onClick = { onSelect(p) },
                label = { Text(p.label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    labelColor = Color.White,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = null,
            )
        }
    }
}

/** [active] : un point signale que cet outil a une modification en cours. */
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
                        .padding(top = 8.dp, end = 8.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
