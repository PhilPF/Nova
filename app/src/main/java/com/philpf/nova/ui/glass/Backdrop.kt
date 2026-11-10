package com.philpf.nova.ui.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.philpf.nova.ui.theme.LocalGlass

/**
 * Content that glass surfaces can show, blurred, behind themselves.
 *
 * Android has no system glass material, so the content is recorded into a graphics layer
 * ([backdropSource]) and each [GlassSurface] draws that layer again, offset so it lines up
 * with what is really behind the surface, through a blur. The layer is shared, not copied:
 * when the content changes, every glass surface follows without redrawing.
 */
@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Draws this content as usual and records it into [backdrop]. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * Flat frosted glass: the [backdrop] blurred (with a little extra saturation so colours don't
 * go grey), a faint white gradient, an optional [tint], a thin light rim and, on light
 * backgrounds, a hairline edge. No shadows or bevels. A surface must not be inside the content
 * of the backdrop it shows.
 */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Brush? = null,
    /** A flat fill over the blur that lets less of the background through (used by sheets). */
    frost: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlass.current
    val blurLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val effect = remember(density) { glassEffect(with(density) { 24.dp.toPx() }) }
    val sheenStopPx = with(density) { 72.dp.toPx() }
    val rim = remember(glass) {
        Brush.linearGradient(0f to glass.rim[0], 0.4f to glass.rim[1], 0.65f to glass.rim[2], 1f to glass.rim[3])
    }
    var position by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                val offset = backdrop.origin - position
                blurLayer.renderEffect = effect
                blurLayer.record {
                    translate(offset.x, offset.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(blurLayer)
                if (frost.alpha > 0f) drawRect(frost)
                val stop = (sheenStopPx / size.height).coerceIn(0.01f, 0.99f)
                drawRect(Brush.verticalGradient(0f to glass.sheenTop, stop to glass.sheenMiddle, 1f to glass.sheenBottom))
                if (tint != null) drawRect(tint)
            }
            .border(1.5.dp, rim, shape)
            .then(if (glass.outline.alpha > 0f) Modifier.border(0.75.dp, glass.outline, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** A glass fill without the blur, for small controls sitting on a glass panel. */
val GlassControlShape = RoundedCornerShape(percent = 50)

private fun glassEffect(radiusPx: Float): RenderEffect {
    val saturate = android.graphics.ColorMatrix().apply { setSaturation(1.35f) }
    return android.graphics.RenderEffect.createChainEffect(
        android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(saturate)),
        android.graphics.RenderEffect.createBlurEffect(radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP),
    ).asComposeRenderEffect()
}

internal val Transparent = Color(0x00000000)
