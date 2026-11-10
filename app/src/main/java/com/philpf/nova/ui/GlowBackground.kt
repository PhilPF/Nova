package com.philpf.nova.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import com.philpf.nova.ui.theme.GlassPalette

/**
 * A glow of [color] centred at ([x], [y]) as fractions of the screen, [radius] a fraction of
 * its width, and how strongly it shines (1 = normal).
 */
data class GlowSpot(val x: Float, val y: Float, val color: Color, val radius: Float = 0.75f, val strength: Float = 1f)

/**
 * The scene behind the glass: a plain gradient with soft glows in the colours of the music
 * playing. The glass panels blur it, so their colour seems to diffuse through them.
 */
@Composable
fun GlowBackground(spots: () -> List<GlowSpot>, glass: GlassPalette, modifier: Modifier = Modifier) {
    val noise = rememberNoiseBrush(glass.isDark)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(glass.backgroundTop, glass.backgroundBottom)))
        // A faint wash of the accent so an empty screen isn't flat.
        val wash = Offset(size.width * 0.15f, 0f)
        drawCircle(
            Brush.radialGradient(listOf(glass.accent.copy(alpha = 0.12f), Color.Transparent), center = wash, radius = size.width),
            radius = size.width,
            center = wash,
        )
        for (spot in spots()) {
            if (spot.strength <= 0.01f) continue
            val center = Offset(size.width * spot.x, size.height * spot.y)
            val radius = size.width * spot.radius
            val a = (glass.glowAlpha * spot.strength).coerceAtMost(0.95f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to spot.color.copy(alpha = a),
                    0.45f to spot.color.copy(alpha = a * 0.38f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        drawRect(noise)
    }
}

/** Fine noise that breaks up banding in the smooth gradients. */
@Composable
private fun rememberNoiseBrush(dark: Boolean): Brush = remember(dark) {
    val n = 128
    val random = java.util.Random(7)
    val alpha = if (dark) 7 else 5
    val pixels = IntArray(n * n) {
        val v = random.nextInt(256)
        (alpha shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}
