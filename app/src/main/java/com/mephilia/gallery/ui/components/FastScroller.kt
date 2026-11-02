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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.mephilia.gallery.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The scroller only shows on lists at least this many screens long. */
private const val MIN_SCREENS = 4

/** How long the handle stays after scrolling stops. */
private const val HIDE_DELAY_MS = 1500L

/** Height of the handle. */
private val HANDLE_HEIGHT = 52.dp

/** Width of the handle's touch area, much wider than what's drawn. */
private val HANDLE_TOUCH_WIDTH = 28.dp

/** Drawn width of the handle. */
private val HANDLE_WIDTH = 6.dp

/** Drawn width of the handle while dragged. */
private val HANDLE_DRAGGED_WIDTH = 8.dp

/** Opacity of the handle when not dragged. */
private const val HANDLE_ALPHA = 0.6f

/** Corner radius of the date bubble. */
private val BUBBLE_CORNER = 16.dp

/** Shadow under the date bubble. */
private val BUBBLE_ELEVATION = 4.dp

/** Horizontal padding inside the date bubble. */
private val BUBBLE_HORIZONTAL_PADDING = 14.dp

/** Date pattern skeleton of the bubble: month and year. */
private const val MONTH_SKELETON = "MMMMyyyy"

/**
 * A scrollbar handle on the right edge of long grids. It appears while scrolling and fades out shortly after;
 * dragging it jumps through the list and, with [showDates], shows the month and year of the items under it.
 * It only shows on lists several screens long.
 *
 * @param dateAt the date of the grid entry at an index, for the bubble.
 * @param topPadding space kept free above the handle's track.
 * @param bottomPadding space kept free under the handle's track.
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
    if (visible == 0 || total < visible * MIN_SCREENS) return

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            shown = true
        } else {
            delay(HIDE_DELAY_MS)
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

    BoxWithConstraints(Modifier.fillMaxSize().padding(top = topPadding + Spacing.s, bottom = bottomPadding + Spacing.s)) {
        val track = with(density) { (maxHeight - HANDLE_HEIGHT).toPx() }.coerceAtLeast(1f)
        val range = (total - visible).coerceAtLeast(1)
        val currentTrack by rememberUpdatedState(track)
        val currentRange by rememberUpdatedState(range)
        val isShown by rememberUpdatedState(shown)

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * track).roundToInt()) }
                .graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(dragging && showDates, enter = fadeIn(), exit = fadeOut()) {
                dateAt((fraction * range).roundToInt())?.let { DateBubble(it) }
            }
            Box(
                Modifier
                    .size(width = HANDLE_TOUCH_WIDTH, height = HANDLE_HEIGHT)
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
                        .padding(end = Spacing.xs)
                        .size(width = if (dragging) HANDLE_DRAGGED_WIDTH else HANDLE_WIDTH, height = HANDLE_HEIGHT)
                        .background(
                            if (dragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HANDLE_ALPHA),
                            CircleShape,
                        ),
                )
            }
        }
    }
}

/** The bubble next to the dragged handle, with the month and year of [millis]. */
@Composable
private fun DateBubble(millis: Long) {
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, MONTH_SKELETON), locale)
    }
    Surface(
        shape = RoundedCornerShape(BUBBLE_CORNER),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = BUBBLE_ELEVATION,
        modifier = Modifier.padding(end = Spacing.s),
    ) {
        Text(
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(monthFormat).replaceFirstChar { it.titlecase(locale) },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = BUBBLE_HORIZONTAL_PADDING, vertical = Spacing.s),
        )
    }
}
