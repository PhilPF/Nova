package com.philpf.nova.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How long one wave takes to flood its panel and fade away, in ms. */
private const val WAVE_MS = 1_000

/** Only beyond this many at once do the oldest give way. */
private const val MAX_WAVES = 8

/** One wave: where it starts (in the panel's own coordinates), its colour, and how far it is (0 to 1). */
class Wave internal constructor(val origin: Offset, val color: Color, val fraction: Offset? = null) {
    internal val progress = Animatable(0f)
}

/**
 * Waves of light spreading across a panel, as Dualsense Manager's panels flood with a
 * controller's colour when it is pressed: here, with the colour of a song's art, when it
 * starts or is touched. Each wave runs to its end, even when another starts.
 */
@Stable
class Waves internal constructor(private val scope: CoroutineScope) {
    internal val list = mutableStateListOf<Wave>()

    /** Starts a wave at [origin] (unspecified: the panel's centre) in [color]. */
    fun start(color: Color, origin: Offset = Offset.Unspecified) = launch(Wave(origin, color))

    /** Starts a wave at a point given as fractions of the panel's width and height. */
    fun startAt(color: Color, x: Float, y: Float) = launch(Wave(Offset.Unspecified, color, Offset(x, y)))

    private fun launch(wave: Wave) {
        list += wave
        while (list.size > MAX_WAVES) list.removeAt(0)
        scope.launch {
            wave.progress.animateTo(1f, tween(WAVE_MS, easing = LinearEasing))
            list -= wave
        }
    }
}

@Composable
fun rememberWaves(): Waves {
    val scope = rememberCoroutineScope()
    return remember { Waves(scope) }
}

/** Draws the waves under the content; the caller clips to its panel. */
fun Modifier.waves(waves: Waves): Modifier = drawBehind { drawWaves(waves) }

/**
 * Draws [waves]: discs growing from their origin until they cover the whole area, brightest
 * at their front, which then fade. (The drawing of Dualsense Manager's press waves.)
 */
fun DrawScope.drawWaves(waves: Waves) {
    for (wave in waves.list) {
        val origin = when {
            wave.fraction != null -> Offset(size.width * wave.fraction.x, size.height * wave.fraction.y)
            wave.origin.isSpecified -> wave.origin
            else -> center
        }
        val reach = maxOf(
            origin.getDistance(),
            (origin - Offset(size.width, 0f)).getDistance(),
            (origin - Offset(0f, size.height)).getDistance(),
            (origin - Offset(size.width, size.height)).getDistance(),
        )
        val t = wave.progress.value
        // The front reaches the farthest corner at about two thirds of the time, then the
        // flooded panel fades.
        val radius = 1f + reach * 1.05f * FastOutSlowInEasing.transform(t)
        // It fades in while still small, so it starts as a wave rather than a bright spot,
        // and fades out once the panel is flooded.
        val strength = 0.55f * smoothstep(0f, 0.12f, t) * (1f - smoothstep(0.35f, 1f, t))
        if (strength <= 0.001f) continue
        val light = wave.color
        drawCircle(
            brush = Brush.radialGradient(
                0f to light.copy(alpha = strength * 0.45f),
                0.72f to light.copy(alpha = strength * 0.7f),
                0.95f to light.copy(alpha = strength),
                1f to light.copy(alpha = 0f),
                center = origin,
                radius = radius,
            ),
            radius = radius,
            center = origin,
        )
    }
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Keeps where the last touch on it went down, for a wave to start there. Does not consume it. */
fun Modifier.lastTouch(onDown: (Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onDown(down.position)
    }
}

/** The colour a song's waves take: its art's main colour, else [fallback]. */
suspend fun waveColor(art: String?, fallback: Color): Color =
    art?.let { ArtCache.colors(it).firstOrNull() } ?: fallback
