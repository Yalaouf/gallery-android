package com.mephilia.gallery.ui.components

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
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.data.AlbumSort
import com.mephilia.gallery.data.MediaFilter
import com.mephilia.gallery.data.MediaSort
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R

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
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            SectionTitle(stringResource(R.string.sheet_sort_by))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaFilter.entries.forEach { f ->
                    FilterChip(selected = f == filter, onClick = { onFilter(f) }, label = { Text(stringResource(f.label)) })
                }
            }
            SectionTitle(stringResource(R.string.sheet_columns))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..6).forEach { n ->
                    FilterChip(selected = n == columns, onClick = { onColumns(n) }, label = { Text("$n") })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlbumOptionsSheet(
    sort: AlbumSort,
    descending: Boolean,
    onSort: (AlbumSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            SectionTitle(stringResource(R.string.sheet_sort_albums_by))
            Text(
                stringResource(R.string.sheet_sort_albums_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}
