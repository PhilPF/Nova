package com.philpf.nova.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.philpf.nova.core.ArtColors
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.philpf.nova.NovaApp
import com.philpf.nova.core.Chapters
import com.philpf.nova.core.Library
import com.philpf.nova.core.Track
import com.philpf.nova.device.DocumentTree
import com.philpf.nova.ui.art.NovaIcon
import com.philpf.nova.ui.glass.Backdrop
import com.philpf.nova.ui.glass.GlassSurface
import com.philpf.nova.ui.glass.backdropSource
import com.philpf.nova.ui.glass.rememberBackdrop
import com.philpf.nova.ui.theme.DarkGlass
import com.philpf.nova.ui.theme.GlassType
import com.philpf.nova.ui.theme.LocalGlass
import com.philpf.nova.ui.theme.Radius
import com.philpf.nova.ui.theme.Space
import kotlinx.coroutines.delay

/** The sheet open over the screens. */
sealed interface Sheet {
    data class AlbumOf(val key: String) : Sheet
    data class ArtistOf(val name: String) : Sheet
    data class PlaylistOf(val id: String) : Sheet
    data class Mix(val fileId: String) : Sheet
    /** A song's menu; [playlistId] and [index] when it was opened from a playlist. */
    data class Song(val track: Track, val playlistId: String? = null, val index: Int = -1) : Sheet
    data class AddTo(val tracks: List<Track>, val title: String) : Sheet
    data class Tracklist(val fileId: String) : Sheet
    /** A file's title, artist and album, set in the app. */
    data class Details(val fileId: String) : Sheet
    data object NewPlaylist : Sheet
    data object Settings : Sheet
}

enum class Fullscreen { OFF, BUTTON, ROTATION }

/** What the debug build opens with, for screenshots: --es open player|fullscreen|mix|..., --es play mix|album, --es tab ... */
data class DemoScript(val open: String?, val play: String?, val tab: String?)

private val MiniPlayerHeight = 64.dp

/**
 * The app's screens, in the colours of the song playing: the accent (the position, the song
 * playing in lists, the main buttons and switches) is its art's colour, the colour of its
 * waves, made readable on the theme; without a song, or for grey art, the theme's own. Every
 * touch on the screen is noted, for waves to start from it.
 */
@Composable
fun NovaScreen(app: NovaApp, connection: PlayerConnection, openPlayerTick: Int, demo: DemoScript?) {
    val base = LocalGlass.current
    val library by app.library.library.collectAsState()
    val art = connection.state.mediaId?.let { library.track(it) }?.file?.art
    // The song's colour; until its art is read (or with none playing), the last one, so the
    // app opens in the colour it was left in.
    val saved = remember { app.settings.lastSongColor.takeIf { it != 0 } }
    val artColor = rememberArtColors(art).firstOrNull()?.toArgb()
    LaunchedEffect(artColor) { if (artColor != null) app.settings.lastSongColor = artColor }
    val songColor = artColor ?: saved.takeIf { connection.state.mediaId == null || art != null }
    val accent = remember(songColor, base.isDark) { songColor?.let { Color(ArtColors.accent(it, base.isDark)) } ?: base.accent }
    // The full-screen video is always dark: its accent is the dark theme's.
    val darkAccent = remember(songColor) { songColor?.let { Color(ArtColors.accent(it, true)) } ?: DarkGlass.accent }

    // Opening the app with nothing playing: the last song, where it was left, paused.
    val loaded by app.library.loaded.collectAsState()
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(loaded, connection.controller) {
        if (!restored && loaded && connection.controller != null && library.files.isNotEmpty() && demo == null) {
            restored = true
            connection.restore(library)
        }
    }

    val touch = LocalTouch.current
    val scrollTitles by app.settings.scrollTitles.collectAsState()
    CompositionLocalProvider(
        LocalGlass provides base.copy(accent = accent),
        LocalDarkAccent provides darkAccent,
        LocalScrollTitles provides scrollTitles,
    ) {
        Box(Modifier.fillMaxSize().recordTouches(touch)) {
            NovaContent(app, connection, openPlayerTick, demo)
        }
    }
}

/** The accent on the always-dark full-screen video. */
val LocalDarkAccent = androidx.compose.runtime.staticCompositionLocalOf { DarkGlass.accent }

@Composable
private fun NovaContent(app: NovaApp, connection: PlayerConnection, openPlayerTick: Int, demo: DemoScript?) {
    val glass = LocalGlass.current
    val library by app.library.library.collectAsState()
    val progress by app.library.progress.collectAsState()
    val playlists by app.playlists.playlists.collectAsState()
    val folder by app.settings.folder.collectAsState()
    val showVideo by app.settings.showVideo.collectAsState()
    val state = connection.state
    // Next and Previous move between the songs of a long video, by the library's tracklists.
    connection.chaptersOf = remember(library) {
        { item -> library.track(item.mediaId)?.takeIf { !it.isClip }?.file?.chapters.orEmpty() }
    }

    var tab by rememberSaveable { mutableStateOf(LibraryTab.SONGS) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(Fullscreen.OFF) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var shownSheet by remember { mutableStateOf<Sheet?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var widgetPreview by remember { mutableStateOf(false) }

    fun open(s: Sheet) {
        sheet = s
        shownSheet = s
    }

    fun play(tracks: List<Track>, index: Int, shuffle: Boolean = false, startMs: Long = 0) {
        connection.play(tracks, index, startMs, shuffle)
        sheet = null
    }

    LaunchedEffect(openPlayerTick) { if (openPlayerTick > 0 && state.mediaId != null) playerOpen = true }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2200)
            notice = null
        }
    }

    // Turning the phone while a video plays in the player shows it full screen.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val videoShown = state.hasVideo && showVideo
    LaunchedEffect(landscape) {
        if (landscape && playerOpen && videoShown && fullscreen == Fullscreen.OFF) fullscreen = Fullscreen.ROTATION
        if (!landscape && fullscreen == Fullscreen.ROTATION) fullscreen = Fullscreen.OFF
    }
    LaunchedEffect(state.mediaId, videoShown) { if (!videoShown || state.mediaId == null) fullscreen = Fullscreen.OFF }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) app.library.setFolder(uri)
    }

    // The debug build's screenshots: play something and open a screen once the library is in.
    if (demo != null) {
        LaunchedEffect(library.files.isNotEmpty(), connection.controller != null) {
            if (library.files.isEmpty() || connection.controller == null) return@LaunchedEffect
            demo.tab?.let { t -> LibraryTab.entries.firstOrNull { it.name.equals(t, true) }?.let { tab = it } }
            val mix = library.mixes.firstOrNull { it.isVideo } ?: library.mixes.firstOrNull()
            when (demo.play) {
                "mix" -> mix?.let { connection.play(listOf(it.wholeTrack), 0, startMs = it.chapters.getOrNull(1)?.startMs?.plus(1500) ?: 0) }
                "album" -> library.albums.firstOrNull()?.let { connection.play(it.tracks, 1) }
            }
            delay(1500)
            when (demo.open) {
                "player" -> {
                    app.settings.setShowVideo(true)
                    playerOpen = true
                }
                "art" -> {
                    app.settings.setShowVideo(false)
                    playerOpen = true
                }
                "fullscreen" -> {
                    app.settings.setShowVideo(true)
                    playerOpen = true
                    fullscreen = Fullscreen.BUTTON
                }
                "mix" -> mix?.let { open(Sheet.Mix(it.id)) }
                "album" -> library.albums.firstOrNull()?.let { open(Sheet.AlbumOf(it.key)) }
                "artist" -> library.artists.firstOrNull { a -> a.tracks.any { it.chapterIndex >= 0 } }?.let { open(Sheet.ArtistOf(it.name)) }
                "tracklist" -> mix?.let { open(Sheet.Tracklist(it.id)) }
                "settings" -> open(Sheet.Settings)
                "menu" -> library.songs.firstOrNull()?.let { open(Sheet.Song(it)) }
                "widget" -> widgetPreview = true
                "details" -> library.files.firstOrNull { it.isVideo && !it.isMix }?.let { open(Sheet.Details(it.id)) }
            }
        }
    }

    val actions = LibraryActions(
        playTracks = { list, i -> play(list, i) },
        openTrack = { open(Sheet.Song(it)) },
        openAlbum = { open(Sheet.AlbumOf(it.key)) },
        openArtist = { open(Sheet.ArtistOf(it.name)) },
        openPlaylist = { open(Sheet.PlaylistOf(it.id)) },
        openMix = { open(Sheet.Mix(it.id)) },
        newPlaylist = { open(Sheet.NewPlaylist) },
        chooseFolder = { folderPicker.launch(null) },
        openSettings = { open(Sheet.Settings) },
    )

    BackHandler(enabled = playerOpen && sheet == null && fullscreen == Fullscreen.OFF) { playerOpen = false }

    // Picture in picture: leaving the app while a video shows and plays puts it in a small
    // window over other apps; in that window, only the video is drawn.
    val pip = LocalPip.current
    val videoOnScreen = state.hasVideo && showVideo && (playerOpen || fullscreen != Fullscreen.OFF)
    LaunchedEffect(videoOnScreen, state.isPlaying, state.videoSize) {
        val v = state.videoSize
        pip.update(v.width.takeIf { it > 0 } ?: 16, v.height.takeIf { it > 0 } ?: 9, videoOnScreen && state.isPlaying, state.isPlaying)
    }
    if (pip.active) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(connection.controller, Modifier.fillMaxSize())
        }
        return
    }

    val root = rememberBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(root)) {
            // The library, and the mini player floating over it.
            val libBackground = rememberBackdrop()
            val libContent = rememberBackdrop()
            val current = state.mediaId?.let { library.track(it) }
            val colors = rememberArtColors(current?.file?.art)
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().backdropSource(libContent)) {
                    GlowBackground(
                        spots = {
                            colors.take(2).mapIndexed { i, c -> if (i == 0) GlowSpot(0.85f, 0.12f, c, 0.9f) else GlowSpot(0.1f, 0.75f, c, 0.8f, 0.8f) }
                        },
                        glass = glass,
                        modifier = Modifier.fillMaxSize().backdropSource(libBackground),
                    )
                    LibraryContent(
                        library = library,
                        playlists = playlists,
                        tab = tab,
                        onTab = { tab = it },
                        query = query,
                        onQuery = { query = it },
                        hasFolder = folder != null || app.demoDir != null,
                        progress = progress,
                        playingId = state.mediaId,
                        backdrop = libBackground,
                        actions = actions,
                        bottomSpace = if (state.mediaId != null) MiniPlayerHeight + 28.dp else 24.dp,
                    )
                }
                if (current != null) {
                    MiniPlayer(
                        connection = connection,
                        track = current,
                        backdrop = libContent,
                        onOpen = { playerOpen = true },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            AnimatedVisibility(
                visible = playerOpen && state.mediaId != null,
                enter = slideInVertically(tween(340)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(220)),
            ) {
                PlayerScreen(
                    connection = connection,
                    library = library,
                    showVideo = showVideo,
                    videoAllowed = fullscreen == Fullscreen.OFF,
                    onShowVideo = app.settings::setShowVideo,
                    onClose = { playerOpen = false },
                    onFullscreen = { fullscreen = Fullscreen.BUTTON },
                    onMore = { open(Sheet.Song(it)) },
                    onEditTracklist = { open(Sheet.Tracklist(it.id)) },
                )
            }
        }

        GlassSheet(visible = sheet != null, backdrop = root, onDismiss = { sheet = null }) {
            SheetContent(
                sheet = shownSheet,
                app = app,
                library = library,
                connection = connection,
                open = ::open,
                close = { sheet = null },
                play = ::play,
                notify = { notice = it },
                pickFolder = { folderPicker.launch(null) },
            )
        }

        if (widgetPreview) {
            val m = connection.controller?.mediaMetadata
            Box(Modifier.fillMaxSize().background(glass.backgroundBottom)) {
                WidgetPreview(
                    com.philpf.nova.widget.NowPlayingWidget.Info(
                        m?.title?.toString(), m?.artist?.toString(), m?.artworkUri?.path, state.playWhenReady, state.mediaId != null,
                    ),
                )
            }
        }

        if (fullscreen != Fullscreen.OFF && state.mediaId != null) {
            FullscreenVideo(connection, library, lockLandscape = fullscreen == Fullscreen.BUTTON, onExit = { fullscreen = Fullscreen.OFF })
        }

        AnimatedVisibility(
            visible = notice != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            var text by remember { mutableStateOf("") }
            notice?.let { text = it }
            GlassSurface(root, Modifier.statusBarsPadding().padding(top = Space.small, start = Space.margin, end = Space.margin), shape = CircleShape) {
                Text(text, style = GlassType.callout, color = glass.textPrimary, modifier = Modifier.padding(horizontal = Space.padding, vertical = Space.gap))
            }
        }
    }
}

@Composable
private fun SheetContent(
    sheet: Sheet?,
    app: NovaApp,
    library: Library,
    connection: PlayerConnection,
    open: (Sheet) -> Unit,
    close: () -> Unit,
    play: (List<Track>, Int, Boolean, Long) -> Unit,
    notify: (String) -> Unit,
    pickFolder: () -> Unit,
) {
    val playlists by app.playlists.playlists.collectAsState()
    val playingId = connection.state.mediaId
    when (sheet) {
        null -> Unit
        is Sheet.AlbumOf -> library.albums.firstOrNull { it.key == sheet.key }?.let { album ->
            AlbumSheet(album, playingId, { l, i, s -> play(l, i, s, 0) }, { open(Sheet.Song(it)) })
        }
        is Sheet.ArtistOf -> library.artists.firstOrNull { it.name == sheet.name }?.let { artist ->
            ArtistSheet(artist, playingId, { l, i, s -> play(l, i, s, 0) }, { open(Sheet.Song(it)) }, { open(Sheet.AlbumOf(it.key)) })
        }
        is Sheet.PlaylistOf -> playlists.firstOrNull { it.id == sheet.id }?.let { p ->
            PlaylistSheet(
                playlist = p,
                tracks = p.entries.map { e -> library.track(e.mediaId)?.let { t -> t.copy(title = e.title.ifEmpty { t.title }, artist = e.artist ?: t.artist) } },
                playingId = playingId,
                play = { l, i, s -> play(l, i, s, 0) },
                onMore = { t, i -> open(Sheet.Song(t, p.id, i)) },
                onRename = { app.playlists.rename(p.id, it) },
                onDelete = {
                    app.playlists.delete(p.id)
                    close()
                    notify("Deleted “${p.name}”")
                },
            )
        }
        is Sheet.Mix -> library.file(sheet.fileId)?.let { f ->
            MixSheet(
                file = f,
                playingId = playingId,
                positionInPlaying = { connection.position() },
                onPlayFrom = { start -> play(listOf(f.wholeTrack), 0, false, start) },
                onMore = { open(Sheet.Song(it)) },
                // The video's songs, each as a clip of it, in random order.
                onShuffle = { play(f.chapterTracks, 0, true, 0) },
                onSaveAsPlaylist = {
                    app.playlists.create(f.title, f.chapterTracks)
                    close()
                    notify("Saved as a playlist")
                },
                onEdit = { open(Sheet.Tracklist(f.id)) },
            )
        }
        is Sheet.Song -> {
            val t = sheet.track
            val album = library.albums.firstOrNull { a -> a.tracks.any { it.file.id == t.file.id } }
            val artist = library.artists.firstOrNull { a -> a.tracks.any { it.mediaId == t.mediaId } }
            SongMenu(
                track = t,
                hasAlbum = album != null && t.chapterIndex < 0,
                hasArtist = artist != null,
                onPlay = { play(listOf(t), 0, false, 0) },
                onPlayNext = {
                    connection.playNext(listOf(t))
                    close()
                    notify("Playing next")
                },
                onQueue = {
                    connection.addToQueue(listOf(t))
                    close()
                    notify("Added to the queue")
                },
                onAddToPlaylist = { open(Sheet.AddTo(listOf(t), t.title)) },
                onAlbum = { album?.let { open(Sheet.AlbumOf(it.key)) } },
                onArtist = { artist?.let { open(Sheet.ArtistOf(it.name)) } },
                // Tracklists are for videos and long recordings, not for ordinary songs.
                onTracklist = if ((!t.isClip || t.chapterIndex >= 0) && (t.file.isVideo || t.file.isMix || t.file.durationMs >= 15 * 60_000)) {
                    { open(Sheet.Tracklist(t.file.id)) }
                } else {
                    null
                },
                onRemoveFromPlaylist = sheet.playlistId?.let { id ->
                    {
                        app.playlists.removeAt(id, sheet.index)
                        open(Sheet.PlaylistOf(id))
                    }
                },
                // Leaves a folder out (voice notes, recordings); Settings brings it back.
                onDetails = { open(Sheet.Details(t.file.id)) },
                hideFolder = t.file.folder.takeIf { it.isNotEmpty() && app.demoDir == null },
                onHideFolder = {
                    app.settings.hideFolder(t.file.folder)
                    close()
                    notify("Hid “${t.file.folder.substringAfterLast('/')}”. Settings shows it again.")
                },
            )
        }
        is Sheet.AddTo -> AddToPlaylistSheet(
            title = sheet.title,
            playlists = playlists,
            onPick = { p ->
                app.playlists.add(p.id, sheet.tracks)
                close()
                notify("Added to “${p.name}”")
            },
            onCreate = { name ->
                app.playlists.create(name, sheet.tracks)
                close()
                notify("Created “$name”")
            },
        )
        Sheet.NewPlaylist -> NewPlaylistSheet { name ->
            val id = app.playlists.create(name)
            open(Sheet.PlaylistOf(id))
        }
        is Sheet.Details -> library.file(sheet.fileId)?.let { f ->
            val original = app.library.original(f.id) ?: f
            DetailsEditor(f, original, hasEdits = app.library.edits(f.id) != null) { edits ->
                app.library.setEdits(f.id, edits)
                close()
                notify(if (edits == null) "Using the file's own details" else "Details saved")
            }
        }
        is Sheet.Tracklist -> library.file(sheet.fileId)?.let { f ->
            val known = remember(library) { library.files.filter { !it.isMix }.mapNotNull { it.artist }.toSet() }
            TracklistEditor(f, app.library.hasManualTracklist(f.id), known) { chapters ->
                app.library.setTracklist(f.id, chapters)
                close()
                notify(if (chapters == null) "Using the file's own tracklist" else if (chapters.isEmpty()) "Tracklist turned off" else "Tracklist saved")
            }
        }
        Sheet.Settings -> {
            val folder by app.settings.folder.collectAsState()
            val hidden by app.settings.hiddenFolders.collectAsState()
            val skipMessaging by app.settings.skipMessaging.collectAsState()
            val theme by app.settings.theme.collectAsState()
            val showVideo by app.settings.showVideo.collectAsState()
            val progress by app.library.progress.collectAsState()
            SettingsSheet(
                folderName = if (app.demoDir != null) "Sample library (debug build)" else folder?.let { DocumentTree.displayName(it) },
                hiddenFolders = hidden,
                skipMessaging = skipMessaging,
                scanning = progress != null,
                theme = theme,
                showVideo = showVideo,
                onChangeFolder = pickFolder,
                onShowFolder = app.settings::showFolder,
                onSkipMessaging = app.settings::setSkipMessaging,
                scrollTitles = app.settings.scrollTitles.collectAsState().value,
                onScrollTitles = app.settings::setScrollTitles,
                onRescan = app.library::rescan,
                onTheme = app.settings::setTheme,
                onShowVideo = app.settings::setShowVideo,
            )
        }
    }
}

/**
 * The song playing, floating over the library: tap it (or swipe it up) for the player, swipe
 * it sideways for the next or previous song.
 */
@Composable
private fun MiniPlayer(connection: PlayerConnection, track: Track, backdrop: Backdrop, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val state = connection.state
    val positionState = rememberPositionState(connection, active = !track.isClip && track.file.isMix, intervalMs = 1000)
    val now = NowPlaying(track, positionState.longValue, state.durationMs)
    val swipe = rememberSkipSwipe(
        onNext = { connection.next() },
        onPrevious = { connection.previous() },
        key = { connection.state.mediaId to Chapters.indexAt(now.chapters, positionState.longValue) },
        canNext = { connection.canNext() },
    )
    // A wave of the art's colour across the panel each time a song starts (from its picture),
    // and from the button when playing or pausing.
    val waves = rememberWaves()
    val density = LocalDensity.current
    val song = state.mediaId to now.chapterIndex
    var seenSong by remember { mutableStateOf(song) }
    val touch = LocalTouch.current
    LaunchedEffect(song) {
        if (song == seenSong) return@LaunchedEffect
        seenSong = song
        // From where the screen was touched to change it, else from the song's picture.
        val color = waveColor(track.file.art, glass.accent)
        val at = touch.recent()
        if (at != null) {
            waves.start(color, at - waves.rootOffset)
        } else {
            waves.start(color, Offset(with(density) { (Space.inset + 24.dp).toPx() }, with(density) { MiniPlayerHeight.toPx() } / 2f))
        }
    }
    fun playWave() = waves.startAtTouch(glass.accent, touch)
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = Space.margin)
            .padding(bottom = Space.margin)
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .skipGesture(swipe)
            .swipeUpGesture(onOpen),
        shape = RoundedCornerShape(Radius.card),
        onClick = onOpen,
    ) {
        Box(Modifier.matchParentSize().waves(waves))
        Row(
            Modifier.fillMaxSize().skipOffset(swipe).padding(horizontal = Space.inset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.gap),
        ) {
            Artwork(track.file.art, size = 48.dp, video = track.file.isVideo, shape = RoundedCornerShape(Radius.thumb))
            Column(Modifier.weight(1f)) {
                MarqueeText(now.title, GlassType.headline, glass.textPrimary)
                val line = now.artist ?: track.album
                if (line != null) MarqueeText(line, GlassType.caption, glass.textSecondary)
            }
            PlainIconButton(if (state.playWhenReady) NovaIcon.PAUSE else NovaIcon.PLAY, if (state.playWhenReady) "Pause" else "Play", {
                playWave()
                connection.togglePlay()
            }, glass.textPrimary, size = 44.dp, iconSize = 28.dp)
            PlainIconButton(NovaIcon.NEXT, "Next", { connection.next() }, glass.textPrimary, size = 44.dp, iconSize = 26.dp)
        }
    }
}
