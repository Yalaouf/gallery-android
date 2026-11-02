package com.mephilia.gallery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

/** Zoom of a double tap. */
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Most a picture can be zoomed in. */
private const val MAX_ZOOM = 6f

/** Above this zoom, a double tap zooms back out rather than in. */
private const val ZOOMED_IN_THRESHOLD = 1.05f

/** Above this zoom, single-finger gestures pan the picture instead of going to the pager. */
private const val PAN_THRESHOLD = 1.01f

/** Below this zoom when the fingers lift, the picture snaps back to its normal size. */
private const val SNAP_BACK_THRESHOLD = 1.02f

/** How far (in pixels) a pan must be held back by the edge for the swipe to go to the pager. */
private const val EDGE_TOLERANCE_PX = 0.5f

/**
 * Pinch and double-tap zoom container. Single-finger gestures are only intercepted when zoomed in, so the pager
 * can still swipe between items; with one finger at the picture's horizontal edge, the pager takes over too.
 * The picture never moves past its edges, and snaps back to its normal size when barely zoomed.
 *
 * @param onTap called on a single tap.
 */
@Composable
fun Zoomable(
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val size = remember { arrayOf(IntSize.Zero) }

    /** [o] limited so that, at scale [s], the picture never moves past its edges. */
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size[0].width * (s - 1f) / 2f
        val maxY = size[0].height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier
            .onSizeChanged { size[0] = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            if (scale.value > ZOOMED_IN_THRESHOLD) {
                                launch { scale.animateTo(1f) }
                                offset.animateTo(Offset.Zero)
                            } else {
                                val center = Offset(size[0].width / 2f, size[0].height / 2f)
                                val newOffset = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1f), DOUBLE_TAP_ZOOM)
                                launch { scale.animateTo(DOUBLE_TAP_ZOOM) }
                                offset.animateTo(newOffset)
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.count { it.pressed }
                        if (pointers > 1 || scale.value > PAN_THRESHOLD) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val oldScale = scale.value
                            val newScale = (oldScale * zoom).coerceIn(1f, MAX_ZOOM)
                            val center = Offset(size[0].width / 2f, size[0].height / 2f)
                            val focus = if (centroid == Offset.Unspecified) center else centroid
                            val raw = (offset.value - (focus - center)) * (newScale / oldScale) + (focus - center) + pan
                            val clamped = clamp(raw, newScale)
                            val atEdge = pointers == 1 && kotlin.math.abs(clamped.x - raw.x) > EDGE_TOLERANCE_PX &&
                                kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)
                            scope.launch {
                                scale.snapTo(newScale)
                                offset.snapTo(clamped)
                            }
                            if (!atEdge) event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale.value < SNAP_BACK_THRESHOLD) scope.launch {
                        scale.snapTo(1f)
                        offset.animateTo(Offset.Zero)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = offset.value.x
                translationY = offset.value.y
            },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
