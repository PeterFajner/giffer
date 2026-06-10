package com.leaptools.giffer.service.gif

import java.io.OutputStream

/*
 * GIF LZW image-data encoder.
 *
 * Adapted from the public-domain GIFCOMPR.C (David Rowley) / Kevin Weiner's Java port, which
 * is itself based on "compress" by Spencer W. Thomas et al. Kotlin port for Giffer.
 */
class LzwEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixels: ByteArray,
    colorDepth: Int,
) {
    companion object {
        private const val EOF = -1
        private const val BITS = 12
        private const val HSIZE = 5003 // 80% occupancy
    }

    private val initCodeSize: Int = maxOf(2, colorDepth)
    private var remaining = 0
    private var curPixel = 0

    private var nBits = 0
    private val maxbits = BITS
    private var maxcode = 0
    private val maxmaxcode = 1 shl BITS

    private val htab = IntArray(HSIZE)
    private val codetab = IntArray(HSIZE)

    private var hsize = HSIZE
    private var freeEnt = 0
    private var clearFlg = false

    private var gInitBits = 0
    private var clearCode = 0
    private var eofCode = 0

    private var curAccum = 0
    private var curBits = 0

    private val masks = intArrayOf(
        0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F, 0x00FF,
        0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF,
    )

    private var aCount = 0
    private val accum = ByteArray(256)

    fun encode(os: OutputStream) {
        os.write(initCodeSize) // write "initial code size" byte
        remaining = imgW * imgH
        curPixel = 0
        compress(initCodeSize + 1, os)
        os.write(0) // block terminator
    }

    private fun maxcode(nBits: Int): Int = (1 shl nBits) - 1

    private fun nextPixel(): Int {
        if (remaining == 0) return EOF
        remaining--
        val pix = pixels[curPixel++]
        return pix.toInt() and 0xff
    }

    private fun compress(initBits: Int, os: OutputStream) {
        gInitBits = initBits
        clearFlg = false
        nBits = gInitBits
        maxcode = maxcode(nBits)

        clearCode = 1 shl (initBits - 1)
        eofCode = clearCode + 1
        freeEnt = clearCode + 2

        aCount = 0

        var ent = nextPixel()

        var hshift = 0
        var fcode = hsize
        while (fcode < 65536) {
            hshift++
            fcode *= 2
        }
        hshift = 8 - hshift
        val hsizeReg = hsize
        clHash(hsizeReg)

        output(clearCode, os)

        outerLoop@ while (true) {
            val c = nextPixel()
            if (c == EOF) break

            fcode = (c shl maxbits) + ent
            var i = (c shl hshift) xor ent

            if (htab[i] == fcode) {
                ent = codetab[i]
                continue
            } else if (htab[i] >= 0) {
                var disp = hsizeReg - i
                if (i == 0) disp = 1
                do {
                    i -= disp
                    if (i < 0) i += hsizeReg
                    if (htab[i] == fcode) {
                        ent = codetab[i]
                        continue@outerLoop
                    }
                } while (htab[i] >= 0)
            }
            output(ent, os)
            ent = c
            if (freeEnt < maxmaxcode) {
                codetab[i] = freeEnt++
                htab[i] = fcode
            } else {
                clearBlock(os)
            }
        }

        output(ent, os)
        output(eofCode, os)
    }

    private fun output(code: Int, os: OutputStream) {
        curAccum = curAccum and masks[curBits]
        curAccum = if (curBits > 0) curAccum or (code shl curBits) else code
        curBits += nBits

        while (curBits >= 8) {
            charOut((curAccum and 0xff).toByte(), os)
            curAccum = curAccum shr 8
            curBits -= 8
        }

        if (freeEnt > maxcode || clearFlg) {
            if (clearFlg) {
                nBits = gInitBits
                maxcode = maxcode(nBits)
                clearFlg = false
            } else {
                nBits++
                maxcode = if (nBits == maxbits) maxmaxcode else maxcode(nBits)
            }
        }

        if (code == eofCode) {
            while (curBits > 0) {
                charOut((curAccum and 0xff).toByte(), os)
                curAccum = curAccum shr 8
                curBits -= 8
            }
            flushChar(os)
        }
    }

    private fun clearBlock(os: OutputStream) {
        clHash(hsize)
        freeEnt = clearCode + 2
        clearFlg = true
        output(clearCode, os)
    }

    private fun clHash(hsize: Int) {
        for (i in 0 until hsize) htab[i] = -1
    }

    private fun charOut(c: Byte, os: OutputStream) {
        accum[aCount++] = c
        if (aCount >= 254) flushChar(os)
    }

    private fun flushChar(os: OutputStream) {
        if (aCount > 0) {
            os.write(aCount)
            os.write(accum, 0, aCount)
            aCount = 0
        }
    }
}
