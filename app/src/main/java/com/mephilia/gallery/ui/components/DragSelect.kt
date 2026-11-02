package com.mephilia.gallery.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.data.MediaItem

/** Height of the zone at the top of the grid where holding the finger scrolls up. */
private val TOP_SCROLL_ZONE = 96.dp

/** Height of the zone at the bottom of the grid where holding the finger scrolls down; it also covers the floating bar. */
private val BOTTOM_SCROLL_ZONE = 160.dp

/** Scroll speed per frame with the finger at the very edge of the screen. */
private val MAX_SCROLL_SPEED = 14.dp

/** Share of the maximum speed reached as soon as the finger enters a scroll zone. */
private const val MIN_SPEED_FRACTION = 0.3f

/** Seconds of holding in a scroll zone for each extra 1× of speed. */
private const val SECONDS_PER_SPEED_STEP = 0.75f

/** The most extra speed gained by holding: up to 3× in total, after 1.5 seconds. */
private const val MAX_EXTRA_SPEED = 2f

/** Nanoseconds in a second. */
private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * Long-press then drag to select every item between the one pressed and the one under the finger.
 * While the finger is held near the top or bottom edge, the grid scrolls by itself, faster near the edge and
 * the longer it's held, so long lists can be selected in one go.
 *
 * A gesture that moves or lifts before the long-press delay is left alone, so the grid scrolls and items are
 * clicked normally. Once drag selection starts, every event is consumed: no click and no manual scroll.
 *
 * @param items the items in display order.
 * @param itemAt the item under a position in the grid, if any.
 */
@Composable
internal fun rememberDragSelectModifier(
    state: LazyGridState,
    items: List<MediaItem>,
    selection: MediaSelection?,
    itemAt: (Offset) -> MediaItem?,
): Modifier {
    if (selection == null) return Modifier
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val topZone = with(density) { TOP_SCROLL_ZONE.toPx() }
    val bottomZone = with(density) { BOTTOM_SCROLL_ZONE.toPx() }
    val maxSpeed = with(density) { MAX_SCROLL_SPEED.toPx() }
    var autoScroll by remember { mutableFloatStateOf(0f) }
    val lastPointer = remember { arrayOf(Offset.Unspecified) }

    LaunchedEffect(autoScroll != 0f) {
        val startNanos = withFrameNanos { it }
        while (autoScroll != 0f) {
            val heldSeconds = (withFrameNanos { it } - startNanos) / NANOS_PER_SECOND
            val boost = 1f + (heldSeconds / SECONDS_PER_SPEED_STEP).coerceAtMost(MAX_EXTRA_SPEED)
            state.scrollBy(autoScroll * boost)
            if (lastPointer[0] != Offset.Unspecified) itemAt(lastPointer[0])?.let(selection::updateDrag)
        }
    }

    fun speedFor(y: Float, height: Int): Float {
        fun ramp(distanceToEdge: Float, zone: Float): Float {
            val depth = 1f - (distanceToEdge / zone).coerceIn(0f, 1f)
            return maxSpeed * (MIN_SPEED_FRACTION + (1f - MIN_SPEED_FRACTION) * depth)
        }
        return when {
            y < topZone -> -ramp(y, topZone)
            y > height - bottomZone -> ramp(height - y, bottomZone)
            else -> 0f
        }
    }

    return Modifier.pointerInput(items) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!awaitStillLongPress(down)) return@awaitEachGesture
            val start = itemAt(down.position) ?: return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            selection.startDrag(start, items)
            lastPointer[0] = down.position
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    event.changes.forEach { it.consume() }
                    if (!change.pressed) break
                    lastPointer[0] = change.position
                    itemAt(change.position)?.let(selection::updateDrag)
                    autoScroll = speedFor(change.position.y, size.height)
                }
            } finally {
                autoScroll = 0f
                lastPointer[0] = Offset.Unspecified
                selection.endDrag()
            }
        }
    }
}

/**
 * Waits for the long-press delay and returns true if the finger stayed down and still during it.
 * Returns false as soon as it lifts, moves past the touch slop, or a second finger comes down.
 */
private suspend fun AwaitPointerEventScope.awaitStillLongPress(down: PointerInputChange): Boolean {
    val interrupted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val moved = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            if (!change.pressed || event.changes.size > 1 || moved) break
        }
        true
    }
    return interrupted == null
}
