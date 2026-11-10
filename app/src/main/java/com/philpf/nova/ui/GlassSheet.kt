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
import androidx.compose.foundation.layout.height
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
import kotlin.math.roundToInt

/**
 * A glass sheet that slides up from the bottom over a dimmed screen. It closes when you tap
 * outside it, swipe it down, or press Back.
 */
@Composable
fun BoxScope.GlassSheet(
    visible: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlass.current
    val dismiss by rememberUpdatedState(onDismiss)
    val dismissDistance = with(LocalDensity.current) { 110.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible) { if (visible) pull = 0f }

    // Pulling down when the content is at its top drags the whole sheet; letting go far enough
    // (or flinging down) closes it, otherwise it springs back.
    val pullToDismiss = remember {
        object : NestedScrollConnection {
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
                if (pull > dismissDistance || available.y > 1800f) {
                    dismiss()
                } else {
                    animate(pull, 0f, animationSpec = tween(220)) { value, _ -> pull = value }
                }
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
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
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
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            shape = RoundedCornerShape(34.dp),
            frost = glass.frost,
        ) {
            Column(
                Modifier
                    .nestedScroll(pullToDismiss)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 22.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(glass.textTertiary.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}
