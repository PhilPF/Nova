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

    /**
     * [argb] made into an accent that reads on the theme: its hue kept; on a dark theme lifted
     * until its luminance is at least 0.4 (and not too saturated, so it does not glare), on a
     * light theme deepened until it is at most 0.2, so text in it stays legible. Greys get
     * no hue, so callers keep their own accent for them (see [dominant], which skips greys).
     */
    fun accent(argb: Int, dark: Boolean): Int {
        val hsv = FloatArray(3)
        toHsv((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, hsv)
        var s = hsv[1]
        var v = hsv[2]
        if (dark) {
            s = s.coerceIn(0.35f, 0.7f)
            v = maxOf(v, 0.9f)
            var c = fromHsv(hsv[0], s, v)
            // Blues and violets are dark even at full value: take saturation away until light enough.
            while (luminance(c) < 0.4 && s > 0.15f) {
                s -= 0.05f
                c = fromHsv(hsv[0], s, 1f)
            }
            return c
        }
        s = s.coerceIn(0.55f, 0.95f)
        v = v.coerceIn(0.35f, 0.85f)
        var c = fromHsv(hsv[0], s, v)
        while (luminance(c) > 0.2 && v > 0.3f) {
            v -= 0.05f
            c = fromHsv(hsv[0], s, v)
        }
        return c
    }

    /** Relative luminance (sRGB, as WCAG defines it), 0 to 1. */
    fun luminance(argb: Int): Double {
        fun ch(x: Int): Double {
            val c = x / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - Math.abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt() % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun to8(f: Float) = ((f + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
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
