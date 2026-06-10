package com.leaptools.giffer.service

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import com.leaptools.giffer.model.GifConfiguration
import com.leaptools.giffer.model.PlaybackMode
import com.leaptools.giffer.service.gif.AnimatedGifEncoder

/**
 * Applies crop + playback ordering to a set of frames and encodes them to an animated GIF.
 * Mirrors the iOS GIFEncoder.
 */
object GifEncoder {

    class EncodingException(message: String) : Exception(message)

    fun encode(
        frames: List<Bitmap>,
        config: GifConfiguration,
        onProgress: (Float) -> Unit = {},
        checkActive: () -> Unit = {},
    ): ByteArray {
        val cropped = config.cropRect?.let { rect ->
            frames.mapNotNull { checkActive(); crop(it, rect) }
        } ?: frames

        val ordered = applyPlaybackMode(cropped, config.playbackMode)
        if (ordered.isEmpty()) throw EncodingException("No frames to encode")

        val delayMs = (1000.0 / config.fps).toInt()
        return AnimatedGifEncoder.encodeToBytes(
            frames = ordered,
            delayMs = delayMs,
            quality = 10,
            onProgress = onProgress,
            checkActive = checkActive,
        )
    }

    fun applyPlaybackMode(frames: List<Bitmap>, mode: PlaybackMode): List<Bitmap> {
        return when (mode) {
            PlaybackMode.FORWARD -> frames
            PlaybackMode.REVERSE -> frames.reversed()
            PlaybackMode.BOUNCE -> {
                if (frames.size <= 2) frames + frames.reversed()
                else frames + frames.subList(1, frames.size - 1).reversed()
            }
        }
    }

    /** Crops a bitmap to a normalized rect (0..1, top-left origin). Returns null if degenerate. */
    fun crop(frame: Bitmap, rect: Rect): Bitmap? {
        val w = frame.width
        val h = frame.height
        val x = (rect.left * w).toInt().coerceIn(0, w - 1)
        val y = (rect.top * h).toInt().coerceIn(0, h - 1)
        val cw = (rect.width * w).toInt().coerceIn(1, w - x)
        val ch = (rect.height * h).toInt().coerceIn(1, h - y)
        if (cw < 1 || ch < 1) return null
        return Bitmap.createBitmap(frame, x, y, cw, ch)
    }
}
