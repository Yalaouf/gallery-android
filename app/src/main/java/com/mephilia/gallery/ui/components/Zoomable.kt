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

/**
 * Pinch / double-tap zoom container. Single-finger gestures are only intercepted when zoomed in,
 * so the pager can still swipe between items.
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
                            if (scale.value > 1.05f) {
                                launch { scale.animateTo(1f) }
                                offset.animateTo(Offset.Zero)
                            } else {
                                val target = 2.5f
                                val center = Offset(size[0].width / 2f, size[0].height / 2f)
                                val newOffset = clamp((center - tap) * (target - 1f), target)
                                launch { scale.animateTo(target) }
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
                        if (pointers > 1 || scale.value > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val oldScale = scale.value
                            val newScale = (oldScale * zoom).coerceIn(1f, 6f)
                            val center = Offset(size[0].width / 2f, size[0].height / 2f)
                            val focus = if (centroid == Offset.Unspecified) center else centroid
                            val raw = (offset.value - (focus - center)) * (newScale / oldScale) + (focus - center) + pan
                            val clamped = clamp(raw, newScale)
                            // With one finger at the horizontal edge, let the pager take over
                            val atEdge = pointers == 1 && kotlin.math.abs(clamped.x - raw.x) > 0.5f &&
                                kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)
                            scope.launch {
                                scale.snapTo(newScale)
                                offset.snapTo(clamped)
                            }
                            if (!atEdge) event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale.value < 1.02f) scope.launch {
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
