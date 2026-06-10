package com.leaptools.giffer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leaptools.giffer.model.GifConfiguration
import com.leaptools.giffer.service.GifEncoder
import com.leaptools.giffer.service.MotionPhotoExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device test of the full Android pipeline against real Motion Photos (a Pixel/Google
 * format file and a Samsung format file). Exercises the device-only code paths — Media3
 * MotionPhotoMetadata, the byte-scan fallback, and MediaMetadataRetriever frame decoding —
 * that the Robolectric unit tests can't cover. Designed to run on Firebase Test Lab.
 */
@RunWith(AndroidJUnit4::class)
class MotionPhotoExtractorInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Copies a bundled test asset to a cache file and returns a file:// Uri for it. */
    private fun assetUri(name: String): Uri {
        val out = File(context.cacheDir, name)
        context.assets.open("motionphoto/$name").use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(out)
    }

    private fun runPipeline(asset: String) = runBlocking {
        val uri = assetUri(asset)
        val config = GifConfiguration(fps = 12)

        val result = MotionPhotoExtractor.extractFrames(context, uri, config)

        assertTrue("[$asset] expected frames", result.frames.isNotEmpty())
        assertTrue("[$asset] width > 0", result.originalSize.width > 0)
        assertTrue("[$asset] height > 0", result.originalSize.height > 0)
        assertTrue("[$asset] duration > 0", result.durationSeconds > 0.0)

        val gif = GifEncoder.encode(result.frames, config)
        assertTrue("[$asset] non-empty GIF", gif.size > 6)
        assertArrayEquals(
            "[$asset] GIF89a header",
            "GIF89a".toByteArray(Charsets.US_ASCII),
            gif.copyOfRange(0, 6),
        )
        assertEquals("[$asset] GIF trailer", 0x3b.toByte(), gif.last())
    }

    @Test
    fun pixelMotionPhoto_extractsAndEncodes() {
        // Google Motion Photo (Pixel 8a) — exercises the Media3 MotionPhotoMetadata path.
        runPipeline("pixel-8a.jpg")
    }

    @Test
    fun samsungMotionPhoto_extractsAndEncodes() {
        // Samsung One UI 6 — has the MotionPhoto_Data trailer (byte-scan fallback path).
        runPipeline("samsung-one-ui-6.jpg")
    }
}
