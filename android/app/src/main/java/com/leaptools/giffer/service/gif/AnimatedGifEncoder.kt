package com.leaptools.giffer.service.gif

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/*
 * Animated GIF89a writer.
 *
 * Adapted from Kevin Weiner's public-domain AnimatedGifEncoder (FM Software), which itself
 * builds on the ACME GifEncoder and Jef Poskanzer's work. Kotlin port for Giffer, operating
 * on Android Bitmaps. Each frame is quantized to its own 256-colour palette (per-frame
 * palette) for better colour fidelity.
 */
class AnimatedGifEncoder {

    private var width = 0
    private var height = 0
    private var started = false
    private lateinit var out: OutputStream

    private var delayMs = 0          // frame delay, milliseconds
    private var repeat = 0           // 0 = loop forever, -1 = no repeat
    private var sample = 10          // NeuQuant sampling factor (1 = best/slow, 10 = default)

    private var firstFrame = true
    private var sizeSet = false

    private lateinit var pixels: ByteArray   // BGR byte array of current frame
    private lateinit var indexedPixels: ByteArray
    private lateinit var colorTab: ByteArray // RGB palette (768 bytes)
    private var colorDepth = 0
    private val usedEntry = BooleanArray(256)
    private val palSize = 7          // color table size (bits-1)

    fun setRepeat(iter: Int) {
        if (iter >= 0) repeat = iter
    }

    /** Frame delay in milliseconds. */
    fun setDelay(ms: Int) {
        delayMs = ms
    }

    /** Quality: NeuQuant sample factor, >= 1. Higher = faster, lower quality. */
    fun setQuality(quality: Int) {
        sample = if (quality < 1) 1 else quality
    }

    fun setSize(w: Int, h: Int) {
        width = w
        height = h
        if (width < 1) width = 320
        if (height < 1) height = 240
        sizeSet = true
    }

    fun start(os: OutputStream): Boolean {
        out = os
        started = true
        firstFrame = true
        return try {
            writeString("GIF89a")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun addFrame(bitmap: Bitmap): Boolean {
        if (!started) return false
        return try {
            if (!sizeSet) setSize(bitmap.width, bitmap.height)
            getImagePixels(bitmap)
            analyzePixels()
            if (firstFrame) {
                writeLSD()
                writePalette()
                if (repeat >= 0) writeNetscapeExt()
            }
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) writePalette()
            writePixels()
            firstFrame = false
            true
        } catch (e: Exception) {
            false
        }
    }

    fun finish(): Boolean {
        if (!started) return false
        return try {
            out.write(0x3b) // GIF trailer
            out.flush()
            true
        } catch (e: Exception) {
            false
        } finally {
            started = false
        }
    }

    private fun getImagePixels(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val count = w * h
        val argb = IntArray(count)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        pixels = ByteArray(count * 3)
        var bi = 0
        for (i in 0 until count) {
            val c = argb[i]
            // store as BGR for NeuQuant
            pixels[bi++] = (c and 0xff).toByte()         // blue
            pixels[bi++] = ((c shr 8) and 0xff).toByte() // green
            pixels[bi++] = ((c shr 16) and 0xff).toByte()// red
        }
    }

    private fun analyzePixels() {
        val len = pixels.size
        val nPix = len / 3
        indexedPixels = ByteArray(nPix)
        val nq = NeuQuant(pixels, len, sample)
        val bgrMap = nq.process() // BGR triples
        // Convert palette to RGB order for the GIF colour table.
        colorTab = ByteArray(bgrMap.size)
        var i = 0
        while (i < bgrMap.size) {
            colorTab[i] = bgrMap[i + 2] // r
            colorTab[i + 1] = bgrMap[i + 1] // g
            colorTab[i + 2] = bgrMap[i] // b
            i += 3
        }
        usedEntry.fill(false)
        // Map image pixels to palette indices.
        var k = 0
        for (j in 0 until nPix) {
            val b = pixels[k++].toInt() and 0xff
            val g = pixels[k++].toInt() and 0xff
            val r = pixels[k++].toInt() and 0xff
            val index = nq.map(b, g, r)
            usedEntry[index] = true
            indexedPixels[j] = index.toByte()
        }
        colorDepth = 8
    }

    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        // global colour table flag + colour resolution + sort + size
        out.write(0x80 or 0x70 or palSize)
        out.write(0) // background colour index
        out.write(0) // pixel aspect ratio
    }

    private fun writePalette() {
        out.write(colorTab, 0, colorTab.size)
        val n = 3 * 256 - colorTab.size
        for (i in 0 until n) out.write(0)
    }

    private fun writeNetscapeExt() {
        out.write(0x21) // extension introducer
        out.write(0xff) // app extension label
        out.write(11)   // block size
        writeString("NETSCAPE2.0")
        out.write(3)
        out.write(1)
        writeShort(repeat)
        out.write(0)
    }

    private fun writeGraphicCtrlExt() {
        out.write(0x21) // extension introducer
        out.write(0xf9) // GCE label
        out.write(4)    // block size
        // no transparency, disposal = leave in place (0)
        out.write(0)
        val delayCs = Math.round(delayMs / 10.0).toInt() // delay in 1/100 sec
        writeShort(delayCs)
        out.write(0) // transparent colour index
        out.write(0) // block terminator
    }

    private fun writeImageDesc() {
        out.write(0x2c) // image separator
        writeShort(0)   // left
        writeShort(0)   // top
        writeShort(width)
        writeShort(height)
        if (firstFrame) {
            out.write(0) // no local colour table (uses global)
        } else {
            out.write(0x80 or palSize) // local colour table
        }
    }

    private fun writePixels() {
        val encoder = LzwEncoder(width, height, indexedPixels, colorDepth)
        encoder.encode(out)
    }

    private fun writeShort(value: Int) {
        out.write(value and 0xff)
        out.write((value shr 8) and 0xff)
    }

    private fun writeString(s: String) {
        for (c in s) out.write(c.code)
    }

    companion object {
        /** Convenience: encode a list of frames with per-frame delays (ms) to GIF bytes. */
        fun encodeToBytes(
            frames: List<Bitmap>,
            delayMs: Int,
            quality: Int = 10,
            onProgress: (Float) -> Unit = {},
        ): ByteArray {
            val bos = ByteArrayOutputStream()
            val enc = AnimatedGifEncoder()
            enc.setQuality(quality)
            enc.setRepeat(0)
            enc.setDelay(delayMs)
            enc.start(bos)
            frames.forEachIndexed { i, frame ->
                enc.addFrame(frame)
                onProgress((i + 1).toFloat() / frames.size)
            }
            enc.finish()
            return bos.toByteArray()
        }
    }
}
