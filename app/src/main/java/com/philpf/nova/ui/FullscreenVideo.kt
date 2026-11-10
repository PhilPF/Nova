package com.philpf.nova.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.philpf.nova.core.Library
import com.philpf.nova.ui.art.Icon
import com.philpf.nova.ui.art.NovaIcon
import com.philpf.nova.ui.theme.GlassType
import kotlinx.coroutines.delay

/**
 * The video over the whole screen, landscape when the video is. A tap shows the controls (they
 * hide again after a few seconds of playing): the song, the position with a mark per song of
 * a long video, the buttons, and its tracklist.
 *
 * [lockLandscape] turns the screen to landscape; when full screen came from turning the phone,
 * it is left to the sensor, and turning back to portrait leaves full screen.
 */
@Composable
fun FullscreenVideo(connection: PlayerConnection, library: Library, lockLandscape: Boolean, onExit: () -> Unit) {
    val state = connection.state
    val track = state.mediaId?.let { library.track(it) }
    val position = rememberPosition(connection, active = true)
    val now = track?.let { NowPlaying(it, position, state.durationMs) }
    var controls by remember { mutableStateOf(true) }
    var touched by remember { mutableIntStateOf(0) }
    var tracklistOpen by remember { mutableStateOf(false) }

    val activity = LocalContext.current as? Activity
    val view = LocalView.current
    val wide = state.videoSize.width >= state.videoSize.height
    DisposableEffect(lockLandscape, wide) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        if (lockLandscape && wide) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            if (lockLandscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(controls, touched, state.isPlaying, tracklistOpen) {
        if (controls && state.isPlaying && !tracklistOpen) {
            delay(3500)
            controls = false
        }
    }
    BackHandler {
        if (tracklistOpen) tracklistOpen = false else onExit()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controls = !controls
                touched++
            },
    ) {
        VideoSurface(connection.controller, Modifier.fillMaxSize())

        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 0.3f to Color.Transparent, 0.65f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)),
                ),
            ) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlainIconButton(NovaIcon.FULLSCREEN_EXIT, "Leave full screen", onExit, Color.White)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(now?.title ?: "", style = GlassType.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val line = listOfNotNull(now?.artist, if (now?.chapterTrack != null) "${now.chapterIndex + 1} of ${now.chapters.size} · ${now.track.file.title}" else null).joinToString(" · ")
                            if (line.isNotEmpty()) Text(line, style = GlassType.caption, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (now != null && now.chapters.isNotEmpty()) {
                            PlainIconButton(NovaIcon.LIST, "Tracklist", { tracklistOpen = !tracklistOpen; touched++ }, Color.White)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    SeekBar(
                        positionMs = position,
                        durationMs = state.durationMs,
                        markers = now?.chapters?.map { it.startMs }.orEmpty(),
                        onSeek = { connection.seekTo(it); touched++ },
                        trackColor = Color.White.copy(alpha = 0.25f),
                        fillColor = Color.White,
                        markerColor = Color.Black,
                        timeColor = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        PlainIconButton(NovaIcon.PREVIOUS, "Previous", { connection.previous(); touched++ }, Color.White, size = 56.dp, iconSize = 32.dp)
                        Spacer(Modifier.width(18.dp))
                        PlayButton(state.playWhenReady, { connection.togglePlay(); touched++ }, size = 60.dp, fill = Color.White, iconColor = Color.Black)
                        Spacer(Modifier.width(18.dp))
                        PlainIconButton(NovaIcon.NEXT, "Next", { connection.next(); touched++ }, Color.White, size = 56.dp, iconSize = 32.dp)
                    }
                }
            }
        }

        // The tracklist, on the right; the video's frames cannot be blurred, so it is dark glass.
        AnimatedVisibility(tracklistOpen && controls && now != null, modifier = Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
            val file = track?.file
            if (file != null && now != null) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(340.dp)
                        .safeDrawingPadding()
                        .padding(vertical = 64.dp, horizontal = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xD90B0E1A))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { touched++ },
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                        Text("TRACKLIST", style = GlassType.section, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.philpf.nova.ui.theme.LocalGlass provides com.philpf.nova.ui.theme.DarkGlass,
                        ) {
                            Tracklist(file, now.chapterIndex) { connection.seekTo(now.chapters[it].startMs); touched++ }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
