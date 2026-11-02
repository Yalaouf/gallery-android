package com.mephilia.gallery.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.R
import com.mephilia.gallery.ui.theme.Dimens
import com.mephilia.gallery.ui.theme.Spacing

/** Space between the bar's edge and its pills. */
private val NAV_BAR_PADDING = 6.dp

/** Opacity of the bar's background: nearly opaque, with a hint of the content behind. */
private const val NAV_BAR_BACKGROUND_ALPHA = 0.96f

/** Horizontal padding inside a pill. */
private val PILL_HORIZONTAL_PADDING = 18.dp

/** The icon shown for each tab in the navigation bar. */
private val HomeTab.icon: ImageVector
    get() = when (this) {
        HomeTab.Photos -> Icons.Rounded.Collections
        HomeTab.Albums -> Icons.Rounded.Folder
        HomeTab.Collections -> Icons.Rounded.CollectionsBookmark
    }

/** String resource of each tab's label. */
private val HomeTab.label: Int
    get() = when (this) {
        HomeTab.Photos -> R.string.tab_photos
        HomeTab.Albums -> R.string.tab_albums
        HomeTab.Collections -> R.string.tab_collections
    }

/**
 * The navigation bar floating at the bottom of the home screen: a pill per tab, the [selected] one highlighted
 * and labelled. [onSelect] is called for every tap, including on the tab that is already selected.
 */
@Composable
internal fun FloatingNavBar(selected: HomeTab, onSelect: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.shadow(Dimens.floatingBarShadow, CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = NAV_BAR_BACKGROUND_ALPHA),
        tonalElevation = Dimens.floatingBarElevation,
    ) {
        Row(Modifier.padding(NAV_BAR_PADDING), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            HomeTab.entries.forEach { tab ->
                NavPill(tab.icon, stringResource(tab.label), selected == tab) { onSelect(tab) }
            }
        }
    }
}

/**
 * A tab of the navigation bar: just its [icon] when not [selected]; when selected, a colored pill
 * that also shows the [label], both animated.
 */
@Composable
private fun NavPill(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "pill background",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pill content",
    )
    Row(
        Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = PILL_HORIZONTAL_PADDING, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = content)
        AnimatedVisibility(selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            Row {
                Spacer(Modifier.width(Spacing.s))
                Text(label, color = content, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
