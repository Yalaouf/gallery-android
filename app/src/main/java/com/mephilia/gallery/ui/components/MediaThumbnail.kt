package com.mephilia.gallery.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.Thumb
import com.mephilia.gallery.ui.theme.Spacing

/** Thumbnails are square. */
private const val THUMBNAIL_ASPECT_RATIO = 1f

/** Scale of a selected thumbnail, which shrinks a little to show it's selected. */
private const val SELECTED_SCALE = 0.86f

/** Corner radius of a thumbnail. */
private val THUMBNAIL_CORNER = 4.dp

/** Corner radius of a selected thumbnail, rounder to go with its smaller size. */
private val SELECTED_THUMBNAIL_CORNER = 14.dp

/** Space between a thumbnail's edge and its selection circle. */
private val SELECTION_MARK_MARGIN = 6.dp

/** Size of the check mark of a selected item. */
private val CHECK_SIZE = 24.dp

/** Size of the empty circle of an unselected item, a bit smaller than the check so they look the same size. */
private val EMPTY_CIRCLE_SIZE = 22.dp

/** Thickness of the empty circle's outline. */
private val EMPTY_CIRCLE_BORDER = 2.dp

/** Opacity of the dark fill inside the empty circle on a photo, so it shows on white areas. */
private const val EMPTY_CIRCLE_FILL_ALPHA = 0.2f

/** Space between the day header's selection circle and its label. */
private val HEADER_MARK_GAP = 10.dp

/** Size of the small icons on a thumbnail (favorite heart, video play symbol). */
private val OVERLAY_ICON_SIZE = 14.dp

/** Space between the favorite heart and the corner of the thumbnail. */
private val FAVORITE_MARGIN = 5.dp

/** Text size of the badge and video duration on a thumbnail. */
private val OVERLAY_TEXT_SIZE = 11.sp

/** Opacity of the dark backgrounds behind the text on a thumbnail. */
private const val OVERLAY_SCRIM_ALPHA = 0.55f

/** Corner radius of the badge's background. */
private val BADGE_CORNER = 6.dp

/** Horizontal padding inside the badge. */
private val BADGE_HORIZONTAL_PADDING = 5.dp

/** Vertical padding inside the badge. */
private val BADGE_VERTICAL_PADDING = 1.dp

/** Horizontal padding of the video duration bar. */
private val DURATION_HORIZONTAL_PADDING = 6.dp

/**
 * A square thumbnail of [item]: the picture, plus a heart for favorites, a duration for videos and an optional
 * [badge]. In [selectionMode] it shows a selection circle; when [isSelected] it also shrinks with rounder corners.
 * Long press and drag selection are handled by [MediaGrid].
 */
@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (isSelected) SELECTED_SCALE else 1f, label = "selection scale")
    Box(
        modifier
            .aspectRatio(THUMBNAIL_ASPECT_RATIO)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(if (isSelected) SELECTED_THUMBNAIL_CORNER else THUMBNAIL_CORNER))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            ThumbnailContent(item, badge)
        }
        if (selectionMode) {
            SelectionMark(isSelected, Modifier.align(Alignment.TopStart).padding(SELECTION_MARK_MARGIN))
        }
    }
}

/**
 * The header of a day in the grid. In selection mode it shows a selection circle,
 * and tapping it selects or deselects the whole day ([onToggle]).
 */
@Composable
internal fun DayHeader(
    label: String,
    selectionMode: Boolean,
    allSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = Spacing.m)
            .clip(RoundedCornerShape(Spacing.m))
            .clickable(enabled = selectionMode, onClick = onToggle)
            .padding(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SelectionMark(allSelected, Modifier.padding(end = HEADER_MARK_GAP), onPhoto = false)
        }
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * A check mark when [isSelected], otherwise an empty circle. [onPhoto] draws the circle white over a
 * slight dark fill, so it shows on any picture; otherwise it uses the theme's outline color.
 */
@Composable
private fun SelectionMark(isSelected: Boolean, modifier: Modifier = Modifier, onPhoto: Boolean = true) {
    if (isSelected) {
        Icon(
            Icons.Rounded.CheckCircle, stringResource(R.string.selected),
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
                .size(CHECK_SIZE)
                .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
        )
    } else {
        Box(
            modifier
                .size(EMPTY_CIRCLE_SIZE)
                .border(EMPTY_CIRCLE_BORDER, if (onPhoto) Color.White else MaterialTheme.colorScheme.outline, CircleShape)
                .background(if (onPhoto) Color.Black.copy(alpha = EMPTY_CIRCLE_FILL_ALPHA) else Color.Transparent, CircleShape),
        )
    }
}

/** The picture of [item] with its overlays: favorite heart, [badge], and play symbol with duration for videos. */
@Composable
private fun ThumbnailContent(item: MediaItem, badge: String?) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = Thumb(item),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.favorite) {
            Icon(
                Icons.Rounded.Favorite, null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(FAVORITE_MARGIN)
                    .size(OVERLAY_ICON_SIZE),
            )
        }
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                fontSize = OVERLAY_TEXT_SIZE,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.xs)
                    .background(Color.Black.copy(alpha = OVERLAY_SCRIM_ALPHA), RoundedCornerShape(BADGE_CORNER))
                    .padding(horizontal = BADGE_HORIZONTAL_PADDING, vertical = BADGE_VERTICAL_PADDING),
            )
        }
        if (item.isVideo) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = OVERLAY_SCRIM_ALPHA))))
                    .padding(horizontal = DURATION_HORIZONTAL_PADDING, vertical = Spacing.xs),
            ) {
                Row(Modifier.align(Alignment.BottomEnd), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(OVERLAY_ICON_SIZE))
                    Text(formatDuration(item.durationMs), color = Color.White, fontSize = OVERLAY_TEXT_SIZE, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
