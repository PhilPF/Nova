package com.philpf.nova.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.philpf.nova.core.QueueEntry
import com.philpf.nova.core.QueueSections
import com.philpf.nova.core.SourceKind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import com.philpf.nova.core.SkipRule
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.philpf.nova.ui.theme.Radius
import com.philpf.nova.ui.theme.Space
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
fun rememberPosition(connection: PlayerConnection, active: Boolean, intervalMs: Long = 250): Long =
    rememberPositionState(connection, active, intervalMs).longValue

/** The same as state, for what reads it later (a gesture waiting for the song to change). */
@Composable
fun rememberPositionState(connection: PlayerConnection, active: Boolean, intervalMs: Long = 250): MutableLongState {
    val position = remember { mutableLongStateOf(connection.position()) }
    LaunchedEffect(active, connection.controller, connection.seeks) {
        while (active) {
            position.longValue = connection.position()
            delay(intervalMs)
        }
    }
    return position
}

/** The height of the title, the position and the buttons under the picture. */
private val BottomBlockHeight = 236.dp

/** The pictures of the songs before and after, decoded ahead so a skip shows them at once. */
private suspend fun preloadNeighbours(state: PlayerState, library: Library) {
    for (i in listOf(state.index + 1, state.index - 1)) {
        val art = state.queue.getOrNull(i)?.let { library.track(it.mediaId)?.file?.art } ?: continue
        ArtCache.load(art, 1000)
    }
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
    val positionState = rememberPositionState(connection, active = true)
    val position = positionState.longValue
    val now = track?.let { NowPlaying(it, position, state.durationMs) }
    val art = track?.file?.art
    val colors = rememberArtColors(art)
    val background = rememberBackdrop()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val pull = rememberPullToClose(onClose)
    // The song shown: changes when the item does, or the song inside a long video.
    val swipe = rememberSkipSwipe(
        onNext = { connection.next() },
        onPrevious = { connection.previous() },
        key = { connection.state.mediaId to Chapters.indexAt(now?.chapters.orEmpty(), positionState.longValue) },
        canNext = { connection.canNext() },
    )
    LaunchedEffect(state.index, state.queue) { preloadNeighbours(state, library) }

    // The screen stays on while the player is open.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Touches stop here, rather than reaching the library underneath.
    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, pull.pull.roundToInt()) }
            .clip(RoundedCornerShape(if (pull.pull > 0f) Radius.panel else 0.dp))
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
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val peek = 76.dp
            val pageHeight = maxHeight - peek - bottomInset
            // The picture: a square as wide as the page (less a small margin), unless the page is
            // too short for it and the controls. A video takes its own shape in that width, so
            // the page is shorter and the panel below rises; the change is animated.
            val artSize = minOf(maxWidth - 32.dp, pageHeight - top - 64.dp - BottomBlockHeight - 24.dp - Space.section, 560.dp).coerceAtLeast(160.dp)

            Column(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(pull)
                    .verticalScroll(scroll)
                    .padding(horizontal = Space.margin),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SplitColumn(
                    minHeight = 0.dp,
                    top = {
                        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassIconButton(onClick = onClose, label = "Close the player") { Icon(NovaIcon.CHEVRON_DOWN, glass.textPrimary, Modifier.size(26.dp)) }
                            // Between the two buttons, with the same room on each side.
                            Column(Modifier.weight(1f).padding(horizontal = Space.gap), horizontalAlignment = Alignment.CenterHorizontally) {
                                val from = if (now?.chapterTrack != null) "PLAYING FROM A VIDEO" else if (track?.album != null) "PLAYING FROM" else "NOW PLAYING"
                                Text(from, style = GlassType.section, color = glass.textTertiary)
                                val source = if (now?.chapterTrack != null) now.track.file.title else track?.album
                                MarqueeText(source ?: "", GlassType.callout, glass.textSecondary)
                            }
                            GlassIconButton(onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } }, label = "Up next") {
                                Icon(NovaIcon.QUEUE, glass.textPrimary, Modifier.size(22.dp))
                            }
                        }
                    },
                    middle = {
                        // The picture, or the video in its place at its own shape; swipe it sideways
                        // to skip a song, double tap its left or right for 10 s back or forward.
                        val isVideoFile = track?.file?.isVideo == true
                        val video = state.hasVideo && showVideo && videoAllowed
                        val aspect = if (video) videoAspect(state) else 1f
                        val mediaWidth by animateDpAsState(if (aspect >= 1f) artSize else artSize * aspect, tween(320, easing = FastOutSlowInEasing), label = "mediaWidth")
                        val mediaHeight by animateDpAsState(if (aspect >= 1f) artSize / aspect else artSize, tween(320, easing = FastOutSlowInEasing), label = "mediaHeight")
                        val mediaWaves = rememberWaves()
                        var skipHint by remember { mutableStateOf<SkipHint?>(null) }
                        Box(
                            Modifier
                                .size(mediaWidth, mediaHeight)
                                .skipOffset(swipe, move = !video)
                                .clip(RoundedCornerShape(Radius.panel)),
                        ) {
                            Crossfade(targetState = video, label = "media") { showingVideo ->
                                if (showingVideo) {
                                    VideoSurface(connection.controller, Modifier.fillMaxSize())
                                } else {
                                    Artwork(art, Modifier.fillMaxSize(), size = artSize, shape = RoundedCornerShape(Radius.panel), video = isVideoFile, sizePx = 1000)
                                }
                            }
                            // Over the video too, which would otherwise take the touches.
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .waves(mediaWaves)
                                    .skipGesture(swipe)
                                    .doubleTapToSkip { forward, at ->
                                        connection.skipBy(if (forward) SkipRule.STEP_MS else -SkipRule.STEP_MS)
                                        mediaWaves.start(glass.accent, at)
                                        skipHint = SkipHint(forward, System.nanoTime())
                                    },
                            )
                            SkipHintView(skipHint, Modifier.matchParentSize())
                            // A video's own buttons, in its corner: art or video, picture in picture, full screen.
                            if (isVideoFile && videoAllowed) {
                                val pip = LocalPip.current
                                Row(Modifier.align(Alignment.BottomEnd).padding(Space.gap), horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                                    GlassIconButton(onClick = { onShowVideo(!showVideo) }, label = if (showVideo) "Show the art" else "Show the video", size = 38.dp) {
                                        Icon(if (showVideo) NovaIcon.IMAGE else NovaIcon.VIDEO, Color.White, Modifier.size(18.dp))
                                    }
                                    if (pip.available && video) {
                                        GlassIconButton(onClick = { pip.enter() }, label = "Picture in picture", size = 38.dp) {
                                            Icon(NovaIcon.PIP, Color.White, Modifier.size(19.dp))
                                        }
                                    }
                                    GlassIconButton(onClick = onFullscreen, label = "Full screen", size = 38.dp) {
                                        Icon(NovaIcon.FULLSCREEN, Color.White, Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    },
                    bottom = {
                        Column(Modifier.height(BottomBlockHeight).padding(horizontal = Space.inset), verticalArrangement = Arrangement.Bottom) {
                            // Title and artist (inside a long video, the song playing), always on the
                            // same lines: one for each, and one kept for "Song 2 of 6".
                            Row(Modifier.fillMaxWidth().skipOffset(swipe).skipGesture(swipe), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    MarqueeText(now?.title ?: "Nothing playing", GlassType.display, glass.textPrimary)
                                    MarqueeText(now?.artist ?: "", GlassType.headline, glass.textSecondary)
                                    Text(
                                        if (now?.chapterTrack != null) "Song ${now.chapterIndex + 1} of ${now.chapters.size}" else "",
                                        style = GlassType.caption,
                                        color = glass.textTertiary,
                                        maxLines = 1,
                                    )
                                }
                                if (track != null) {
                                    GlassIconButton(onClick = { onMore(now?.chapterTrack ?: track) }, label = "More") { Icon(NovaIcon.MORE, glass.textPrimary, Modifier.size(22.dp)) }
                                }
                            }
                            Spacer(Modifier.height(Space.gap))

                            SeekBar(
                                positionMs = position,
                                durationMs = state.durationMs,
                                markers = now?.chapters?.map { it.startMs }.orEmpty(),
                                onSeek = { connection.seekTo(it) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(Space.small))

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
                            Spacer(Modifier.height(Space.gap))
                        }
                    },
                )

                // Up next (and a long video's songs), peeking at the bottom: swipe up for all of it.
                Spacer(Modifier.height(Space.section))
                UpNextPanel(
                    backdrop = background,
                    connection = connection,
                    library = library,
                    now = now,
                    onEditTracklist = onEditTracklist,
                )
                Spacer(Modifier.height(Space.section))
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
 * What plays next, in the order it will play, as one list: first, when a long video plays
 * whole, its songs still to come; then the queue, its songs grouped by what they were added
 * as (an album, a playlist, a video's tracklist…) under a header that also takes the whole
 * group out. A song on its own names where it came from. Songs move by their handle (not
 * while shuffling: the order shown is then not the queue's) and leave by their ×. The panel is
 * as tall as what it lists.
 */
@Composable
private fun UpNextPanel(
    backdrop: Backdrop,
    connection: PlayerConnection,
    library: Library,
    now: NowPlaying?,
    onEditTracklist: (MediaFile) -> Unit,
) {
    val glass = LocalGlass.current
    val state = connection.state
    // A wave of the new song's colour each time a song starts.
    val waves = rememberWaves()
    val touch = LocalTouch.current
    val song = state.mediaId to (now?.chapterIndex ?: -1)
    var seenSong by remember { mutableStateOf(song) }
    LaunchedEffect(song) {
        if (song == seenSong) return@LaunchedEffect
        seenSong = song
        // From where the screen was touched to change it (a swipe, Next), else from the top.
        waves.startAtTouch(waveColor(now?.track?.file?.art, glass.accent), touch, 0.5f, 0f)
    }

    val entries = remember(state.queue) {
        state.queue.mapIndexed { i, q -> QueueEntry(i, q.mediaId, q.title, q.artist, q.source) }
    }
    val blocks = remember(entries, state.upcoming) { QueueSections.upcoming(entries, state.upcoming.take(QUEUE_SHOWN)) }
    val restOfVideo = now?.takeIf { it.chapters.isNotEmpty() }?.let { n -> (n.chapterIndex + 1 until n.chapters.size).toList() }.orEmpty()
    val count = restOfVideo.size + state.upcoming.size

    // Moving a song: where each row is, which one moves and how far.
    val rowCenters = remember { mutableStateMapOf<Int, Float>() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val canMove = !state.shuffle
    fun dropTarget(): Int {
        val from = rowCenters[dragging] ?: return dragging
        val y = from + dragOffset
        return state.upcoming.filter { it in rowCenters }.minByOrNull { kotlin.math.abs((rowCenters[it] ?: 0f) - y) } ?: dragging
    }
    val target = if (dragging >= 0) dropTarget() else -1

    GlassSurface(backdrop, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.panel)) {
        Box(Modifier.matchParentSize().waves(waves))
        Column(Modifier.padding(Space.inset)) {
            SectionLabel(if (count > 0) "Up next · ${songCount(count)}" else "Up next", Modifier.padding(top = Space.gap))
            if (count == 0) {
                Text("Nothing after this song.", style = GlassType.callout, color = glass.textSecondary, modifier = Modifier.padding(horizontal = Space.inset, vertical = Space.small))
            }
            if (now != null && restOfVideo.isNotEmpty()) {
                GroupHeader(SourceKind.TRACKLIST.label, now.track.file.title) {
                    PlainIconButton(NovaIcon.EDIT, "Edit the tracklist", { onEditTracklist(now.track.file) }, glass.textTertiary, size = 36.dp, iconSize = 16.dp)
                }
                restOfVideo.forEach { i ->
                    val t = now.track.file.chapterTrack(i)
                    UpNextRow(
                        title = t.title,
                        subtitle = t.artist,
                        art = null,
                        leading = Chapters.formatTime(t.startMs),
                        onClick = { connection.seekTo(t.startMs) },
                    )
                }
            }
            for (block in blocks) {
                val source = block.source
                if (source != null) {
                    GroupHeader(source.kind.label, source.title) {
                        PlainIconButton(NovaIcon.CLOSE, "Remove “${source.title}” from the queue", { connection.removeAll(block.entries.map { it.index }) }, glass.textTertiary, size = 36.dp, iconSize = 16.dp)
                    }
                }
                for (e in block.entries) {
                    val file = library.track(e.mediaId)?.file
                    // A song on its own names where it came from.
                    val from = e.source?.takeIf { source == null }?.let { "${it.kind.label}: ${it.title}" }
                    key(e.index, e.mediaId) {
                        UpNextRow(
                            title = e.title,
                            subtitle = listOfNotNull(e.artist, from).joinToString(" · ").ifEmpty { null },
                            art = file?.art,
                            video = file?.isVideo == true,
                            onClick = { connection.skipTo(e.index) },
                            onRemove = { connection.removeAt(e.index) },
                            dragHandle = if (canMove) {
                                Modifier.pointerInput(e.index) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragging = e.index
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            val to = dropTarget()
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
                            } else {
                                null
                            },
                            moving = e.index == dragging,
                            offset = if (e.index == dragging) dragOffset else 0f,
                            // Where a moved song will land: a line above or below the row it takes the place of.
                            dropLine = when {
                                dragging < 0 || e.index != target || target == dragging -> 0
                                target < dragging -> -1
                                else -> 1
                            },
                            modifier = Modifier.onGloballyPositioned { rowCenters[e.index] = it.positionInRoot().y + it.size.height / 2f },
                        )
                    }
                }
            }
            if (state.upcoming.size > QUEUE_SHOWN) {
                Text("and ${state.upcoming.size - QUEUE_SHOWN} more", style = GlassType.caption, color = glass.textTertiary, modifier = Modifier.padding(horizontal = Space.inset, vertical = Space.small))
            }
        }
    }
}

private const val QUEUE_SHOWN = 150

private fun videoAspect(state: PlayerState): Float {
    val v = state.videoSize
    if (v.width <= 0 || v.height <= 0) return 16f / 9f
    return (v.width * v.pixelWidthHeightRatio / v.height).coerceIn(0.75f, 2.4f)
}

/** A group's header in the next panel: what it is, its name, and a button. */
@Composable
private fun GroupHeader(kind: String, title: String, action: @Composable () -> Unit) {
    val glass = LocalGlass.current
    Row(Modifier.fillMaxWidth().padding(start = Space.inset, top = Space.gap), verticalAlignment = Alignment.CenterVertically) {
        Text(kind.uppercase(), style = GlassType.section, color = glass.accent)
        Text("  ·  ", style = GlassType.section, color = glass.textTertiary)
        Text(title, style = GlassType.callout, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        action()
    }
}

/** A song in the next panel. */
@Composable
private fun UpNextRow(
    title: String,
    subtitle: String?,
    art: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    video: Boolean = false,
    leading: String? = null,
    onRemove: (() -> Unit)? = null,
    dragHandle: Modifier? = null,
    moving: Boolean = false,
    offset: Float = 0f,
    dropLine: Int = 0,
) {
    val glass = LocalGlass.current
    val lineColor = glass.accent
    Row(
        modifier
            .fillMaxWidth()
            .zIndex(if (moving) 1f else 0f)
            .graphicsLayer { translationY = offset }
            .drawBehind {
                if (dropLine != 0) {
                    val y = if (dropLine < 0) 0f else size.height
                    drawLine(lineColor, Offset(Space.inset.toPx(), y), Offset(size.width - Space.inset.toPx(), y), strokeWidth = 2.dp.toPx())
                }
            }
            .clip(RoundedCornerShape(Radius.panel - Space.inset))
            .background(if (moving) glass.controlFillSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.inset, vertical = Space.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.gap),
    ) {
        if (leading != null) {
            Text(leading, style = GlassType.caption, color = glass.textTertiary, modifier = Modifier.width(44.dp))
        } else {
            Artwork(art, size = 42.dp, video = video)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.body, color = glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = GlassType.caption, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) PlainIconButton(NovaIcon.CLOSE, "Remove from the queue", onRemove, glass.textTertiary, size = 36.dp, iconSize = 16.dp)
        if (dragHandle != null) {
            Box(dragHandle.size(36.dp).semantics { contentDescription = "Drag to move" }, contentAlignment = Alignment.Center) {
                Icon(NovaIcon.DRAG, glass.textSecondary, Modifier.size(22.dp))
            }
        }
    }
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
                .clip(RoundedCornerShape(Radius.row))
                .playingMark(playing, glass.accent)
                .clickable { onPick(i) }
                .padding(horizontal = Space.inset, vertical = Space.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.gap),
        ) {
            Text(Chapters.formatTime(t.startMs), style = GlassType.caption, color = if (playing) glass.accent else glass.textTertiary, modifier = Modifier.width(52.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = GlassType.body, color = if (playing) glass.accent else glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.artist != null) Text(t.artist, style = GlassType.caption, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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

/** A double tap's skip, shown briefly over the picture. */
class SkipHint(val forward: Boolean, val at: Long)

/**
 * Double taps on the left half (back) or the right half (forward), with where the tap was.
 * Single taps and drags are left to the rest.
 */
fun Modifier.doubleTapToSkip(onSkip: (forward: Boolean, at: Offset) -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(onDoubleTap = { at -> onSkip(at.x > size.width / 2f, at) })
}

/** "‹‹ 10 s" on the left or "10 s ››" on the right, fading out. */
@Composable
fun SkipHintView(hint: SkipHint?, modifier: Modifier = Modifier, color: Color = Color.White) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(hint?.at) {
        if (hint == null) return@LaunchedEffect
        alpha.snapTo(1f)
        delay(350)
        alpha.animateTo(0f, tween(450))
    }
    if (hint == null) return
    Box(modifier.padding(horizontal = Space.section), contentAlignment = if (hint.forward) Alignment.CenterEnd else Alignment.CenterStart) {
        Row(
            Modifier
                .graphicsLayer { this.alpha = alpha.value }
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = Space.padding, vertical = Space.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.small),
        ) {
            if (!hint.forward) Icon(NovaIcon.REWIND, color, Modifier.size(20.dp))
            Text("${SkipRule.STEP_MS / 1000} s", style = GlassType.headline, color = color)
            if (hint.forward) Icon(NovaIcon.FORWARD, color, Modifier.size(20.dp))
        }
    }
}
