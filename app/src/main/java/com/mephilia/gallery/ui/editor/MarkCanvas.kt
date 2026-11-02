package com.mephilia.gallery.ui.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

/** Thinnest stroke drawn on screen, in pixels, so very small strokes stay visible. */
private const val MIN_STROKE_PX = 1f

/**
 * The cropped photo with its adjustments and marks (drawings and hidden areas).
 *
 * When [drawing] is set, a finger draws a new mark with its settings (pen color or mosaic, width); [onMark]
 * receives it once the finger lifts. Points are stored relative to the image (0 to 1), so marks don't depend on
 * the preview's size.
 *
 * @param pixelated the mosaic version of [image], which hidden areas reveal; until it's ready they aren't drawn.
 */
@Composable
internal fun MarkCanvas(
    image: Bitmap,
    pixelated: Bitmap?,
    edits: PhotoEdits,
    drawing: Mark?,
    onMark: (Mark) -> Unit,
) {
    val imageBitmap = remember(image) { image.asImageBitmap() }
    val mosaicBrush = remember(pixelated) { pixelated?.let { ShaderBrush(ImageShader(it.asImageBitmap())) } }
    val current = remember { mutableStateListOf<Offset>() }
    val colorFilter = if (edits.adjust.isIdentity) null else ColorFilter.colorMatrix(edits.adjust.composeMatrix())
    val drawInput = if (drawing == null) Modifier else Modifier.pointerInput(drawing) {
        awaitEachGesture {
            val down = awaitFirstDown()
            current.clear()
            current += Offset(down.position.x / size.width, down.position.y / size.height)
            down.consume()
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                current += Offset(
                    (change.position.x / size.width).coerceIn(0f, 1f),
                    (change.position.y / size.height).coerceIn(0f, 1f),
                )
            }
            if (current.isNotEmpty()) onMark(drawing.copy(points = current.toList()))
            current.clear()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(imageBitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, colorFilter = colorFilter)
        Canvas(Modifier.fillMaxSize().then(drawInput)) {
            edits.marks.forEach { drawMark(it, image, mosaicBrush) }
            inProgress(drawing, current)?.let { drawMark(it, image, mosaicBrush) }
        }
    }
}

/** The mark being drawn: [drawing]'s settings with the [points] so far, or null if nothing is being drawn. */
private fun inProgress(drawing: Mark?, points: SnapshotStateList<Offset>): Mark? =
    if (drawing != null && points.isNotEmpty()) drawing.copy(points = points.toList()) else null

/**
 * Draws [mark] over the preview: a colored stroke, or for a hidden area, the [mosaicBrush] along the stroke.
 * The mosaic shader is sized to [image], so the stroke is drawn in image coordinates for the shader to line up
 * with the photo.
 */
private fun DrawScope.drawMark(mark: Mark, image: Bitmap, mosaicBrush: ShaderBrush?) {
    val path = strokePath(mark.points, size.width, size.height).asComposePath()
    val width = (mark.width * size.width).coerceAtLeast(MIN_STROKE_PX)
    if (!mark.mosaic) {
        drawPath(path, Color(mark.color), style = roundStroke(width))
        return
    }
    val brush = mosaicBrush ?: return
    val scaleX = size.width / image.width
    val scaleY = size.height / image.height
    val imagePath = android.graphics.Path(path.asAndroidPath()).apply {
        transform(Matrix().apply { setScale(1 / scaleX, 1 / scaleY) })
    }.asComposePath()
    drawContext.canvas.save()
    drawContext.canvas.scale(scaleX, scaleY)
    drawPath(imagePath, brush, style = roundStroke(width / scaleX))
    drawContext.canvas.restore()
}

/** A stroke of [width] with round ends and corners, like a felt pen. */
private fun roundStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
