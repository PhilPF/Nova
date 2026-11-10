package com.philpf.nova.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swiping a song sideways: to the left for the next, to the right for the previous. The song
 * follows the finger; let go past a quarter of the width (or flicked) and it slides out while
 * the next one slides in, else it springs back.
 */
@Stable
class SkipSwipe internal constructor(private val scope: CoroutineScope) {
    internal val offset = Animatable(0f)
    private var raw = 0f
    internal var onNext: () -> Unit = {}
    internal var onPrevious: () -> Unit = {}

    internal fun drag(delta: Float) {
        raw += delta
        scope.launch { offset.snapTo(raw) }
    }

    internal fun release(width: Float, velocity: Float = 0f) {
        val goNext = raw < -width * 0.25f || (velocity < -1200f && raw < 0)
        val goPrevious = raw > width * 0.25f || (velocity > 1200f && raw > 0)
        raw = 0f
        scope.launch {
            when {
                goNext -> {
                    offset.animateTo(-width, tween(150))
                    onNext()
                    offset.snapTo(width * 0.5f)
                    offset.animateTo(0f, tween(220))
                }
                goPrevious -> {
                    offset.animateTo(width, tween(150))
                    onPrevious()
                    offset.snapTo(-width * 0.5f)
                    offset.animateTo(0f, tween(220))
                }
                else -> offset.animateTo(0f, tween(200))
            }
        }
    }
}

@Composable
fun rememberSkipSwipe(onNext: () -> Unit, onPrevious: () -> Unit): SkipSwipe {
    val scope = rememberCoroutineScope()
    val swipe = remember { SkipSwipe(scope) }
    swipe.onNext = onNext
    swipe.onPrevious = onPrevious
    return swipe
}

/** Where sideways drags are read: a vertical drag still scrolls what is around it. */
fun Modifier.skipGesture(swipe: SkipSwipe): Modifier = pointerInput(swipe) {
    var last = 0L
    var velocity = 0f
    detectHorizontalDragGestures(
        onDragStart = {
            last = System.nanoTime()
            velocity = 0f
        },
        onDragEnd = { swipe.release(size.width.toFloat(), velocity) },
        onDragCancel = { swipe.release(size.width.toFloat()) },
    ) { change, delta ->
        change.consume()
        val now = System.nanoTime()
        val dt = (now - last).coerceAtLeast(1) / 1e9f
        velocity = 0.7f * velocity + 0.3f * (delta / dt)
        last = now
        swipe.drag(delta)
    }
}

/**
 * What moves with the finger: the song's picture and names. A video only fades ([move] false):
 * its surface is drawn by the system and would not follow a moved layer.
 */
fun Modifier.skipOffset(swipe: SkipSwipe, move: Boolean = true): Modifier = graphicsLayer {
    val x = swipe.offset.value
    if (move) translationX = x
    alpha = (1f - abs(x) / (size.width.coerceAtLeast(1f)) * 0.8f).coerceIn(0.2f, 1f)
}

/** Swiping up on it (the mini player) opens what it stands for. */
fun Modifier.swipeUpGesture(onUp: () -> Unit): Modifier = pointerInput(Unit) {
    var total = 0f
    detectVerticalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (total < -32.dp.toPx()) onUp() },
    ) { change, dy ->
        change.consume()
        total += dy
    }
}

/**
 * Pulling a page down from the top of its scroll closes it: the page follows the finger, and
 * letting go far enough down (or flinging down) closes it, otherwise it springs back.
 */
@Stable
class PullToClose internal constructor(private val threshold: Float, private val onClose: () -> Unit) : NestedScrollConnection {
    var pull by mutableFloatStateOf(0f)
        private set

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y < 0f && pull > 0f) {
            val used = maxOf(available.y, -pull)
            pull += used
            return Offset(0f, used)
        }
        return Offset.Zero
    }

    @Suppress("DEPRECATION") // NestedScrollSource.Drag: a finger drag, not a fling
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y > 0f && source == NestedScrollSource.Drag) {
            pull += available.y
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (pull <= 0f) return Velocity.Zero
        if (pull > threshold || available.y > 1800f) {
            onClose()
        } else {
            animate(pull, 0f, animationSpec = tween(220)) { value, _ -> pull = value }
        }
        return available
    }

    fun reset() {
        pull = 0f
    }
}

@Composable
fun rememberPullToClose(onClose: () -> Unit): PullToClose {
    val latest by rememberUpdatedState(onClose)
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    return remember { PullToClose(threshold) { latest() } }
}
