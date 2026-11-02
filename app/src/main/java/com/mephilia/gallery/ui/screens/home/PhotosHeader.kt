package com.mephilia.gallery.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mephilia.gallery.R
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.Thumb
import com.mephilia.gallery.ui.theme.Dimens
import com.mephilia.gallery.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Width of a year card in the "on this day" row. */
private val YEAR_CARD_WIDTH = 132.dp

/** Height of a year card in the "on this day" row. */
private val YEAR_CARD_HEIGHT = 168.dp

/** Padding between a year card's edges and its text. */
private val YEAR_CARD_TEXT_PADDING = 10.dp

/** Opacity of the dark gradient at the bottom of a year card, which keeps the white text readable. */
private const val YEAR_CARD_SCRIM_ALPHA = 0.6f

/** Opacity of the item count under the year, slightly dimmer than the year itself. */
private const val YEAR_CARD_COUNT_ALPHA = 0.85f

/**
 * Banner shown at the top of the photos when the user only gave access to some of them,
 * with a button to [onRequest] access to all.
 */
@Composable
internal fun PartialAccessBanner(onRequest: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(Dimens.bannerCorner),
        modifier = Modifier.padding(Spacing.s),
    ) {
        Row(
            Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(Spacing.m))
            Text(
                stringResource(R.string.partial_access),
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRequest) { Text(stringResource(R.string.partial_access_button)) }
        }
    }
}

/**
 * Photos taken on this day in previous years: one card per year, newest first.
 * Tapping a card opens the first of that year's [items].
 */
@Composable
internal fun OnThisDayRow(items: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val thisYear = remember { LocalDate.now(zone).year }
    val byYear = remember(items) {
        items.groupBy { Instant.ofEpochMilli(it.dateTaken).atZone(zone).year }
            .toList()
            .sortedByDescending { (year, _) -> year }
    }
    Column(Modifier.padding(top = Spacing.xs, bottom = Spacing.s)) {
        Text(
            stringResource(R.string.on_this_day),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = Spacing.s, bottom = Spacing.s),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            items(byYear, key = { (year, _) -> year }) { (year, yearItems) ->
                YearCard(yearsAgo = thisYear - year, items = yearItems, onClick = { onOpen(yearItems.first()) })
            }
        }
    }
}

/** A card of the "on this day" row: the first of [items] as cover, with how many [yearsAgo] and the count. */
@Composable
private fun YearCard(yearsAgo: Int, items: List<MediaItem>, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = YEAR_CARD_WIDTH, height = YEAR_CARD_HEIGHT)
            .clip(RoundedCornerShape(Dimens.bannerCorner))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = Thumb(items.first()),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = YEAR_CARD_SCRIM_ALPHA)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(YEAR_CARD_TEXT_PADDING)) {
            Text(
                pluralStringResource(R.plurals.years_ago, yearsAgo, yearsAgo),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                pluralStringResource(R.plurals.items_count, items.size, items.size),
                color = Color.White.copy(alpha = YEAR_CARD_COUNT_ALPHA),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
