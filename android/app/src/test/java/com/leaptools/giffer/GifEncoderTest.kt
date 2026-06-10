package com.leaptools.giffer

import android.graphics.Bitmap
import android.graphics.Color
import com.leaptools.giffer.model.GifConfiguration
import com.leaptools.giffer.model.PlaybackMode
import com.leaptools.giffer.service.GifEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GifEncoderTest {

    private fun solid(color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    private fun ids(frames: List<Bitmap>, source: List<Bitmap>): List<Int> =
        frames.map { source.indexOf(it) }

    @Test
    fun forward_preservesOrder() {
        val src = listOf(solid(Color.RED), solid(Color.GREEN), solid(Color.BLUE))
        val out = GifEncoder.applyPlaybackMode(src, PlaybackMode.FORWARD)
        assertEquals(listOf(0, 1, 2), ids(out, src))
    }

    @Test
    fun reverse_reversesOrder() {
        val src = listOf(solid(Color.RED), solid(Color.GREEN), solid(Color.BLUE))
        val out = GifEncoder.applyPlaybackMode(src, PlaybackMode.REVERSE)
        assertEquals(listOf(2, 1, 0), ids(out, src))
    }

    @Test
    fun bounce_appendsInteriorReversed() {
        val src = listOf(solid(Color.RED), solid(Color.GREEN), solid(Color.BLUE))
        // forward 0,1,2 then interior reversed (drop first & last) -> 1
        val out = GifEncoder.applyPlaybackMode(src, PlaybackMode.BOUNCE)
        assertEquals(listOf(0, 1, 2, 1), ids(out, src))
    }

    @Test
    fun bounce_twoFrames_isForwardPlusReversed() {
        val src = listOf(solid(Color.RED), solid(Color.GREEN))
        val out = GifEncoder.applyPlaybackMode(src, PlaybackMode.BOUNCE)
        assertEquals(listOf(0, 1, 1, 0), ids(out, src))
    }

    @Test
    fun encode_producesValidGif89aBytes() {
        val frames = listOf(solid(Color.RED), solid(Color.GREEN), solid(Color.BLUE))
        val data = GifEncoder.encode(frames, GifConfiguration(fps = 12))
        assertTrue("non-empty output", data.size > 6)
        // Header "GIF89a"
        assertArrayEquals(
            "GIF89a".toByteArray(Charsets.US_ASCII),
            data.copyOfRange(0, 6),
        )
        // Trailer 0x3b
        assertEquals(0x3b.toByte(), data.last())
    }
}
