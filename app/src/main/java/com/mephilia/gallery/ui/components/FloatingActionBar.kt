package com.mephilia.gallery.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.mephilia.gallery.ui.theme.Dimens
import com.mephilia.gallery.ui.theme.Spacing

/**
 * A pill-shaped bar floating above the content, holding [BarAction]s: the selection actions of the photos,
 * the trash and the locked folder.
 *
 * @param horizontalPadding space between the bar's ends and its actions.
 */
@Composable
fun FloatingActionBar(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = Spacing.s,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.shadow(Dimens.floatingBarShadow, CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Dimens.floatingBarElevation,
    ) {
        Row(
            Modifier.padding(horizontal = horizontalPadding, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            content = actions,
        )
    }
}

/** An action of a [FloatingActionBar]: an [icon] above a small [label]. */
@Composable
fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(Dimens.cardCorner))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
