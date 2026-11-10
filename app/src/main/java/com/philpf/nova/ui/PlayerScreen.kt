package com.philpf.nova.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.philpf.nova.ui.glass.Backdrop
import com.philpf.nova.ui.glass.GlassChip
import com.philpf.nova.ui.glass.SegmentedControl
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
 * usual buttons. Swiping the art (or the video) sideways goes to the next or previous song;
 * pulling the page down closes it. Below, peeking at the bottom, is what plays next (drag a
 * song by its handle to move it) and, for a long video with a tracklist, its songs, each a
 * tap away; Next and Previous move between them.
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
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val pull = rememberPullToClose(onClose)
    val swipe = rememberSkipSwipe(onNext = { connection.next() }, onPrevious = { connection.previous() })

    // Touches stop here, rather than reaching the library underneath.
    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, pull.pull.roundToInt()) }
            .clip(RoundedCornerShape(if (pull.pull > 0f) 28.dp else 0.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
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

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val insets = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val peek = 84.dp
            val pageHeight = maxHeight - peek - WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // The picture is as large as the page leaves room for, at most 520 dp.
            val artMax = (maxHeight - insets - 480.dp).coerceIn(180.dp, 520.dp)
            val panelMin = maxHeight - insets - 120.dp

            Column(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(pull)
                    .verticalScroll(scroll)
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SplitColumn(
                    minHeight = pageHeight,
                    top = {
                        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton(onClick = onClose, label = "Close the player") { Icon(NovaIcon.CHEVRON_DOWN, glass.textPrimary, Modifier.size(26.dp)) }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                val from = if (now?.chapterTrack != null) "PLAYING FROM A VIDEO" else if (track?.album != null) "PLAYING FROM" else "NOW PLAYING"
                                Text(from, style = GlassType.section, color = glass.textTertiary)
                                val source = if (now?.chapterTrack != null) now.track.file.title else track?.album
                                if (source != null) Text(source, style = GlassType.callout, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            GlassIconButton(onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } }, label = "Up next") {
                                Icon(NovaIcon.QUEUE, glass.textPrimary, Modifier.size(22.dp))
                            }
                        }
                    },
                    middle = {
                        // The picture, or the video in its place; swipe it sideways to skip.
                        val video = state.hasVideo && showVideo && videoAllowed
                        val aspect = if (video) videoAspect(state) else 1f
                        Box(
                            Modifier
                                .widthIn(max = if (video) 520.dp else artMax)
                                .fillMaxWidth()
                                .aspectRatio(aspect)
                                .skipOffset(swipe, move = !video)
                                .clip(RoundedCornerShape(28.dp)),
                        ) {
                            Crossfade(targetState = video, label = "media") { showingVideo ->
                                if (showingVideo) {
                                    VideoSurface(connection.controller, Modifier.fillMaxSize())
                                } else {
                                    Artwork(art, Modifier.fillMaxSize(), size = 520.dp, shape = RoundedCornerShape(28.dp), video = track?.file?.isVideo == true, sizePx = 900)
                                }
                            }
                            // Over the video too, which would otherwise take the touches.
                            Box(Modifier.matchParentSize().skipGesture(swipe))
                            if (video) {
                                Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                                    GlassIconButton(onClick = onFullscreen, label = "Full screen") { Icon(NovaIcon.FULLSCREEN, Color.White, Modifier.size(24.dp)) }
                                }
                            }
                        }
                    },
                    bottom = {
                        Spacer(Modifier.height(20.dp))
                        // Title and artist; inside a long video, the song playing.
                        Row(Modifier.fillMaxWidth().skipOffset(swipe).skipGesture(swipe), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(now?.title ?: "Nothing playing", style = GlassType.display, color = glass.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(now?.artist ?: "", style = GlassType.headline, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (now?.chapterTrack != null) {
                                    Text("Song ${now.chapterIndex + 1} of ${now.chapters.size}", style = GlassType.caption, color = glass.textTertiary)
                                }
                            }
                            if (track != null) {
                                GlassIconButton(onClick = { onMore(now?.chapterTrack ?: track) }, label = "More") { Icon(NovaIcon.MORE, glass.textPrimary, Modifier.size(22.dp)) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        SeekBar(
                            positionMs = position,
                            durationMs = state.durationMs,
                            markers = now?.chapters?.map { it.startMs }.orEmpty(),
                            onSeek = { connection.seekTo(it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))

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
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassChip(text = "Video", selected = showVideo, onClick = { onShowVideo(true) }, leading = { Icon(NovaIcon.VIDEO, glass.textPrimary, Modifier.size(16.dp)) })
                                GlassChip(text = "Art", selected = !showVideo, onClick = { onShowVideo(false) }, leading = { Icon(NovaIcon.IMAGE, glass.textPrimary, Modifier.size(16.dp)) })
                                GlassChip(text = "Full screen", selected = false, onClick = onFullscreen, leading = { Icon(NovaIcon.FULLSCREEN, glass.textPrimary, Modifier.size(16.dp)) })
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    },
                )

                // Up next (and a long video's songs), peeking at the bottom: swipe up for all of it.
                UpNextPanel(
                    backdrop = background,
                    connection = connection,
                    library = library,
                    now = now,
                    onEditTracklist = onEditTracklist,
                    minHeight = panelMin,
                )
                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * [top] at the top, [bottom] at the bottom of at least [minHeight], and [middle] centred in
 * the space between: the player's controls sit at the foot of the screen, with the next panel
 * just below the fold, and the picture in the middle of what is left.
 */
@Composable
private fun SplitColumn(
    minHeight: Dp,
    top: @Composable ColumnScope.() -> Unit,
    middle: @Composable ColumnScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    Layout(
        contents = listOf<@Composable () -> Unit>(
            { Column(horizontalAlignment = Alignment.CenterHorizontally, content = top) },
            { Column(horizontalAlignment = Alignment.CenterHorizontally, content = middle) },
            { Column(horizontalAlignment = Alignment.CenterHorizontally, content = bottom) },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) { (topPart, middlePart, bottomPart), constraints ->
        val each = constraints.copy(minWidth = constraints.maxWidth, minHeight = 0, maxHeight = Constraints.Infinity)
        val t = topPart.first().measure(each)
        val m = middlePart.first().measure(each)
        val b = bottomPart.first().measure(each)
        val gap = 16.dp.roundToPx()
        val height = maxOf(minHeight.roundToPx(), t.height + m.height + b.height + 2 * gap)
        layout(constraints.maxWidth, height) {
            t.place(0, 0)
            m.place(0, t.height + (height - t.height - b.height - m.height) / 2)
            b.place(0, height - b.height)
        }
    }
}

/**
 * What plays next, and a long video's tracklist, as two tabs when there is a tracklist (it
 * opens on the tracklist when nothing follows the video).
 */
@Composable
private fun UpNextPanel(
    backdrop: Backdrop,
    connection: PlayerConnection,
    library: Library,
    now: NowPlaying?,
    onEditTracklist: (MediaFile) -> Unit,
    minHeight: Dp,
) {
    val glass = LocalGlass.current
    val state = connection.state
    val chapters = now?.chapters.orEmpty()
    val following = (state.queue.size - state.index - 1).coerceAtLeast(0)
    var tab by remember(state.mediaId) { mutableIntStateOf(if (chapters.isNotEmpty() && following == 0) 1 else 0) }
    if (chapters.isEmpty() && tab != 0) tab = 0

    GlassSurface(backdrop, Modifier.fillMaxWidth().heightIn(min = minHeight), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 10.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(glass.textTertiary.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.height(10.dp))
            if (chapters.isNotEmpty()) {
                SegmentedControl(listOf("Up next", "Tracklist · ${chapters.size}"), tab, { tab = it })
                Spacer(Modifier.height(8.dp))
            } else {
                SectionLabel(if (following > 0) "Up next · ${songCount(following)}" else "Up next", Modifier.padding(top = 4.dp, start = 4.dp))
            }
            if (tab == 1 && now != null) {
                Tracklist(now.track.file, now.chapterIndex) { connection.seekTo(chapters[it].startMs) }
                ActionRow(NovaIcon.EDIT, "Edit the tracklist", { onEditTracklist(now.track.file) })
            } else {
                QueueList(connection, library)
                if (now != null && chapters.isEmpty() && now.track.file.isVideo && !now.track.isClip) {
                    ActionRow(NovaIcon.LIST, "Add a tracklist to this video", { onEditTracklist(now.track.file) })
                }
            }
        }
    }
}

private const val QUEUE_SHOWN = 150

/**
 * The queue from the song playing on. Drag a song by its handle to move it; the song playing
 * stays where it is.
 */
@Composable
private fun QueueList(connection: PlayerConnection, library: Library) {
    val glass = LocalGlass.current
    val state = connection.state
    val start = state.index.coerceAtLeast(0)
    val last = minOf(state.queue.size - 1, start + QUEUE_SHOWN)
    if (state.queue.isEmpty()) {
        Text("Nothing in the queue.", style = GlassType.body, color = glass.textSecondary, modifier = Modifier.padding(10.dp))
        return
    }
    val rowHeight = 60.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val target = if (dragging < 0) -1 else (dragging + (dragOffset / rowPx).roundToInt()).coerceIn(start + 1, last)

    for (i in start..last) {
        val item = state.queue[i]
        val current = i == start
        val shift = when {
            dragging < 0 || i == dragging -> 0f
            i in (dragging + 1)..target -> -rowPx
            i in target until dragging -> rowPx
            else -> 0f
        }
        key(item.mediaId, i) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (i == dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (i == dragging) dragOffset else shift }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (current) glass.controlFillSelected else if (i == dragging) glass.controlFill else Color.Transparent)
                    .clickable { connection.skipTo(i) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val file = library.track(item.mediaId)?.file
                Artwork(file?.art, size = 42.dp, video = file?.isVideo == true)
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = GlassType.body, color = if (current) glass.accent else glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.artist != null) Text(item.artist, style = GlassType.caption, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (current) {
                    Icon(NovaIcon.NOTE, glass.accent, Modifier.size(18.dp))
                } else {
                    PlainIconButton(NovaIcon.CLOSE, "Remove from the queue", { connection.removeAt(i) }, glass.textTertiary, size = 36.dp, iconSize = 16.dp)
                    Box(
                        Modifier
                            .size(40.dp)
                            .pointerInput(i, start, last) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = i
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        val to = (dragging + (dragOffset / rowPx).roundToInt()).coerceIn(start + 1, last)
                                        if (dragging >= 0 && to != dragging) connection.moveItem(dragging, to)
                                        dragging = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        dragging = -1
                                        dragOffset = 0f
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                }
                            }
                            .semantics { contentDescription = "Drag to move" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(NovaIcon.DRAG, glass.textSecondary, Modifier.size(22.dp)) }
                }
            }
        }
    }
    if (state.queue.size - 1 > last) {
        Text("and ${state.queue.size - 1 - last} more", style = GlassType.caption, color = glass.textTertiary, modifier = Modifier.padding(10.dp))
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
