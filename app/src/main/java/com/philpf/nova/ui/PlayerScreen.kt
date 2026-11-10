package com.philpf.nova.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.philpf.nova.core.Chapters
import com.philpf.nova.core.Library
import com.philpf.nova.core.MediaFile
import com.philpf.nova.core.Track
import com.philpf.nova.ui.art.Icon
import com.philpf.nova.ui.art.NovaIcon
import com.philpf.nova.ui.glass.GlassIconButton
import com.philpf.nova.ui.glass.GlassSurface
import com.philpf.nova.ui.glass.SectionLabel
import com.philpf.nova.ui.glass.backdropSource
import com.philpf.nova.ui.glass.rememberBackdrop
import com.philpf.nova.ui.theme.GlassType
import com.philpf.nova.ui.theme.LocalGlass
import kotlinx.coroutines.delay

/** What is playing, as the player page shows it: the track, and the song inside it when it is a long video. */
class NowPlaying(val track: Track, val positionMs: Long, val durationMs: Long) {
    /** The file's songs, when the whole file is playing (a clip is one song already). */
    val chapters = if (track.isClip) emptyList() else track.file.chapters
    val chapterIndex = Chapters.indexAt(chapters, positionMs)
    val chapterTrack: Track? = chapterIndex.takeIf { it >= 0 }?.let { track.file.chapterTrack(it) }
    val title get() = chapterTrack?.title ?: track.title
    val artist get() = chapterTrack?.artist ?: track.artist
}

/** The position of the player, read a few times a second while [active]. */
@Composable
fun rememberPosition(connection: PlayerConnection, active: Boolean, intervalMs: Long = 250): Long {
    var position by remember { mutableLongStateOf(connection.position()) }
    LaunchedEffect(active, connection.controller) {
        while (active) {
            position = connection.position()
            delay(intervalMs)
        }
    }
    return position
}

/**
 * The player: the album art, or the video in its place, with the song, its position and the
 * usual buttons. For a long video with a tracklist, the song playing inside it is named and
 * the tracklist is shown below, each song a tap away; Next and Previous move between them.
 */
@Composable
fun PlayerScreen(
    connection: PlayerConnection,
    library: Library,
    showVideo: Boolean,
    videoAllowed: Boolean,
    onShowVideo: (Boolean) -> Unit,
    onClose: () -> Unit,
    onFullscreen: () -> Unit,
    onQueue: () -> Unit,
    onMore: (Track) -> Unit,
    onEditTracklist: (MediaFile) -> Unit,
) {
    val glass = LocalGlass.current
    val state = connection.state
    val track = state.mediaId?.let { library.track(it) }
    val position = rememberPosition(connection, active = true)
    val now = track?.let { NowPlaying(it, position, state.durationMs) }
    val art = track?.file?.art
    val colors = rememberArtColors(art)
    val background = rememberBackdrop()

    // Touches stop here, rather than reaching the library underneath.
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        // The scene: the art's colours glowing, and the art itself blurred over them.
        Box(Modifier.fillMaxSize().backdropSource(background)) {
            GlowBackground(
                spots = {
                    colors.take(3).mapIndexed { i, c ->
                        when (i) {
                            0 -> GlowSpot(0.2f, 0.18f, c, 0.95f, 1.1f)
                            1 -> GlowSpot(0.9f, 0.5f, c, 0.85f)
                            else -> GlowSpot(0.3f, 0.92f, c, 0.8f)
                        }
                    }
                },
                glass = glass,
                modifier = Modifier.fillMaxSize(),
            )
            BlurredArt(art)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(onClick = onClose, label = "Close the player") { Icon(NovaIcon.CHEVRON_DOWN, glass.textPrimary, Modifier.size(26.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val from = if (now?.chapterTrack != null) "PLAYING FROM A VIDEO" else if (track?.album != null) "PLAYING FROM" else "NOW PLAYING"
                    Text(from, style = GlassType.section, color = glass.textTertiary)
                    val source = if (now?.chapterTrack != null) now.track.file.title else track?.album
                    if (source != null) Text(source, style = GlassType.callout, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassIconButton(onClick = onQueue, label = "Up next") { Icon(NovaIcon.QUEUE, glass.textPrimary, Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(22.dp))

            // The picture, or the video in its place.
            val video = state.hasVideo && showVideo && videoAllowed
            Box(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .aspectRatio(if (video) videoAspect(state) else 1f)
                    .clip(RoundedCornerShape(28.dp)),
            ) {
                Crossfade(targetState = video, label = "media") { showingVideo ->
                    if (showingVideo) {
                        Box(Modifier.fillMaxSize()) {
                            VideoSurface(connection.controller, Modifier.fillMaxSize())
                            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                                GlassIconButton(onClick = onFullscreen, label = "Full screen") { Icon(NovaIcon.FULLSCREEN, Color.White, Modifier.size(24.dp)) }
                            }
                        }
                    } else {
                        Artwork(art, Modifier.fillMaxSize(), size = 520.dp, shape = RoundedCornerShape(28.dp), video = track?.file?.isVideo == true, sizePx = 900)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // Title and artist; inside a long video, the song playing.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(now?.title ?: "Nothing playing", style = GlassType.largeTitle.copy(fontSize = GlassType.title.fontSize * 1.2f), color = glass.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(now?.artist ?: "", style = GlassType.headline, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (now?.chapterTrack != null) {
                        Text("Song ${now.chapterIndex + 1} of ${now.chapters.size}", style = GlassType.caption, color = glass.textTertiary)
                    }
                }
                if (track != null) {
                    GlassIconButton(onClick = { onMore(now?.chapterTrack ?: track) }, label = "More") { Icon(NovaIcon.MORE, glass.textPrimary, Modifier.size(22.dp)) }
                }
            }
            Spacer(Modifier.height(14.dp))

            SeekBar(
                positionMs = position,
                durationMs = state.durationMs,
                markers = now?.chapters?.map { it.startMs }.orEmpty(),
                onSeek = { connection.seekTo(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PlainIconButton(NovaIcon.SHUFFLE, "Shuffle", { connection.toggleShuffle() }, if (state.shuffle) glass.accent else glass.textSecondary)
                PlainIconButton(NovaIcon.PREVIOUS, "Previous", { connection.previous() }, glass.textPrimary, size = 60.dp, iconSize = 36.dp)
                PlayButton(state.playWhenReady, { connection.togglePlay() })
                PlainIconButton(NovaIcon.NEXT, "Next", { connection.next() }, glass.textPrimary, size = 60.dp, iconSize = 36.dp)
                PlainIconButton(
                    if (state.repeat == Player.REPEAT_MODE_ONE) NovaIcon.REPEAT_ONE else NovaIcon.REPEAT,
                    when (state.repeat) {
                        Player.REPEAT_MODE_OFF -> "Repeat: off"
                        Player.REPEAT_MODE_ALL -> "Repeat: all"
                        else -> "Repeat: this song"
                    },
                    { connection.cycleRepeat() },
                    if (state.repeat != Player.REPEAT_MODE_OFF) glass.accent else glass.textSecondary,
                )
            }

            // For videos: picture or video, and full screen.
            if (track?.file?.isVideo == true && videoAllowed) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.philpf.nova.ui.glass.GlassChip(
                        text = "Video",
                        selected = showVideo,
                        onClick = { onShowVideo(true) },
                        leading = { Icon(NovaIcon.VIDEO, glass.textPrimary, Modifier.size(16.dp)) },
                    )
                    com.philpf.nova.ui.glass.GlassChip(
                        text = "Art",
                        selected = !showVideo,
                        onClick = { onShowVideo(false) },
                        leading = { Icon(NovaIcon.IMAGE, glass.textPrimary, Modifier.size(16.dp)) },
                    )
                    com.philpf.nova.ui.glass.GlassChip(
                        text = "Full screen",
                        selected = false,
                        onClick = onFullscreen,
                        leading = { Icon(NovaIcon.FULLSCREEN, glass.textPrimary, Modifier.size(16.dp)) },
                    )
                }
            }

            // The songs of a long video.
            if (now != null && now.chapters.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                GlassSurface(background, Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.padding(vertical = 14.dp, horizontal = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Tracklist · ${songCount(now.chapters.size)}", Modifier.weight(1f).padding(top = 4.dp))
                            PlainIconButton(NovaIcon.EDIT, "Edit the tracklist", { onEditTracklist(now.track.file) }, glass.textSecondary, size = 36.dp, iconSize = 18.dp)
                        }
                        Tracklist(now.track.file, now.chapterIndex) { connection.seekTo(now.chapters[it].startMs) }
                    }
                }
            } else if (track != null && track.file.isVideo && !track.isClip) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Add tracklist",
                    style = GlassType.callout,
                    color = glass.accent,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onEditTracklist(track.file) }.padding(8.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

private fun videoAspect(state: PlayerState): Float {
    val v = state.videoSize
    if (v.width <= 0 || v.height <= 0) return 16f / 9f
    return (v.width * v.pixelWidthHeightRatio / v.height).coerceIn(0.75f, 2.4f)
}

/** A long video's songs, the one playing marked; tapping one plays from its start. */
@Composable
fun Tracklist(file: MediaFile, current: Int, onPick: (Int) -> Unit) {
    val glass = LocalGlass.current
    file.chapters.indices.forEach { i ->
        val t = file.chapterTrack(i)
        val playing = i == current
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (playing) glass.controlFillSelected else Color.Transparent)
                .clickable { onPick(i) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(Chapters.formatTime(t.startMs), style = GlassType.caption, color = if (playing) glass.accent else glass.textTertiary, modifier = Modifier.width(52.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = GlassType.body, color = if (playing) glass.accent else glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.artist != null) Text(t.artist, style = GlassType.caption, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (playing) Icon(NovaIcon.NOTE, glass.accent, Modifier.size(16.dp))
        }
    }
}

/** The album art, large and blurred, faded into the scene. */
@Composable
private fun BlurredArt(path: String?) {
    val image = rememberArt(path, 256)
    val glass = LocalGlass.current
    val radius = with(LocalDensity.current) { 60.dp.toPx() }
    if (image != null) {
        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
                    alpha = if (glass.isDark) 0.55f else 0.45f
                },
        )
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(glass.backgroundTop.copy(alpha = 0.25f), glass.backgroundBottom.copy(alpha = 0.55f), glass.backgroundBottom.copy(alpha = 0.85f)),
            ),
        ),
    )
}
