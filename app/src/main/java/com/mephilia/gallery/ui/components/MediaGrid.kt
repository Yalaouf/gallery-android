package com.mephilia.gallery.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mephilia.gallery.data.MediaItem
import com.mephilia.gallery.data.Thumb
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.mephilia.gallery.R
import androidx.compose.ui.platform.LocalConfiguration
import android.text.format.DateFormat

private sealed interface GridEntry {
    /** [ids] = éléments du jour, pour tout sélectionner d'un coup depuis l'en-tête. */
    class Header(val label: String, val key: String, val ids: List<Long>) : GridEntry
    data class Cell(val item: MediaItem) : GridEntry
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    groupByDate: Boolean,
    state: LazyGridState,
    contentPadding: PaddingValues,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    selection: MediaSelection? = null,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val today = stringResource(R.string.today)
    val yesterday = stringResource(R.string.yesterday)
    val locale = LocalConfiguration.current.locales[0]
    val entries = remember(items, groupByDate, locale) { buildEntries(items, groupByDate, today, yesterday, locale) }
    val byId = remember(items) { items.associateBy { it.id } }
    val selected = selection?.ids.orEmpty()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    // Zones de défilement automatique : celle du bas couvre aussi la barre d'actions flottante
    val topEdge = with(density) { 96.dp.toPx() }
    val bottomEdge = with(density) { 160.dp.toPx() }
    val maxSpeed = with(density) { 14.dp.toPx() }
    // Défilement automatique quand le doigt approche du haut ou du bas pendant un glissement
    var autoScroll by remember { mutableFloatStateOf(0f) }
    val lastPointer = remember { arrayOf(Offset.Unspecified) }

    fun itemAt(p: Offset): MediaItem? {
        val info = state.layoutInfo
        val y = p.y + info.viewportStartOffset
        val hit = info.visibleItemsInfo.firstOrNull {
            p.x >= it.offset.x && p.x < it.offset.x + it.size.width &&
                y >= it.offset.y && y < it.offset.y + it.size.height
        } ?: return null
        return byId[hit.key as? Long ?: return null]
    }

    LaunchedEffect(autoScroll != 0f) {
        val startNanos = withFrameNanos { it }
        while (autoScroll != 0f) {
            // Accélère tant qu'on reste dans la zone (jusqu'à ×3 après 1,5 s) pour parcourir de longues listes
            val held = (withFrameNanos { it } - startNanos) / 1_000_000_000f
            state.scrollBy(autoScroll * (1f + (held / 0.75f).coerceAtMost(2f)))
            if (lastPointer[0] != Offset.Unspecified) itemAt(lastPointer[0])?.let { selection?.updateDrag(it) }
        }
    }

    val dragModifier = if (selection == null) Modifier else Modifier.pointerInput(items) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Attend un appui long sans mouvement ; sinon on laisse défiler / cliquer normalement
            val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                    if (!ch.pressed || ev.changes.size > 1) return@withTimeoutOrNull true
                    if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull true
                    }
                }
                @Suppress("UNREACHABLE_CODE") true
            }
            if (cancelled == true) return@awaitEachGesture
            val start = itemAt(down.position) ?: return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            selection.startDrag(start, items)
            lastPointer[0] = down.position
            try {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    // On consomme tout : ni clic sur la vignette, ni défilement manuel pendant la sélection
                    ev.changes.forEach { it.consume() }
                    if (!ch.pressed) break
                    lastPointer[0] = ch.position
                    itemAt(ch.position)?.let(selection::updateDrag)
                    val h = size.height
                    // Vitesse minimale dès l'entrée dans la zone, maximale au bord (ou au-delà)
                    autoScroll = when {
                        ch.position.y < topEdge ->
                            -maxSpeed * (0.3f + 0.7f * (1f - (ch.position.y / topEdge).coerceIn(0f, 1f)))
                        ch.position.y > h - bottomEdge ->
                            maxSpeed * (0.3f + 0.7f * (1f - ((h - ch.position.y) / bottomEdge).coerceIn(0f, 1f)))
                        else -> 0f
                    }
                }
            } finally {
                autoScroll = 0f
                lastPointer[0] = Offset.Unspecified
                selection.endDrag()
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxSize().then(dragModifier),
    ) {
        header?.invoke(this)
        items(
            entries,
            key = { e -> if (e is GridEntry.Header) e.key else (e as GridEntry.Cell).item.id },
            span = { e -> if (e is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { e -> e::class },
        ) { e ->
            when (e) {
                is GridEntry.Header -> DayHeader(
                    label = e.label,
                    selectionMode = selected.isNotEmpty(),
                    allSelected = e.ids.all { it in selected },
                    onToggle = { selection?.toggleAll(e.ids) },
                    modifier = Modifier.animateItem(),
                )
                is GridEntry.Cell -> MediaThumbnail(
                    item = e.item,
                    modifier = Modifier.animateItem(),
                    selectionMode = selected.isNotEmpty(),
                    isSelected = e.item.id in selected,
                ) { onClick(e.item) }
            }
        }
    }
}

@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val inset by animateFloatAsState(if (isSelected) 0.86f else 1f, label = "inset")
    // L'appui long (et le glissement) est géré par la grille, voir MediaGrid
    Box(
        modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = inset
                    scaleY = inset
                }
                .clip(RoundedCornerShape(if (isSelected) 14.dp else 4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            ThumbnailContent(item)
        }
        if (selectionMode) {
            SelectionMark(isSelected, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
    }
}

/** En-tête de jour ; en mode sélection, le toucher (dé)sélectionne toute la journée. */
@Composable
private fun DayHeader(
    label: String,
    selectionMode: Boolean,
    allSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = selectionMode, onClick = onToggle)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SelectionMark(allSelected, Modifier.padding(end = 10.dp), onPhoto = false)
        }
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SelectionMark(isSelected: Boolean, modifier: Modifier = Modifier, onPhoto: Boolean = true) {
    if (isSelected) {
        Icon(
            Icons.Rounded.CheckCircle, stringResource(R.string.selected),
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
        )
    } else {
        Box(
            modifier
                .size(22.dp)
                // Sur une photo : cercle blanc ; sur le fond de l'app : couleur du thème
                .border(2.dp, if (onPhoto) Color.White else MaterialTheme.colorScheme.outline, CircleShape)
                .background(if (onPhoto) Color.Black.copy(alpha = 0.2f) else Color.Transparent, CircleShape),
        )
    }
}

@Composable
private fun ThumbnailContent(item: MediaItem) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = Thumb(item),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Row(Modifier.align(Alignment.BottomEnd), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(formatDuration(item.durationMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun buildEntries(
    items: List<MediaItem>,
    groupByDate: Boolean,
    todayLabel: String,
    yesterdayLabel: String,
    locale: Locale,
): List<GridEntry> {
    if (!groupByDate) return items.map { GridEntry.Cell(it) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    // Formats de date dans la langue du téléphone (ordre jour/mois adapté à chaque langue)
    val dayFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    val dayYearFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy"), locale)
    val out = ArrayList<GridEntry>(items.size + 64)
    var lastDay: LocalDate? = null
    var dayIds = ArrayList<Long>()
    for (item in items) {
        val day = Instant.ofEpochMilli(item.dateTaken).atZone(zone).toLocalDate()
        if (day != lastDay) {
            lastDay = day
            val label = when {
                day == today -> todayLabel
                day == today.minusDays(1) -> yesterdayLabel
                day.year == today.year -> day.format(dayFormat)
                else -> day.format(dayYearFormat)
            }.replaceFirstChar { it.titlecase(locale) }
            dayIds = ArrayList()
            out += GridEntry.Header(label, "h:$day", dayIds)
        }
        dayIds += item.id
        out += GridEntry.Cell(item)
    }
    return out
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
