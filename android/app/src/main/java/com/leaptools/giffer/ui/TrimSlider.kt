package com.leaptools.giffer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.leaptools.giffer.ui.theme.GifferYellow
import kotlin.math.abs
import kotlin.math.roundToInt

private val handleWidth = 16.dp
private val barHeight = 58.dp

/**
 * Two-handle trim slider over a filmstrip of [frames]. [trimStart]/[trimEnd] are 0..1.
 * Mirrors iOS TrimSliderView.
 */
@Composable
fun TrimSlider(
    trimStart: Double,
    trimEnd: Double,
    frames: List<Bitmap>,
    onTrimChange: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val handlePx = with(density) { handleWidth.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val usable = (widthPx - handlePx * 2).coerceAtLeast(1f)
        val leftX = handlePx + (trimStart * usable).toFloat()
        val rightX = handlePx + (trimEnd * usable).toFloat()

        Filmstrip(
            frames,
            Modifier
                .fillMaxSize()
                .pointerInput(usable) {
                    if (usable <= 1f) return@pointerInput
                    detectDragGestures { change, _ ->
                        change.consume()
                        val x = change.position.x
                        val curLeft = handlePx + (trimStart * usable).toFloat()
                        val curRight = handlePx + (trimEnd * usable).toFloat()
                        if (abs(x - curLeft) <= abs(x - curRight)) {
                            val v = ((x - handlePx) / usable).toDouble().coerceIn(0.0, trimEnd - 0.02)
                            onTrimChange(v, trimEnd)
                        } else {
                            val v = ((x - handlePx) / usable).toDouble().coerceIn(trimStart + 0.02, 1.0)
                            onTrimChange(trimStart, v)
                        }
                    }
                },
        )

        // Dimmed regions outside the selection
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { (leftX - handlePx).coerceAtLeast(0f).toDp() })
                .background(Color.Black.copy(alpha = 0.6f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(with(density) { (widthPx - rightX - handlePx).coerceAtLeast(0f).toDp() })
                .background(Color.Black.copy(alpha = 0.6f))
        )

        // Left handle
        TrimHandle(
            leading = true,
            modifier = Modifier.offset { IntOffset((leftX - handlePx).roundToInt(), 0) },
        )
        // Right handle
        TrimHandle(
            leading = false,
            modifier = Modifier.offset { IntOffset(rightX.roundToInt(), 0) },
        )
    }
}

@Composable
private fun TrimHandle(leading: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier
            .width(handleWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(GifferYellow),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (leading) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun Filmstrip(frames: List<Bitmap>, modifier: Modifier) {
    Box(modifier = modifier.background(Color(0xFF333333))) {
        if (frames.size >= 2) {
            Row(modifier = Modifier.fillMaxSize()) {
                val thumbCount = 8
                for (i in 0 until thumbCount) {
                    val frameIndex =
                        (i * (frames.size - 1) / (thumbCount - 1)).coerceIn(0, frames.size - 1)
                    Image(
                        bitmap = frames[frameIndex].asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
