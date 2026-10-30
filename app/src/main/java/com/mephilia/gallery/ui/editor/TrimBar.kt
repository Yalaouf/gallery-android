package com.mephilia.gallery.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * [onChangeStarted] receives the grabbed handle (true = start);
 * [onChange] receives (start, end, startHandleMoved).
 */
@Composable
fun TrimBar(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    positionMs: Long,
    frames: List<Bitmap>,
    onChangeStarted: (Boolean) -> Unit,
    onChange: (Long, Long, Boolean) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val handleW = with(density) { 14.dp.toPx() }
    val minGap = minOf(500L, durationMs)
    val start by rememberUpdatedState(startMs)
    val end by rememberUpdatedState(endMs)
    val started by rememberUpdatedState(onChangeStarted)
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    val dragging = remember { arrayOf<Boolean?>(null) } // true = start handle, false = end handle

    Box(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            // The handles sit near the screen edges: keep the back gesture from stealing them
            .systemGestureExclusion()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            frames.forEach { f ->
                Image(
                    f.asImageBitmap(), null,
                    Modifier.weight(1f).fillMaxHeight(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(durationMs) {
                    val track = size.width - 2 * handleW
                    fun xOf(ms: Long) = handleW + track * ms / durationMs.coerceAtLeast(1)
                    detectDragGestures(
                        onDragStart = { p ->
                            val isStart = abs(p.x - xOf(start)) <= abs(p.x - xOf(end))
                            dragging[0] = isStart
                            started(isStart)
                        },
                        onDragEnd = { dragging[0] = null; finished() },
                        onDragCancel = { dragging[0] = null; finished() },
                    ) { c, drag ->
                        val isStart = dragging[0] ?: return@detectDragGestures
                        c.consume()
                        val deltaMs = (drag.x / track * durationMs).toLong()
                        if (isStart) {
                            change((start + deltaMs).coerceIn(0, end - minGap), end, true)
                        } else {
                            change(start, (end + deltaMs).coerceIn(start + minGap, durationMs), false)
                        }
                    }
                },
        ) {
            val track = size.width - 2 * handleW
            val d = durationMs.coerceAtLeast(1).toFloat()
            val sx = handleW + track * startMs / d
            val ex = handleW + track * endMs / d
            val dim = Color.Black.copy(alpha = 0.6f)
            val top = 4.dp.toPx()
            val h = size.height - 2 * top
            drawRect(dim, Offset(handleW, top), Size(sx - handleW, h))
            drawRect(dim, Offset(ex, top), Size(size.width - handleW - ex, h))
            drawRoundRect(
                accent, Offset(sx - handleW, 0f), Size(ex - sx + 2 * handleW, size.height),
                CornerRadius(10.dp.toPx()), style = Stroke(3.dp.toPx()),
            )
            drawRoundRect(accent, Offset(sx - handleW, 0f), Size(handleW, size.height), CornerRadius(10.dp.toPx()))
            drawRoundRect(accent, Offset(ex, 0f), Size(handleW, size.height), CornerRadius(10.dp.toPx()))
            val grip = Color.Black.copy(alpha = 0.5f)
            val gh = size.height * 0.3f
            drawLine(grip, Offset(sx - handleW / 2, (size.height - gh) / 2), Offset(sx - handleW / 2, (size.height + gh) / 2), 2.dp.toPx())
            drawLine(grip, Offset(ex + handleW / 2, (size.height - gh) / 2), Offset(ex + handleW / 2, (size.height + gh) / 2), 2.dp.toPx())
            val px = handleW + track * positionMs.coerceIn(startMs, endMs) / d
            drawLine(Color.White, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
        }
    }
}
