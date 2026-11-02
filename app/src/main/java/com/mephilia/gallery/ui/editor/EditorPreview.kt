package com.mephilia.gallery.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** The largest box with the aspect [ratio] (width / height) that fits the available space, centered. */
@Composable
fun FitBox(ratio: Float, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val containerRatio = maxWidth / maxHeight
        val (width, height) = if (ratio > containerRatio) maxWidth to maxWidth / ratio else maxHeight * ratio to maxHeight
        Box(Modifier.size(width, height)) { content() }
    }
}

/**
 * Shows [content] rotated by [angle] degrees and zoomed in so the corners stay covered,
 * matching what [applyEdits] renders for a picture of aspect [ratio].
 */
@Composable
fun Straightened(ratio: Float, angle: Float, content: @Composable () -> Unit) {
    val zoom = 1f / straightenScale(ratio, 1f, angle)
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                rotationZ = angle
                scaleX = zoom
                scaleY = zoom
            },
        ) { content() }
    }
}

/**
 * Draws [content] turned by [quarterTurns] clockwise, then mirrored if [flipH].
 * The content is measured at its unturned size (width and height swapped when it's sideways).
 */
@Composable
internal fun Transformed(quarterTurns: Int, flipH: Boolean, content: @Composable () -> Unit) {
    val turns = quarterTurns.mod(QUARTER_TURNS_PER_TURN)
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = if (flipH) -1f else 1f }) {
        Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val childConstraints = if (isSideways(turns)) Constraints.fixed(height, width) else Constraints.fixed(width, height)
            val placeable = measurables.first().measure(childConstraints)
            layout(width, height) {
                placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
                    rotationZ = DEGREES_PER_QUARTER_TURN * turns
                }
            }
        }
    }
}

/** Shows only the [crop] area (normalized, 0 to 1) of [content], scaled up to fill the space. */
@Composable
internal fun CroppedTo(crop: Rect, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize().clipToBounds()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val fullWidth = (width / crop.width).roundToInt()
        val fullHeight = (height / crop.height).roundToInt()
        val placeable = measurables.first().measure(Constraints.fixed(fullWidth, fullHeight))
        layout(width, height) {
            placeable.place((-crop.left * fullWidth).roundToInt(), (-crop.top * fullHeight).roundToInt())
        }
    }
}
