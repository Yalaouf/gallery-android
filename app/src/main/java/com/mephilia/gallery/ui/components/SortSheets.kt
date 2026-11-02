package com.mephilia.gallery.ui.components

import com.mephilia.gallery.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mephilia.gallery.data.AlbumSort
import com.mephilia.gallery.data.MediaFilter
import com.mephilia.gallery.data.MediaSort
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R

/** The grid column counts offered. */
private val COLUMN_CHOICES = 2..6

/**
 * Options of the photos: sort order, photos and/or videos, and the number of columns.
 * Picking "Random" again shuffles anew.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaOptionsSheet(
    sort: MediaSort,
    filter: MediaFilter,
    columns: Int,
    onSort: (MediaSort) -> Unit,
    onFilter: (MediaFilter) -> Unit,
    onColumns: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.xl).navigationBarsPadding()) {
            SectionTitle(stringResource(R.string.sheet_sort_by))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                MediaSort.entries.forEach { s ->
                    FilterChip(
                        selected = s == sort,
                        onClick = { onSort(s) },
                        label = { Text(stringResource(s.label)) },
                        leadingIcon = when {
                            s == MediaSort.Random -> ({ Icon(Icons.Rounded.Shuffle, null) })
                            s == sort -> ({ Icon(Icons.Rounded.Check, null) })
                            else -> null
                        },
                    )
                }
            }
            SectionTitle(stringResource(R.string.sheet_show))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                MediaFilter.entries.forEach { f ->
                    FilterChip(selected = f == filter, onClick = { onFilter(f) }, label = { Text(stringResource(f.label)) })
                }
            }
            SectionTitle(stringResource(R.string.sheet_columns))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                COLUMN_CHOICES.forEach { n ->
                    FilterChip(selected = n == columns, onClick = { onColumns(n) }, label = { Text("$n") })
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * Sort options of the albums. Picking the current sort again reverses the order, shown by the arrow
 * on the selected chip.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlbumOptionsSheet(
    sort: AlbumSort,
    descending: Boolean,
    onSort: (AlbumSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.xl).navigationBarsPadding()) {
            SectionTitle(stringResource(R.string.sheet_sort_albums_by))
            Text(
                stringResource(R.string.sheet_sort_albums_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AlbumSort.entries.forEach { s ->
                    val selected = s == sort
                    FilterChip(
                        selected = selected,
                        onClick = { onSort(s) },
                        label = { Text(stringResource(s.label)) },
                        trailingIcon = if (selected) ({
                            Icon(if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward, null)
                        }) else null,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** The title of a section of the options, in the accent color. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.s),
    )
}
