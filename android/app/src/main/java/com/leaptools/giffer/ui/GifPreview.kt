package com.leaptools.giffer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay

/**
 * Plays the supplied frames as a looping animation at [fps]. If [cropRect] is non-null the
 * frames are shown cropped (normalized rect). Mirrors iOS GIFPreviewView.
 */
@Composable
fun GifPreview(
    frames: List<Bitmap>,
    fps: Int,
    modifier: Modifier = Modifier,
    cropRect: Rect? = null,
) {
    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(frames, fps) {
        if (frames.size <= 1) {
            index = 0
            return@LaunchedEffect
        }
        val frameMs = (1000L / fps.coerceAtLeast(1))
        while (true) {
            delay(frameMs)
            index = (index + 1) % frames.size
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (frames.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF262626)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val safe = index % frames.size
            val shown = cropRect?.let { cropBitmap(frames[safe], it) } ?: frames[safe]
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun cropBitmap(frame: Bitmap, rect: Rect): Bitmap {
    val w = frame.width
    val h = frame.height
    val x = (rect.left * w).toInt().coerceIn(0, w - 1)
    val y = (rect.top * h).toInt().coerceIn(0, h - 1)
    val cw = (rect.width * w).toInt().coerceIn(1, w - x)
    val ch = (rect.height * h).toInt().coerceIn(1, h - y)
    return Bitmap.createBitmap(frame, x, y, cw, ch)
}
