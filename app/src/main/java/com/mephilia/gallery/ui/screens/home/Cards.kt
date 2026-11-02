package com.mephilia.gallery.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mephilia.gallery.R
import com.mephilia.gallery.data.Album
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.Thumb
import com.mephilia.gallery.ui.theme.Dimens
import com.mephilia.gallery.ui.theme.Spacing

/** Covers are square. */
private const val COVER_ASPECT_RATIO = 1f

/** Opacity of the trash cover, faded so the trash icon on top of it stands out. */
private const val TRASH_COVER_ALPHA = 0.35f

/** Size of the small icon badge in the corner of a collection cover. */
private val BADGE_ICON_SIZE = 20.dp

/** Space between the badge and the corner of the cover. */
private val BADGE_MARGIN = 10.dp

/** Space between the badge icon and the edge of its round background. */
private val BADGE_PADDING = 6.dp

/** Opacity of the badge's dark round background, so the white icon shows on any photo. */
private const val BADGE_BACKGROUND_ALPHA = 0.35f

/**
 * The grid of cards used by the albums and collections tabs: as many columns as fit, with room left at the bottom
 * for the floating navigation bar.
 */
@Composable
internal fun CardGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimens.cardMinWidth),
        state = state,
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Dimens.floatingBarClearance),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardGridHorizontalGap),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardGridVerticalGap),
        modifier = modifier,
        content = content,
    )
}

/**
 * The layout shared by every card of the albums and collections tabs: a square, rounded [cover],
 * then a one-line [title] and a smaller [subtitle] (usually the item count).
 */
@Composable
private fun CoverCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    cover: @Composable BoxScope.() -> Unit,
) {
    Column(modifier.clip(RoundedCornerShape(Dimens.cardCorner)).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_ASPECT_RATIO)
                .clip(RoundedCornerShape(Dimens.cardCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
            content = cover,
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

/** A thumbnail of [item] filling the whole cover. */
@Composable
private fun CoverImage(item: MediaItem, modifier: Modifier = Modifier, description: String? = null) {
    AsyncImage(
        model = Thumb(item),
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

/** A folder, with its most recent item as cover and its item count. */
@Composable
internal fun AlbumCard(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CoverCard(album.name, "${album.count}", modifier, onClick) {
        CoverImage(album.cover, description = album.name)
    }
}

/** The trash, with a faded cover under a trash icon, and how many items it holds. */
@Composable
internal fun TrashCard(trash: List<MediaItem>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CoverCard(stringResource(R.string.trash_title), "${trash.size}", modifier, onClick) {
        trash.firstOrNull()?.let { CoverImage(it, Modifier.alpha(TRASH_COVER_ALPHA)) }
        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Dimens.largeIcon))
    }
}

/**
 * A collection that isn't a folder (favorites, on this day, locked folder…). With a [cover], its [icon] is shown
 * as a small badge in the corner; without one, the icon alone fills the card.
 */
@Composable
internal fun CollectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    cover: MediaItem?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CoverCard(title, subtitle.orEmpty(), modifier, onClick) {
        if (cover != null) {
            CoverImage(cover)
            Icon(
                icon, null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(BADGE_MARGIN)
                    .background(Color.Black.copy(alpha = BADGE_BACKGROUND_ALPHA), CircleShape)
                    .padding(BADGE_PADDING)
                    .size(BADGE_ICON_SIZE),
            )
        } else {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Dimens.largeIcon))
        }
    }
}
