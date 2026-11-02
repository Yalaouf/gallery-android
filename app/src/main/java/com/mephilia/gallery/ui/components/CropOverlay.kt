package com.mephilia.gallery.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** How close to a corner or edge a touch must start to grab it. */
private val TOUCH_RADIUS = 28.dp

/** Smallest crop side, so the crop stays easy to grab. */
private val MIN_CROP_SIZE = 48.dp

/** Opacity of the veil over the parts cropped away. */
private const val VEIL_ALPHA = 0.55f

/** Opacity of the rule-of-thirds grid lines. */
private const val GRID_ALPHA = 0.35f

/** The grid divides the crop in thirds. */
private const val GRID_DIVISIONS = 3

/** Thickness of the grid lines. */
private val GRID_LINE_WIDTH = 1.dp

/** Thickness of the crop's outline. */
private val OUTLINE_WIDTH = 1.5.dp

/** Length of each arm of a corner handle. */
private val CORNER_LENGTH = 20.dp

/** Thickness of the corner handles. */
private val CORNER_WIDTH = 4.dp

/** What a drag on the crop moves: a corner, an edge, or the whole crop. */
private enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight, Left, Top, Right, Bottom, Move }

/**
 * Crop overlay, laid out exactly over the content (same size). Corners resize the crop, edges too in a free crop,
 * and dragging inside moves it. The parts cropped away are veiled, and a rule-of-thirds grid helps composing.
 * Its edges sit near the screen's, so the system back gesture is kept from stealing them.
 *
 * @param crop the crop, normalized (0 to 1) to the content.
 * @param ratio the enforced width / height ratio, or null for a free crop.
 */
@Composable
fun CropOverlay(
    crop: Rect,
    onCropChange: (Rect) -> Unit,
    ratio: Float?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val touchRadius = with(density) { TOUCH_RADIUS.toPx() }
    val minSize = with(density) { MIN_CROP_SIZE.toPx() }
    val currentCrop by rememberUpdatedState(crop)
    val currentRatio by rememberUpdatedState(ratio)
    val onChange by rememberUpdatedState(onCropChange)
    val handle = remember { arrayOf<Handle?>(null) }

    Canvas(
        modifier.systemGestureExclusion().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { position ->
                    val rect = currentCrop.scaled(size.width.toFloat(), size.height.toFloat())
                    handle[0] = pickHandle(rect, position, touchRadius, edgesAllowed = currentRatio == null)
                },
                onDragEnd = { handle[0] = null },
                onDragCancel = { handle[0] = null },
            ) { change, drag ->
                val grabbed = handle[0] ?: return@detectDragGestures
                change.consume()
                val width = size.width.toFloat()
                val height = size.height.toFloat()
                val updated = dragRect(currentCrop.scaled(width, height), grabbed, drag, width, height, minSize, currentRatio)
                onChange(Rect(updated.left / width, updated.top / height, updated.right / width, updated.bottom / height))
            }
        },
    ) {
        drawCrop(crop.scaled(size.width, size.height))
    }
}

/** Draws the veil around [rect], the grid and outline inside it, and the corner handles. */
private fun DrawScope.drawCrop(rect: Rect) {
    val veil = Color.Black.copy(alpha = VEIL_ALPHA)
    drawRect(veil, Offset.Zero, Size(size.width, rect.top))
    drawRect(veil, Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
    drawRect(veil, Offset(0f, rect.top), Size(rect.left, rect.height))
    drawRect(veil, Offset(rect.right, rect.top), Size(size.width - rect.right, rect.height))

    val grid = Color.White.copy(alpha = GRID_ALPHA)
    for (i in 1 until GRID_DIVISIONS) {
        val x = rect.left + rect.width * i / GRID_DIVISIONS
        val y = rect.top + rect.height * i / GRID_DIVISIONS
        drawLine(grid, Offset(x, rect.top), Offset(x, rect.bottom), GRID_LINE_WIDTH.toPx())
        drawLine(grid, Offset(rect.left, y), Offset(rect.right, y), GRID_LINE_WIDTH.toPx())
    }
    drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(OUTLINE_WIDTH.toPx()))

    val length = CORNER_LENGTH.toPx()
    val width = CORNER_WIDTH.toPx()
    fun corner(x: Float, y: Float, towardsX: Float, towardsY: Float) {
        drawLine(Color.White, Offset(x, y), Offset(x + towardsX * length, y), width)
        drawLine(Color.White, Offset(x, y), Offset(x, y + towardsY * length), width)
    }
    corner(rect.left, rect.top, 1f, 1f)
    corner(rect.right, rect.top, -1f, 1f)
    corner(rect.left, rect.bottom, 1f, -1f)
    corner(rect.right, rect.bottom, -1f, -1f)
}

/** This normalized rect scaled to [width] × [height] pixels. */
private fun Rect.scaled(width: Float, height: Float) = Rect(left * width, top * height, right * width, bottom * height)

/**
 * What a drag starting at [position] grabs: a corner first, then an edge (only in a free crop, [edgesAllowed]),
 * then the whole crop if it starts inside; null if it starts elsewhere.
 */
private fun pickHandle(rect: Rect, position: Offset, touchRadius: Float, edgesAllowed: Boolean): Handle? {
    val nearLeft = abs(position.x - rect.left) < touchRadius
    val nearRight = abs(position.x - rect.right) < touchRadius
    val nearTop = abs(position.y - rect.top) < touchRadius
    val nearBottom = abs(position.y - rect.bottom) < touchRadius
    val withinX = position.x > rect.left - touchRadius && position.x < rect.right + touchRadius
    val withinY = position.y > rect.top - touchRadius && position.y < rect.bottom + touchRadius
    return when {
        nearLeft && nearTop -> Handle.TopLeft
        nearRight && nearTop -> Handle.TopRight
        nearLeft && nearBottom -> Handle.BottomLeft
        nearRight && nearBottom -> Handle.BottomRight
        edgesAllowed && nearLeft && withinY -> Handle.Left
        edgesAllowed && nearRight && withinY -> Handle.Right
        edgesAllowed && nearTop && withinX -> Handle.Top
        edgesAllowed && nearBottom && withinX -> Handle.Bottom
        rect.contains(position) -> Handle.Move
        else -> null
    }
}

/**
 * [rect] after dragging [handle] by [drag], kept inside the [width] × [height] content and at least [minSize].
 * With a fixed [ratio], the opposite corner stays in place and the height follows the width.
 */
private fun dragRect(rect: Rect, handle: Handle, drag: Offset, width: Float, height: Float, minSize: Float, ratio: Float?): Rect {
    if (handle == Handle.Move) {
        return rect.translate(drag.x.coerceIn(-rect.left, width - rect.right), drag.y.coerceIn(-rect.top, height - rect.bottom))
    }
    if (ratio == null) {
        var (left, top, right, bottom) = listOf(rect.left, rect.top, rect.right, rect.bottom)
        if (handle in setOf(Handle.TopLeft, Handle.BottomLeft, Handle.Left)) left = (left + drag.x).coerceIn(0f, right - minSize)
        if (handle in setOf(Handle.TopRight, Handle.BottomRight, Handle.Right)) right = (right + drag.x).coerceIn(left + minSize, width)
        if (handle in setOf(Handle.TopLeft, Handle.TopRight, Handle.Top)) top = (top + drag.y).coerceIn(0f, bottom - minSize)
        if (handle in setOf(Handle.BottomLeft, Handle.BottomRight, Handle.Bottom)) bottom = (bottom + drag.y).coerceIn(top + minSize, height)
        return Rect(left, top, right, bottom)
    }
    val growsLeft = handle == Handle.TopLeft || handle == Handle.BottomLeft
    val growsUp = handle == Handle.TopLeft || handle == Handle.TopRight
    val anchorX = if (growsLeft) rect.right else rect.left
    val anchorY = if (growsUp) rect.bottom else rect.top
    val dx = if (growsLeft) -drag.x else drag.x
    val dy = if (growsUp) -drag.y else drag.y
    val maxWidth = if (growsLeft) anchorX else width - anchorX
    val maxHeight = if (growsUp) anchorY else height - anchorY
    val newWidth = (if (abs(dx) >= abs(dy * ratio)) rect.width + dx else rect.width + dy * ratio)
        .coerceIn(minSize, minOf(maxWidth, maxHeight * ratio))
    val newHeight = newWidth / ratio
    val left = if (growsLeft) anchorX - newWidth else anchorX
    val top = if (growsUp) anchorY - newHeight else anchorY
    return Rect(left, top, left + newWidth, top + newHeight)
}
