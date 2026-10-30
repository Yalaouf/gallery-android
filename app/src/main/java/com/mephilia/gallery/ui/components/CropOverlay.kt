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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Normalized (0..1) crop covering the whole content. */
val FullCrop = Rect(0f, 0f, 1f, 1f)

data class AspectPreset(val label: String, val ratio: Float?)

fun aspectPresets(originalRatio: Float, freeLabel: String, originalLabel: String) = listOf(
    AspectPreset(freeLabel, null),
    AspectPreset(originalLabel, originalRatio),
    AspectPreset("1:1", 1f),
    AspectPreset("4:5", 4f / 5f),
    AspectPreset("3:4", 3f / 4f),
    AspectPreset("4:3", 4f / 3f),
    AspectPreset("9:16", 9f / 16f),
    AspectPreset("16:9", 16f / 9f),
)

/** Rotates a normalized crop along with the image by [quarterTurns] (positive = clockwise). */
fun Rect.rotatedBy(quarterTurns: Int): Rect {
    var r = this
    repeat(quarterTurns.mod(4)) {
        // Clockwise: point (x, y) becomes (1 - y, x)
        r = Rect(1f - r.bottom, r.left, 1f - r.top, r.right)
    }
    return r
}

/** Largest centered crop with pixel aspect [ratio] inside content of aspect [contentRatio]. */
fun centeredCrop(ratio: Float?, contentRatio: Float): Rect {
    if (ratio == null) return FullCrop
    val k = ratio / contentRatio
    val (w, h) = if (k >= 1f) 1f to 1f / k else k to 1f
    return Rect((1f - w) / 2f, (1f - h) / 2f, (1f + w) / 2f, (1f + h) / 2f)
}

private enum class Handle { TL, TR, BL, BR, L, T, R, B, Move }

/**
 * Crop overlay, laid out exactly over the content (same size).
 * [ratio] is the enforced width/height ratio, or null for a free crop.
 */
@Composable
fun CropOverlay(
    crop: Rect,
    onCropChange: (Rect) -> Unit,
    ratio: Float?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val touch = with(density) { 28.dp.toPx() }
    val minSize = with(density) { 48.dp.toPx() }
    val currentCrop by rememberUpdatedState(crop)
    val currentRatio by rememberUpdatedState(ratio)
    val onChange by rememberUpdatedState(onCropChange)
    val handle = remember { arrayOf<Handle?>(null) }

    Canvas(
        modifier.systemGestureExclusion().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { p ->
                    val r = currentCrop.toPx(size.width.toFloat(), size.height.toFloat())
                    handle[0] = pickHandle(r, p, touch, edgesAllowed = currentRatio == null)
                },
                onDragEnd = { handle[0] = null },
                onDragCancel = { handle[0] = null },
            ) { change, drag ->
                val h = handle[0] ?: return@detectDragGestures
                change.consume()
                val w = size.width.toFloat()
                val hh = size.height.toFloat()
                val r = currentCrop.toPx(w, hh)
                val updated = dragRect(r, h, drag, w, hh, minSize, currentRatio)
                onChange(Rect(updated.left / w, updated.top / hh, updated.right / w, updated.bottom / hh))
            }
        },
    ) {
        val r = crop.toPx(size.width, size.height)
        val dim = Color.Black.copy(alpha = 0.55f)
        drawRect(dim, Offset.Zero, Size(size.width, r.top))
        drawRect(dim, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
        drawRect(dim, Offset(0f, r.top), Size(r.left, r.height))
        drawRect(dim, Offset(r.right, r.top), Size(size.width - r.right, r.height))
        val grid = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            val x = r.left + r.width * i / 3f
            val y = r.top + r.height * i / 3f
            drawLine(grid, Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
            drawLine(grid, Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
        }
        drawRect(Color.White, r.topLeft, r.size, style = Stroke(1.5.dp.toPx()))
        val len = 20.dp.toPx()
        val sw = 4.dp.toPx()
        val c = Color.White
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y), Offset(x + dx * len, y), sw)
            drawLine(c, Offset(x, y), Offset(x, y + dy * len), sw)
        }
        corner(r.left, r.top, 1f, 1f)
        corner(r.right, r.top, -1f, 1f)
        corner(r.left, r.bottom, 1f, -1f)
        corner(r.right, r.bottom, -1f, -1f)
    }
}

private fun Rect.toPx(w: Float, h: Float) = Rect(left * w, top * h, right * w, bottom * h)

private fun pickHandle(r: Rect, p: Offset, touch: Float, edgesAllowed: Boolean): Handle? {
    val nearL = abs(p.x - r.left) < touch
    val nearR = abs(p.x - r.right) < touch
    val nearT = abs(p.y - r.top) < touch
    val nearB = abs(p.y - r.bottom) < touch
    val inX = p.x > r.left - touch && p.x < r.right + touch
    val inY = p.y > r.top - touch && p.y < r.bottom + touch
    return when {
        nearL && nearT -> Handle.TL
        nearR && nearT -> Handle.TR
        nearL && nearB -> Handle.BL
        nearR && nearB -> Handle.BR
        edgesAllowed && nearL && inY -> Handle.L
        edgesAllowed && nearR && inY -> Handle.R
        edgesAllowed && nearT && inX -> Handle.T
        edgesAllowed && nearB && inX -> Handle.B
        r.contains(p) -> Handle.Move
        else -> null
    }
}

private fun dragRect(r: Rect, h: Handle, d: Offset, w: Float, hh: Float, min: Float, ratio: Float?): Rect {
    if (h == Handle.Move) {
        val dx = d.x.coerceIn(-r.left, w - r.right)
        val dy = d.y.coerceIn(-r.top, hh - r.bottom)
        return r.translate(dx, dy)
    }
    if (ratio == null) {
        var (l, t, rr, b) = listOf(r.left, r.top, r.right, r.bottom)
        if (h == Handle.TL || h == Handle.BL || h == Handle.L) l = (l + d.x).coerceIn(0f, rr - min)
        if (h == Handle.TR || h == Handle.BR || h == Handle.R) rr = (rr + d.x).coerceIn(l + min, w)
        if (h == Handle.TL || h == Handle.TR || h == Handle.T) t = (t + d.y).coerceIn(0f, b - min)
        if (h == Handle.BL || h == Handle.BR || h == Handle.B) b = (b + d.y).coerceIn(t + min, hh)
        return Rect(l, t, rr, b)
    }
    // Fixed ratio: anchor the opposite corner and derive the height from the width
    val anchorX = if (h == Handle.TL || h == Handle.BL) r.right else r.left
    val anchorY = if (h == Handle.TL || h == Handle.TR) r.bottom else r.top
    val growLeft = anchorX == r.right
    val growUp = anchorY == r.bottom
    val dxSigned = if (growLeft) -d.x else d.x
    val dySigned = if (growUp) -d.y else d.y
    var newW = if (abs(dxSigned) >= abs(dySigned * ratio)) r.width + dxSigned else r.width + dySigned * ratio
    val maxW = if (growLeft) anchorX else w - anchorX
    val maxH = if (growUp) anchorY else hh - anchorY
    newW = newW.coerceIn(min, minOf(maxW, maxH * ratio))
    val newH = newW / ratio
    val l = if (growLeft) anchorX - newW else anchorX
    val t = if (growUp) anchorY - newH else anchorY
    return Rect(l, t, l + newW, t + newH)
}
