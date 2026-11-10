package com.philpf.nova.core

/**
 * The main colours of a picture, for the glows behind the glass: the pixels (ARGB) of a small
 * copy of it are binned by hue, each weighted by its saturation and brightness so that greys
 * count little, and the heaviest bins give their average colour.
 */
object ArtColors {
    private const val BINS = 12

    fun dominant(pixels: IntArray, count: Int = 3): List<Int> {
        val weight = DoubleArray(BINS)
        val r = DoubleArray(BINS)
        val g = DoubleArray(BINS)
        val b = DoubleArray(BINS)
        val hsv = FloatArray(3)
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            toHsv(pr, pg, pb, hsv)
            val w = (hsv[1] * hsv[1] * hsv[2]).toDouble()
            if (w < 0.02) continue
            val bin = ((hsv[0] / 360f) * BINS).toInt().coerceIn(0, BINS - 1)
            weight[bin] += w
            r[bin] += pr * w
            g[bin] += pg * w
            b[bin] += pb * w
        }
        val total = weight.sum()
        if (total <= 0.0) return emptyList()
        return weight.indices
            .filter { weight[it] >= total * 0.06 }
            .sortedByDescending { weight[it] }
            .take(count)
            .map { i ->
                val w = weight[i]
                (0xFF shl 24) or ((r[i] / w).toInt() shl 16) or ((g[i] / w).toInt() shl 8) or (b[i] / w).toInt()
            }
    }

    /** Hue in degrees, saturation and value in [0, 1]. */
    fun toHsv(r: Int, g: Int, b: Int, out: FloatArray) {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = (max - min).toFloat()
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        out[0] = if (h < 0) h + 360f else h
        out[1] = if (max == 0) 0f else d / max
        out[2] = max / 255f
    }
}
