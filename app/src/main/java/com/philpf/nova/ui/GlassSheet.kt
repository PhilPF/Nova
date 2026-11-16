package com.philpf.nova.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.philpf.nova.ui.glass.Backdrop
import com.philpf.nova.ui.glass.GlassSurface
import com.philpf.nova.ui.theme.LocalGlass
import com.philpf.nova.ui.theme.Radius
import com.philpf.nova.ui.theme.Space
import kotlin.math.roundToInt

/** Which part of a sheet is being drawn: its fixed head, or the body that scrolls below it. */
enum class SheetPart { HEADER, BODY }

val LocalSheetPart = compositionLocalOf { SheetPart.BODY }

/**
 * In a sheet's content, the part that stays at the top (title, picture, main buttons) while the
 * rest scrolls under it. The content is drawn twice, once for each part: this returns true in
 * the head's pass, after drawing [head], so the caller returns and draws nothing else there.
 */
@Composable
fun sheetHeader(head: @Composable () -> Unit): Boolean {
    val isHeader = LocalSheetPart.current == SheetPart.HEADER
    if (isHeader) head()
    return isHeader
}

/** The share of the screen a sheet opens to at most, and grows to. */
private const val OPEN_FRACTION = 0.75f
private const val FULL_FRACTION = 0.9f

/**
 * A glass sheet that slides up from the bottom over a dimmed screen. It opens at most three
 * quarters of the screen high (less for less content); its head (the part drawn by
 * [sheetHeader]: picture, title, main buttons) stays while the body scrolls under it, fading.
 *
 * Moving up, by its handle or head or by scrolling the body, first grows it to its full height
 * (at most 90% of the screen), then scrolls; moving down scrolls back, then shrinks it, then
 * closes it. Let go, it settles at the nearer height (or where a flick sends it). It also
 * closes when you tap outside it or press Back.
 */
@Composable
fun BoxScope.GlassSheet(
    visible: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlass.current
    val density = LocalDensity.current
    val dismiss by rememberUpdatedState(onDismiss)
    val dismissDistance = with(density) { 110.dp.toPx() }
    val screen = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val scroll = rememberScrollState()

    // Heights in pixels: what the content needs, and the two the sheet rests at.
    var headPx by remember { mutableIntStateOf(0) }
    var bodyPx by remember { mutableIntStateOf(0) }
    val natural = (headPx + bodyPx).toFloat()
    val measured = headPx > 0 && bodyPx > 0
    val openPx = minOf(natural, screen * OPEN_FRACTION)
    val fullPx = minOf(natural, screen * FULL_FRACTION)

    var expanded by remember { mutableStateOf(false) }
    // Set while a finger (or the settling after it) moves the sheet's height.
    var dragHeight by remember { mutableStateOf<Float?>(null) }
    var pull by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            pull = 0f
            expanded = false
            dragHeight = null
            scroll.scrollTo(0)
        }
    }
    fun height() = (dragHeight ?: if (expanded) fullPx else openPx).coerceIn(openPx, fullPx)

    /** Moves the sheet by [dy] (down positive); returns what it used. */
    fun moveBy(dy: Float): Float {
        if (dy < 0f) {
            if (pull > 0f) {
                val used = maxOf(dy, -pull)
                pull += used
                return used
            }
            val h = height()
            if (h < fullPx - 0.5f) {
                val grow = minOf(-dy, fullPx - h)
                dragHeight = h + grow
                return -grow
            }
            return 0f
        }
        var rest = dy
        val h = height()
        if (h > openPx + 0.5f) {
            val shrink = minOf(rest, h - openPx)
            dragHeight = h - shrink
            rest -= shrink
        }
        pull += rest
        return dy
    }

    /** After the finger: closes, springs back, or rests at the nearer height. */
    suspend fun settle(velocityY: Float) {
        if (pull > 0f) {
            if (pull > dismissDistance || velocityY > 1800f) dismiss()
            else animate(pull, 0f, animationSpec = tween(220)) { v, _ -> pull = v }
            return
        }
        val h = dragHeight ?: return
        val toFull = when {
            velocityY < -600f -> true
            velocityY > 600f -> false
            else -> h > (openPx + fullPx) / 2f
        }
        animate(h, if (toFull) fullPx else openPx, animationSpec = tween(240)) { v, _ -> dragHeight = v }
        expanded = toFull
        dragHeight = null
    }

    // The connection lives across frames; it calls the latest of these, which read the
    // heights as they are now.
    val latestMove = rememberUpdatedState<(Float) -> Float> { moveBy(it) }
    val latestSettle = rememberUpdatedState<suspend (Float) -> Unit> { settle(it) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0f) Offset(0f, latestMove.value(available.y)) else Offset.Zero

            @Suppress("DEPRECATION") // NestedScrollSource.Drag: a finger drag, not a fling
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0f && source == NestedScrollSource.Drag) Offset(0f, latestMove.value(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f && dragHeight == null) return Velocity.Zero
                latestSettle.value(available.y)
                return available
            }
        }
    }

    BackHandler(enabled = visible) { dismiss() }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(glass.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() },
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
    ) {
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .offset { IntOffset(0, pull.roundToInt()) }
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Space.small)
                .padding(bottom = Space.small)
                .fillMaxWidth()
                // Until the content is measured, as high as it opens at most.
                .then(
                    if (measured) Modifier.height(with(density) { height().toDp() })
                    else Modifier.heightIn(max = with(density) { (screen * OPEN_FRACTION).toDp() }),
                ),
            shape = RoundedCornerShape(Radius.panel),
            frost = glass.frost,
        ) {
            Column(Modifier.fillMaxWidth()) {
                // The head: the handle and what stays; dragging it moves the sheet.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { headPx = it.height }
                        .draggable(
                            state = rememberDraggableState { delta -> moveBy(delta) },
                            orientation = Orientation.Vertical,
                            onDragStopped = { v -> settle(v) },
                        )
                        .padding(horizontal = Space.margin)
                        .padding(top = Space.gap),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 38.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(glass.textTertiary.copy(alpha = 0.35f)),
                    )
                    Spacer(Modifier.height(Space.small))
                    CompositionLocalProvider(LocalSheetPart provides SheetPart.HEADER) { content() }
                }
                // The body, scrolling under the head and fading out at its top.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (scroll.value > 0) {
                                val fade = minOf(Space.section.toPx(), scroll.value.toFloat())
                                drawRect(
                                    Brush.verticalGradient(0f to Color.Transparent, fade / size.height to Color.Black),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                        .nestedScroll(connection)
                        .verticalScroll(scroll),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { bodyPx = it.height }
                            .padding(horizontal = Space.margin)
                            .padding(bottom = Space.section),
                    ) {
                        CompositionLocalProvider(LocalSheetPart provides SheetPart.BODY) { content() }
                    }
                }
            }
        }
    }
}
