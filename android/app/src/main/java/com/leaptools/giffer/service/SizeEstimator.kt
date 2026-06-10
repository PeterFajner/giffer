package com.leaptools.giffer.service

import android.graphics.Bitmap
import com.leaptools.giffer.model.GifConfiguration
import com.leaptools.giffer.model.PlaybackMode
import com.leaptools.giffer.service.gif.AnimatedGifEncoder

/**
 * Estimates final GIF size by encoding a few sample frames and extrapolating, mirroring the
 * iOS SizeEstimator.
 */
object SizeEstimator {

    fun estimatedSize(
        sampleFrames: List<Bitmap>,
        totalFrameCount: Int,
        config: GifConfiguration,
    ): Long {
        if (sampleFrames.isEmpty() || totalFrameCount <= 0) return 0

        val samples = sampleFrames.take(3)
        val data = AnimatedGifEncoder.encodeToBytes(samples, delayMs = (1000.0 / config.fps).toInt())
        if (data.isEmpty()) return 0

        val bytesPerFrame = data.size.toLong() / samples.size
        val effectiveFrameCount = when (config.playbackMode) {
            PlaybackMode.FORWARD, PlaybackMode.REVERSE -> totalFrameCount
            PlaybackMode.BOUNCE -> maxOf(1, totalFrameCount * 2 - 2)
        }
        return bytesPerFrame * effectiveFrameCount
    }
}
