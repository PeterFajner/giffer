package com.leaptools.giffer.model

import androidx.compose.ui.geometry.Rect

/**
 * All values are normalized (0..1) where they describe a fraction of the source, mirroring
 * the iOS GIFConfiguration. [cropRect] is in normalized image coordinates with the origin at
 * the top-left; null means no crop.
 */
data class GifConfiguration(
    val resolutionScale: Float = 1.0f,
    val fps: Int = 12,
    val trimStart: Double = 0.0,
    val trimEnd: Double = 1.0,
    val cropRect: Rect? = null,
    val playbackMode: PlaybackMode = PlaybackMode.FORWARD,
)
