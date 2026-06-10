package com.leaptools.giffer

import android.graphics.Bitmap
import android.graphics.Color
import com.leaptools.giffer.model.GifConfiguration
import com.leaptools.giffer.model.PlaybackMode
import com.leaptools.giffer.service.SizeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SizeEstimatorTest {

    private fun sample(): List<Bitmap> = (0 until 3).map {
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    }

    @Test
    fun zeroFrames_returnsZero() {
        assertEquals(0L, SizeEstimator.estimatedSize(emptyList(), 10, GifConfiguration()))
    }

    @Test
    fun bounceCountsRoughlyDouble() {
        val frames = sample()
        val forward = SizeEstimator.estimatedSize(frames, 10, GifConfiguration(playbackMode = PlaybackMode.FORWARD))
        val bounce = SizeEstimator.estimatedSize(frames, 10, GifConfiguration(playbackMode = PlaybackMode.BOUNCE))
        // bounce effective frames = 2*10-2 = 18 vs forward 10
        assertTrue("bounce should estimate larger", bounce > forward)
    }
}
