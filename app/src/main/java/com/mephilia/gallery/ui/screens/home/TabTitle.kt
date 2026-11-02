package com.mephilia.gallery.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/** How collapsed the large top bar must be (0 = expanded, 1 = collapsed) for the subtitle to hide. */
private const val SUBTITLE_HIDDEN_FROM = 0.5f

/**
 * Title of a tab's large top bar, with a [subtitle] (item count, sort…) shown only while the bar is mostly
 * expanded, since the collapsed bar has room for a single line.
 *
 * @param collapsedFraction how collapsed the bar is, from its scroll behavior state.
 */
@Composable
internal fun TabTitle(title: String, subtitle: String?, collapsedFraction: Float) {
    Column {
        Text(title, fontWeight = FontWeight.Bold)
        if (subtitle != null && collapsedFraction < SUBTITLE_HIDDEN_FROM) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
