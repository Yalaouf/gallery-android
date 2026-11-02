package com.mephilia.gallery.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.ui.theme.Spacing

/** Size of the icon of an empty state. */
private val EMPTY_STATE_ICON_SIZE = 56.dp

/** Placeholder filling a screen that has nothing to show: a large, muted [icon] above a short [text]. */
@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(EMPTY_STATE_ICON_SIZE), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(Spacing.m))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
