package com.leaptools.giffer.service.gif

/*
 * NeuQuant Neural-Net image quantization algorithm.
 *
 * Copyright (c) 1994 Anthony Dekker. "Kohonen neural networks for optimal colour
 * quantization" in Network: Computation in Neural Systems Vol. 5 (1994) pp 351-367.
 * Anthony Dekker grants permission to use, copy, modify, and distribute this software and
 * its documentation for any purpose and without fee, provided that this copyright notice
 * remains. (See https://www.cs.bath.ac.uk/~rwh/neuquant/, and the widely used Java port by
 * Kevin Weiner, FM Software.)
 *
 * Kotlin port for Giffer. Produces a 256-colour palette from a stream of RGB samples.
 */
class NeuQuant(
    private val thepicture: ByteArray, // BGR triples
    private val lengthcount: Int,
    private val samplefac: Int,
) {
    companion object {
        private const val netsize = 256
        private const val prime1 = 499
        private const val prime2 = 491
        private const val prime3 = 487
        private const val prime4 = 503
        private const val minpicturebytes = 3 * prime4

        private const val maxnetpos = netsize - 1
        private const val netbiasshift = 4
        private const val ncycles = 100

        private const val intbiasshift = 16
        private const val intbias = 1 shl intbiasshift
        private const val gammashift = 10
        private const val betashift = 10
        private const val beta = intbias shr betashift
        private const val betagamma = intbias shl (gammashift - betashift)

        private const val initrad = netsize shr 3
        private const val radiusbiasshift = 6
        private const val radiusbias = 1 shl radiusbiasshift
        private const val initradius = initrad * radiusbias
        private const val radiusdec = 30

        private const val alphabiasshift = 10
        private const val initalpha = 1 shl alphabiasshift

        private const val radbiasshift = 8
        private const val radbias = 1 shl radbiasshift
        private const val alpharadbshift = alphabiasshift + radbiasshift
        private const val alpharadbias = 1 shl alpharadbshift
    }

    private var alphadec = 0

    // the network itself: [netsize][4] (b, g, r, index)
    private val network = Array(netsize) { IntArray(4) }
    private val netindex = IntArray(256)
    private val bias = IntArray(netsize)
    private val freq = IntArray(netsize)
    private val radpower = IntArray(initrad)

    init {
        for (i in 0 until netsize) {
            val p = network[i]
            val v = (i shl (netbiasshift + 8)) / netsize
            p[0] = v
            p[1] = v
            p[2] = v
            freq[i] = intbias / netsize
            bias[i] = 0
        }
    }

    /** Returns the palette as BGR triples (length netsize*3), ordered by network index. */
    fun colorMap(): ByteArray {
        val map = ByteArray(netsize * 3)
        val index = IntArray(netsize)
        for (i in 0 until netsize) index[network[i][3]] = i
        var k = 0
        for (i in 0 until netsize) {
            val j = index[i]
            map[k++] = network[j][0].toByte()
            map[k++] = network[j][1].toByte()
            map[k++] = network[j][2].toByte()
        }
        return map
    }

    /** Insertion sort of network and building of netindex[0..255]. */
    private fun inxbuild() {
        var previouscol = 0
        var startpos = 0
        for (i in 0 until netsize) {
            val p = network[i]
            var smallpos = i
            var smallval = p[1] // index on g
            // find smallest in i..netsize-1
            for (j in i + 1 until netsize) {
                val q = network[j]
                if (q[1] < smallval) {
                    smallpos = j
                    smallval = q[1]
                }
            }
            val q = network[smallpos]
            if (i != smallpos) {
                var t = q[0]; q[0] = p[0]; p[0] = t
                t = q[1]; q[1] = p[1]; p[1] = t
                t = q[2]; q[2] = p[2]; p[2] = t
                t = q[3]; q[3] = p[3]; p[3] = t
            }
            if (smallval != previouscol) {
                netindex[previouscol] = (startpos + i) shr 1
                for (j in previouscol + 1 until smallval) netindex[j] = i
                previouscol = smallval
                startpos = i
            }
        }
        netindex[previouscol] = (startpos + maxnetpos) shr 1
        for (j in previouscol + 1 until 256) netindex[j] = maxnetpos
    }

    /** Search for BGR values 0..255 and return colour index. */
    fun map(b: Int, g: Int, r: Int): Int {
        var bestd = 1000
        var best = -1
        var i = netindex[g]
        var j = i - 1

        while (i < netsize || j >= 0) {
            if (i < netsize) {
                val p = network[i]
                var dist = p[1] - g
                if (dist >= bestd) {
                    i = netsize
                } else {
                    i++
                    if (dist < 0) dist = -dist
                    var a = p[0] - b
                    if (a < 0) a = -a
                    dist += a
                    if (dist < bestd) {
                        a = p[2] - r
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            bestd = dist
                            best = p[3]
                        }
                    }
                }
            }
            if (j >= 0) {
                val p = network[j]
                var dist = g - p[1]
                if (dist >= bestd) {
                    j = -1
                } else {
                    j--
                    if (dist < 0) dist = -dist
                    var a = p[0] - b
                    if (a < 0) a = -a
                    dist += a
                    if (dist < bestd) {
                        a = p[2] - r
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            bestd = dist
                            best = p[3]
                        }
                    }
                }
            }
        }
        return best
    }

    fun process(): ByteArray {
        learn()
        unbiasnet()
        inxbuild()
        return colorMap()
    }

    private fun unbiasnet() {
        for (i in 0 until netsize) {
            network[i][0] = network[i][0] shr netbiasshift
            network[i][1] = network[i][1] shr netbiasshift
            network[i][2] = network[i][2] shr netbiasshift
            network[i][3] = i // record colour no
        }
    }

    private fun alterneigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
        var lo = i - rad
        if (lo < -1) lo = -1
        var hi = i + rad
        if (hi > netsize) hi = netsize

        var j = i + 1
        var k = i - 1
        var m = 1
        while (j < hi || k > lo) {
            val a = radpower[m++]
            if (j < hi) {
                val p = network[j++]
                p[0] -= (a * (p[0] - b)) / alpharadbias
                p[1] -= (a * (p[1] - g)) / alpharadbias
                p[2] -= (a * (p[2] - r)) / alpharadbias
            }
            if (k > lo) {
                val p = network[k--]
                p[0] -= (a * (p[0] - b)) / alpharadbias
                p[1] -= (a * (p[1] - g)) / alpharadbias
                p[2] -= (a * (p[2] - r)) / alpharadbias
            }
        }
    }

    private fun altersingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {
        val p = network[i]
        p[0] -= (alpha * (p[0] - b)) / initalpha
        p[1] -= (alpha * (p[1] - g)) / initalpha
        p[2] -= (alpha * (p[2] - r)) / initalpha
    }

    private fun contest(b: Int, g: Int, r: Int): Int {
        var bestd = Int.MAX_VALUE
        var bestbiasd = bestd
        var bestpos = -1
        var bestbiaspos = bestpos

        for (i in 0 until netsize) {
            val n = network[i]
            var dist = n[0] - b
            if (dist < 0) dist = -dist
            var a = n[1] - g
            if (a < 0) a = -a
            dist += a
            a = n[2] - r
            if (a < 0) a = -a
            dist += a
            if (dist < bestd) {
                bestd = dist
                bestpos = i
            }
            val biasdist = dist - (bias[i] shr (intbiasshift - netbiasshift))
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist
                bestbiaspos = i
            }
            val betafreq = freq[i] shr betashift
            freq[i] -= betafreq
            bias[i] += betafreq shl gammashift
        }
        freq[bestpos] += beta
        bias[bestpos] -= betagamma
        return bestbiaspos
    }

    private fun learn() {
        val p = thepicture
        val lengthcountLocal = lengthcount

        if (lengthcountLocal < minpicturebytes) {
            // samplefac forced to 1 for tiny images
        }
        alphadec = 30 + ((samplefac - 1) / 3)
        var pix = 0
        val lim = lengthcountLocal
        val samplepixels = lengthcountLocal / (3 * samplefac)
        var delta = samplepixels / ncycles
        if (delta == 0) delta = 1
        var alpha = initalpha
        var radius = initradius

        var rad = radius shr radiusbiasshift
        if (rad <= 1) rad = 0
        for (i in 0 until rad) {
            radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad))
        }

        val step: Int = when {
            lengthcountLocal < minpicturebytes -> 3
            lengthcountLocal % prime1 != 0 -> 3 * prime1
            lengthcountLocal % prime2 != 0 -> 3 * prime2
            lengthcountLocal % prime3 != 0 -> 3 * prime3
            else -> 3 * prime4
        }

        var i = 0
        while (i < samplepixels) {
            val b = (p[pix].toInt() and 0xff) shl netbiasshift
            val g = (p[pix + 1].toInt() and 0xff) shl netbiasshift
            val r = (p[pix + 2].toInt() and 0xff) shl netbiasshift
            val j = contest(b, g, r)

            altersingle(alpha, j, b, g, r)
            if (rad != 0) alterneigh(rad, j, b, g, r)

            pix += step
            if (pix >= lim) pix -= lengthcountLocal

            i++
            if (delta != 0 && i % delta == 0) {
                alpha -= alpha / alphadec
                radius -= radius / radiusdec
                rad = radius shr radiusbiasshift
                if (rad <= 1) rad = 0
                for (k in 0 until rad) {
                    radpower[k] = alpha * (((rad * rad - k * k) * radbias) / (rad * rad))
                }
            }
        }
    }
}
