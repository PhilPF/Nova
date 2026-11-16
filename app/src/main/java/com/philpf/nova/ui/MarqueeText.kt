package com.philpf.nova.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.delay

/** Whether long titles scroll (a setting); off, they end in "…". */
val LocalScrollTitles = staticCompositionLocalOf { true }

/** How a long title moves: slowly enough to read along. */
private const val SPEED_DP_PER_S = 30f
private const val REST_AT_START_MS = 2_000L
private const val REST_AT_END_MS = 1_500L
private const val FADE_MS = 300

/**
 * One line of text that, when it does not fit, shows its start for a moment, glides left at a
 * steady reading pace until its end shows, rests there, then fades back to its start and
 * begins again. The edges fade where text is hidden, so the cut never looks like the end of
 * the text. When it fits, or when [animate] is off (or the setting is), it is plain text
 * ending in "…" if it must.
 */
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val enabled = animate && LocalScrollTitles.current
    if (!enabled) {
        Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    var boxWidth by remember { mutableIntStateOf(0) }
    var textWidth by remember { mutableIntStateOf(0) }
    val overflow = (textWidth - boxWidth).coerceAtLeast(0)
    val offset = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val density = LocalDensity.current
    val fadePx = with(density) { 16.dp.toPx() }

    LaunchedEffect(text, overflow) {
        offset.snapTo(0f)
        alpha.snapTo(1f)
        if (overflow <= 0) return@LaunchedEffect
        val pxPerMs = with(density) { SPEED_DP_PER_S.dp.toPx() } / 1000f
        while (true) {
            delay(REST_AT_START_MS)
            offset.animateTo(-overflow.toFloat(), tween((overflow / pxPerMs).toInt().coerceAtLeast(400), easing = LinearEasing))
            delay(REST_AT_END_MS)
            alpha.animateTo(0f, tween(FADE_MS))
            offset.snapTo(0f)
            alpha.animateTo(1f, tween(FADE_MS))
        }
    }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { boxWidth = it.width }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (overflow > 0) {
                    // Fade the left edge once text has gone past it, the right while text is beyond it.
                    val w = size.width
                    val left = if (offset.value < -1f) fadePx / w else 0f
                    val right = if (offset.value > -overflow + 1f) 1f - fadePx / w else 1f
                    drawRect(
                        Brush.horizontalGradient(0f to (if (left > 0f) Color.Transparent else Color.Black), left to Color.Black, right to Color.Black, 1f to (if (right < 1f) Color.Transparent else Color.Black)),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        Text(
            text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .onSizeChanged { textWidth = it.width }
                .offset { IntOffset(offset.value.toInt(), 0) }
                .graphicsLayer { this.alpha = alpha.value },
        )
    }
}
