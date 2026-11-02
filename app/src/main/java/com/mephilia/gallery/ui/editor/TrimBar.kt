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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mephilia.gallery.ui.theme.Spacing
import kotlin.math.abs

/** Height of the trim bar. */
private val BAR_HEIGHT = 60.dp

/** Width of a handle. */
private val HANDLE_WIDTH = 14.dp

/** Space above and below the frames, where the selection outline shows. */
private val FRAMES_VERTICAL_INSET = 4.dp

/** Corner radius of the frame strip. */
private val FRAMES_CORNER = 8.dp

/** Corner radius of the handles and the selection outline. */
private val HANDLE_CORNER = 10.dp

/** Thickness of the selection outline. */
private val OUTLINE_WIDTH = 3.dp

/** Thickness of the grip lines on the handles and of the playback position line. */
private val LINE_WIDTH = 2.dp

/** Height of the grip lines, as a share of the bar's height. */
private const val GRIP_HEIGHT_FRACTION = 0.3f

/** Opacity of the frame strip's background, shown while the frames load. */
private const val FRAMES_BACKGROUND_ALPHA = 0.08f

/** Opacity of the dark veil over the parts trimmed away. */
private const val TRIMMED_VEIL_ALPHA = 0.6f

/** Opacity of the grip lines. */
private const val GRIP_ALPHA = 0.5f

/** Shortest selection, in milliseconds (or the whole video if it's shorter). */
private const val MIN_SELECTION_MS = 500L

/**
 * The trim bar of the video editor: a strip of [frames] with two handles to choose the part kept, between
 * [startMs] and [endMs], and a line at the playback position. The handles sit near the screen edges, so the
 * system back gesture is kept from stealing them. A drag moves the handle nearest to where it started.
 *
 * @param onChangeStarted a drag started; true if it moves the start handle, false for the end one.
 * @param onChange the new start and end while dragging, and whether the start handle is the one moving.
 * @param onChangeFinished the drag ended.
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
    val handleWidth = with(LocalDensity.current) { HANDLE_WIDTH.toPx() }
    val minGap = minOf(MIN_SELECTION_MS, durationMs)
    val start by rememberUpdatedState(startMs)
    val end by rememberUpdatedState(endMs)
    val started by rememberUpdatedState(onChangeStarted)
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    val draggingStart = remember { arrayOf<Boolean?>(null) }

    Box(
        modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .systemGestureExclusion()
            .padding(horizontal = Spacing.l),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = HANDLE_WIDTH, vertical = FRAMES_VERTICAL_INSET)
                .clip(RoundedCornerShape(FRAMES_CORNER))
                .background(Color.White.copy(alpha = FRAMES_BACKGROUND_ALPHA)),
        ) {
            frames.forEach { frame ->
                Image(frame.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(durationMs) {
                    val track = size.width - 2 * handleWidth
                    fun xOf(ms: Long) = handleWidth + track * ms / durationMs.coerceAtLeast(1)
                    fun stop() {
                        draggingStart[0] = null
                        finished()
                    }
                    detectDragGestures(
                        onDragStart = { position ->
                            val isStart = abs(position.x - xOf(start)) <= abs(position.x - xOf(end))
                            draggingStart[0] = isStart
                            started(isStart)
                        },
                        onDragEnd = ::stop,
                        onDragCancel = ::stop,
                    ) { pointer, drag ->
                        val isStart = draggingStart[0] ?: return@detectDragGestures
                        pointer.consume()
                        val deltaMs = (drag.x / track * durationMs).toLong()
                        if (isStart) change((start + deltaMs).coerceIn(0, end - minGap), end, true)
                        else change(start, (end + deltaMs).coerceIn(start + minGap, durationMs), false)
                    }
                },
        ) {
            val track = size.width - 2 * handleWidth
            val duration = durationMs.coerceAtLeast(1).toFloat()
            fun xOf(ms: Long) = handleWidth + track * ms / duration
            drawSelection(xOf(startMs), xOf(endMs), handleWidth, accent)
            val positionX = xOf(positionMs.coerceIn(startMs, endMs))
            drawLine(Color.White, Offset(positionX, 0f), Offset(positionX, size.height), LINE_WIDTH.toPx())
        }
    }
}

/**
 * Draws the selection between [startX] and [endX]: a veil over the trimmed parts, the outline,
 * and the two handles with their grips.
 */
private fun DrawScope.drawSelection(startX: Float, endX: Float, handleWidth: Float, accent: Color) {
    val veil = Color.Black.copy(alpha = TRIMMED_VEIL_ALPHA)
    val inset = FRAMES_VERTICAL_INSET.toPx()
    val framesHeight = size.height - 2 * inset
    drawRect(veil, Offset(handleWidth, inset), Size(startX - handleWidth, framesHeight))
    drawRect(veil, Offset(endX, inset), Size(size.width - handleWidth - endX, framesHeight))

    val corner = CornerRadius(HANDLE_CORNER.toPx())
    drawRoundRect(
        accent, Offset(startX - handleWidth, 0f), Size(endX - startX + 2 * handleWidth, size.height),
        corner, style = Stroke(OUTLINE_WIDTH.toPx()),
    )
    drawRoundRect(accent, Offset(startX - handleWidth, 0f), Size(handleWidth, size.height), corner)
    drawRoundRect(accent, Offset(endX, 0f), Size(handleWidth, size.height), corner)

    val grip = Color.Black.copy(alpha = GRIP_ALPHA)
    val gripHeight = size.height * GRIP_HEIGHT_FRACTION
    val gripTop = (size.height - gripHeight) / 2
    val gripBottom = (size.height + gripHeight) / 2
    for (x in listOf(startX - handleWidth / 2, endX + handleWidth / 2)) {
        drawLine(grip, Offset(x, gripTop), Offset(x, gripBottom), LINE_WIDTH.toPx())
    }
}
