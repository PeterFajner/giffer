package com.leaptools.giffer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val MIN_SIZE = 0.05f

private enum class Handle { TL, TR, BL, BR, T, B, L, R, MOVE }

/**
 * Interactive crop rectangle over the preview. [cropRect] is normalized (0..1). Draws the
 * rule-of-thirds grid, dimmed exterior, and L-shaped corner / edge handles. Mirrors the iOS
 * CropOverlayView.
 */
@Composable
fun CropOverlay(
    cropRect: Rect,
    imageAspectRatio: Float,
    onCropChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeHandle by remember { mutableStateOf<Handle?>(null) }
    var initialRect by remember { mutableStateOf(cropRect) }
    var startPoint by remember { mutableStateOf(Offset.Zero) }

    // Always hit-test against the latest crop rect; capturing the param directly would freeze
    // it at the rect from first composition, so a second drag would jump back to it.
    val currentCrop by rememberUpdatedState(cropRect)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Crop handles can reach the screen edges; opt out of the system back-swipe there.
            .systemGestureExclusion()
            .pointerInput(imageAspectRatio) {
                val hitPx = 28.dp.toPx() // generous touch radius around each handle
                detectDragGestures(
                    onDragStart = { pos ->
                        val img = aspectFitRect(size.width.toFloat(), size.height.toFloat(), imageAspectRatio)
                        val cr = cropToPixels(currentCrop, img)
                        activeHandle = hitTest(pos, cr, hitPx)
                        initialRect = currentCrop
                        startPoint = pos
                    },
                    onDragEnd = { activeHandle = null },
                    onDragCancel = { activeHandle = null },
                ) { change, _ ->
                    change.consume()
                    val handle = activeHandle ?: return@detectDragGestures
                    val img = aspectFitRect(size.width.toFloat(), size.height.toFloat(), imageAspectRatio)
                    val norm = pixelToNormalized(change.position, img)
                    val clamped = Offset(norm.x.coerceIn(0f, 1f), norm.y.coerceIn(0f, 1f))

                    if (handle == Handle.MOVE) {
                        val startNorm = pixelToNormalized(startPoint, img)
                        val dx = clamped.x - startNorm.x.coerceIn(0f, 1f)
                        val dy = clamped.y - startNorm.y.coerceIn(0f, 1f)
                        val nx = (initialRect.left + dx).coerceIn(0f, 1f - initialRect.width)
                        val ny = (initialRect.top + dy).coerceIn(0f, 1f - initialRect.height)
                        onCropChange(Rect(nx, ny, nx + initialRect.width, ny + initialRect.height))
                    } else {
                        onCropChange(resize(handle, initialRect, clamped))
                    }
                }
            }
    ) {
        val img = aspectFitRect(size.width, size.height, imageAspectRatio)
        val cr = cropToPixels(cropRect, img)
        drawDimmed(cr)
        drawGridAndHandles(cr)
    }
}

private fun DrawScope.drawDimmed(cr: Rect) {
    val full = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
    val hole = Path().apply { addRect(cr) }
    val outside = Path().apply { op(full, hole, PathOperation.Difference) }
    drawPath(outside, Color.Black.copy(alpha = 0.5f))
}

private fun DrawScope.drawGridAndHandles(cr: Rect) {
    // Sizes are in dp so handles stay a usable size on high-density screens.
    val borderW = 1.dp.toPx()
    val gridW = 1.dp.toPx()

    // Border
    drawRect(Color.White, topLeft = cr.topLeft, size = Size(cr.width, cr.height), style = Stroke(borderW))

    // Rule of thirds
    val line = Color.White.copy(alpha = 0.25f)
    for (i in 1..2) {
        val f = i / 3f
        drawLine(line, Offset(cr.left + f * cr.width, cr.top), Offset(cr.left + f * cr.width, cr.bottom), gridW)
        drawLine(line, Offset(cr.left, cr.top + f * cr.height), Offset(cr.right, cr.top + f * cr.height), gridW)
    }

    // L-shaped corner handles
    val len = 22.dp.toPx()
    val thick = 4.dp.toPx()
    data class Corner(val p: Offset, val hd: Float, val vd: Float)
    val corners = listOf(
        Corner(cr.topLeft, 1f, 1f),
        Corner(Offset(cr.right, cr.top), -1f, 1f),
        Corner(Offset(cr.left, cr.bottom), 1f, -1f),
        Corner(Offset(cr.right, cr.bottom), -1f, -1f),
    )
    for (c in corners) {
        val hx = if (c.hd < 0) c.p.x - len else c.p.x
        drawRect(Color.White, topLeft = Offset(hx, c.p.y - thick / 2), size = Size(len, thick))
        val vy = if (c.vd < 0) c.p.y - len else c.p.y
        drawRect(Color.White, topLeft = Offset(c.p.x - thick / 2, vy), size = Size(thick, len))
    }

    // Edge midpoint handles
    val edgeLen = 40.dp.toPx()
    drawRect(Color.White, topLeft = Offset(cr.center.x - edgeLen / 2, cr.top - thick / 2), size = Size(edgeLen, thick))
    drawRect(Color.White, topLeft = Offset(cr.center.x - edgeLen / 2, cr.bottom - thick / 2), size = Size(edgeLen, thick))
    drawRect(Color.White, topLeft = Offset(cr.left - thick / 2, cr.center.y - edgeLen / 2), size = Size(thick, edgeLen))
    drawRect(Color.White, topLeft = Offset(cr.right - thick / 2, cr.center.y - edgeLen / 2), size = Size(thick, edgeLen))
}

private fun aspectFitRect(w: Float, h: Float, aspect: Float): Rect {
    if (w <= 0 || h <= 0 || aspect <= 0) return Rect(0f, 0f, w, h)
    val viewAspect = w / h
    val fitW: Float
    val fitH: Float
    if (aspect > viewAspect) {
        fitW = w; fitH = w / aspect
    } else {
        fitH = h; fitW = h * aspect
    }
    val x = (w - fitW) / 2
    val y = (h - fitH) / 2
    return Rect(x, y, x + fitW, y + fitH)
}

private fun cropToPixels(crop: Rect, img: Rect): Rect = Rect(
    img.left + crop.left * img.width,
    img.top + crop.top * img.height,
    img.left + crop.right * img.width,
    img.top + crop.bottom * img.height,
)

private fun pixelToNormalized(p: Offset, img: Rect): Offset = Offset(
    (p.x - img.left) / img.width.coerceAtLeast(1f),
    (p.y - img.top) / img.height.coerceAtLeast(1f),
)

private fun hitTest(p: Offset, cr: Rect, thresh: Float): Handle {
    val corners = listOf(
        Handle.TL to cr.topLeft,
        Handle.TR to Offset(cr.right, cr.top),
        Handle.BL to Offset(cr.left, cr.bottom),
        Handle.BR to Offset(cr.right, cr.bottom),
    )
    for ((h, pos) in corners) {
        if (abs(p.x - pos.x) < thresh && abs(p.y - pos.y) < thresh) return h
    }
    if (abs(p.y - cr.top) < thresh && p.x > cr.left && p.x < cr.right) return Handle.T
    if (abs(p.y - cr.bottom) < thresh && p.x > cr.left && p.x < cr.right) return Handle.B
    if (abs(p.x - cr.left) < thresh && p.y > cr.top && p.y < cr.bottom) return Handle.L
    if (abs(p.x - cr.right) < thresh && p.y > cr.top && p.y < cr.bottom) return Handle.R
    return Handle.MOVE
}

private fun resize(handle: Handle, initial: Rect, p: Offset): Rect {
    val ms = MIN_SIZE
    return when (handle) {
        Handle.TL -> {
            val w = maxOf(ms, initial.right - p.x)
            val h = maxOf(ms, initial.bottom - p.y)
            Rect(initial.right - w, initial.bottom - h, initial.right, initial.bottom)
        }
        Handle.TR -> {
            val w = maxOf(ms, p.x - initial.left)
            val h = maxOf(ms, initial.bottom - p.y)
            Rect(initial.left, initial.bottom - h, initial.left + w, initial.bottom)
        }
        Handle.BL -> {
            val w = maxOf(ms, initial.right - p.x)
            val h = maxOf(ms, p.y - initial.top)
            Rect(initial.right - w, initial.top, initial.right, initial.top + h)
        }
        Handle.BR -> {
            val w = maxOf(ms, p.x - initial.left)
            val h = maxOf(ms, p.y - initial.top)
            Rect(initial.left, initial.top, initial.left + w, initial.top + h)
        }
        Handle.T -> {
            val h = maxOf(ms, initial.bottom - p.y)
            Rect(initial.left, initial.bottom - h, initial.right, initial.bottom)
        }
        Handle.B -> {
            val h = maxOf(ms, p.y - initial.top)
            Rect(initial.left, initial.top, initial.right, initial.top + h)
        }
        Handle.L -> {
            val w = maxOf(ms, initial.right - p.x)
            Rect(initial.right - w, initial.top, initial.right, initial.bottom)
        }
        Handle.R -> {
            val w = maxOf(ms, p.x - initial.left)
            Rect(initial.left, initial.top, initial.left + w, initial.bottom)
        }
        Handle.MOVE -> initial
    }
}
