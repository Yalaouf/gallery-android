package com.mephilia.gallery.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Scrollbar handle on the right edge for long grids. It appears while scrolling; dragging it jumps through
 * the list and shows the month and year of the items under it.
 */
@Composable
fun FastScroller(
    state: LazyGridState,
    dateAt: (index: Int) -> Long?,
    showDates: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    val total by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
    val visible by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.size } }
    // Only worth it when the list is several screens long
    if (visible == 0 || total < visible * 4) return

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) shown = true
        else {
            delay(1500)
            shown = false
        }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "scroller")
    val scrolled by remember {
        derivedStateOf {
            val range = (state.layoutInfo.totalItemsCount - state.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
            (state.firstVisibleItemIndex.toFloat() / range).coerceIn(0f, 1f)
        }
    }
    val fraction = if (dragging) dragFraction else scrolled

    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale)
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(top = topPadding + 8.dp, bottom = bottomPadding + 8.dp)) {
        val handleHeight = 52.dp
        val track = with(density) { (maxHeight - handleHeight).toPx() }.coerceAtLeast(1f)
        val y = (fraction * track).roundToInt()
        val range = (total - visible).coerceAtLeast(1)
        // Read from the gesture without restarting it when they change mid-drag
        val currentTrack by rememberUpdatedState(track)
        val currentRange by rememberUpdatedState(range)
        val isShown by rememberUpdatedState(shown)

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, y) }
                .graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(dragging && showDates, enter = fadeIn(), exit = fadeOut()) {
                val millis = dateAt((fraction * range).roundToInt())
                if (millis != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(monthFormat)
                                .replaceFirstChar { it.titlecase(locale) },
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            // Wide touch area, thin visible handle
            Box(
                Modifier
                    .size(width = 28.dp, height = handleHeight)
                    .pointerInput(Unit) {
                        var start = 0f
                        var moved = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                if (!isShown) return@detectVerticalDragGestures
                                dragging = true
                                start = scrolled
                                moved = 0f
                                dragFraction = start
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onVerticalDrag = { change, dy ->
                                if (!dragging) return@detectVerticalDragGestures
                                change.consume()
                                moved += dy
                                dragFraction = (start + moved / currentTrack).coerceIn(0f, 1f)
                                scope.launch { state.scrollToItem((dragFraction * currentRange).roundToInt()) }
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        )
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(width = if (dragging) 8.dp else 6.dp, height = handleHeight)
                        .background(
                            if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            CircleShape,
                        ),
                )
            }
        }
    }
}
