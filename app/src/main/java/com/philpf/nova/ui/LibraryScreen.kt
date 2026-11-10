package com.philpf.nova.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.philpf.nova.core.Album
import com.philpf.nova.core.Artist
import com.philpf.nova.core.ChapterOrigin
import com.philpf.nova.core.Library
import com.philpf.nova.core.MediaFile
import com.philpf.nova.core.Playlist
import com.philpf.nova.core.Track
import com.philpf.nova.device.ScanProgress
import com.philpf.nova.ui.art.Icon
import com.philpf.nova.ui.art.NovaIcon
import com.philpf.nova.ui.glass.Backdrop
import com.philpf.nova.ui.glass.GlassButton
import com.philpf.nova.ui.glass.GlassIconButton
import com.philpf.nova.ui.glass.GlassSurface
import com.philpf.nova.ui.glass.GlassTextField
import com.philpf.nova.ui.glass.SectionLabel
import com.philpf.nova.ui.glass.SegmentedControl
import com.philpf.nova.ui.theme.GlassType
import com.philpf.nova.ui.theme.LocalGlass
import com.philpf.nova.ui.theme.contentColorOn

enum class LibraryTab(val label: String) { SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), PLAYLISTS("Playlists") }

/** What the library page does when something in it is used. */
class LibraryActions(
    val playTracks: (List<Track>, Int) -> Unit,
    val openTrack: (Track) -> Unit,
    val openAlbum: (Album) -> Unit,
    val openArtist: (Artist) -> Unit,
    val openPlaylist: (Playlist) -> Unit,
    val openMix: (MediaFile) -> Unit,
    val newPlaylist: () -> Unit,
    val chooseFolder: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * The library page: the songs in the music folder by song, album or artist, and the
 * playlists, those made by the user and those read from long videos. Swipe sideways (or tap
 * the tabs) to go from one to the next; the search applies to the page shown.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryContent(
    library: Library,
    playlists: List<Playlist>,
    tab: LibraryTab,
    onTab: (LibraryTab) -> Unit,
    query: TextFieldValue,
    onQuery: (TextFieldValue) -> Unit,
    hasFolder: Boolean,
    progress: ScanProgress?,
    playingId: String?,
    backdrop: Backdrop,
    actions: LibraryActions,
    bottomSpace: androidx.compose.ui.unit.Dp,
) {
    val glass = LocalGlass.current
    val q = query.text.trim()
    val pager = rememberPagerState(initialPage = tab.ordinal) { LibraryTab.entries.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { onTab(LibraryTab.entries[pager.currentPage]) }
    LaunchedEffect(tab) { if (pager.currentPage != tab.ordinal && !pager.isScrollInProgress) pager.scrollToPage(tab.ordinal) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(Modifier.weight(1f))
                if (progress != null) {
                    Text(
                        if (progress.total > 0) "${progress.done} / ${progress.total}" else "Scanning…",
                        style = GlassType.caption,
                        color = glass.textTertiary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
                GlassIconButton(onClick = actions.openSettings, label = "Settings") {
                    Icon(NovaIcon.GEAR, glass.textPrimary, Modifier.size(22.dp))
                }
            }
            SegmentedControl(
                options = LibraryTab.entries.map { it.label },
                selectedIndex = pager.currentPage,
                onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                position = { pager.currentPage + pager.currentPageOffsetFraction },
            )
            Spacer(Modifier.height(10.dp))
            if (library.files.isNotEmpty()) {
                GlassTextField(value = query, onValueChange = onQuery, placeholder = "Search")
                Spacer(Modifier.height(6.dp))
            }
        }

        if (library.files.isEmpty()) {
            Column(Modifier.padding(horizontal = 14.dp)) { EmptyLibrary(hasFolder, progress != null, backdrop, actions.chooseFolder) }
            return@Column
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp)) {
                when (LibraryTab.entries[page]) {
                    LibraryTab.SONGS -> {
                        val songs = if (q.isEmpty()) library.songs else library.search(q)
                        if (songs.isEmpty()) item { NothingFound(q) }
                        items(songs, key = { "s" + it.mediaId }) { t ->
                            TrackRow(t, playing = t.mediaId == playingId, onClick = { actions.playTracks(songs, songs.indexOf(t)) }, onMore = { actions.openTrack(t) })
                        }
                    }
                    LibraryTab.ALBUMS -> {
                        val albums = library.albums.filter { q.isEmpty() || it.title.contains(q, true) || it.artist?.contains(q, true) == true }
                        if (albums.isEmpty()) item { NothingFound(q, if (q.isEmpty()) "No album tags in these files yet." else null) }
                        items(albums.chunked(2), key = { "a" + it.first().key }) { pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                for (album in pair) AlbumCard(album, backdrop, { actions.openAlbum(album) }, Modifier.weight(1f))
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    LibraryTab.ARTISTS -> {
                        val artists = library.artists.filter { q.isEmpty() || it.name.contains(q, true) }
                        if (artists.isEmpty()) item { NothingFound(q) }
                        items(artists, key = { "r" + it.name }) { a -> ArtistRow(a) { actions.openArtist(a) } }
                    }
                    LibraryTab.PLAYLISTS -> playlistItems(
                        library,
                        playlists.filter { q.isEmpty() || it.name.contains(q, true) },
                        library.mixes.filter { q.isEmpty() || it.title.contains(q, true) },
                        backdrop,
                        actions,
                    )
                }
                item(key = "bottom") {
                    Spacer(Modifier.height(bottomSpace))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }
}

/** Newsreader's capitals are 0.715 em tall over the baseline. */
private const val CAP_HEIGHT_EM = 0.715f

/**
 * The star and "Nova". The star is centred on the capitals (half the cap height over the
 * baseline), not on the text's box, which also holds room for descenders; its points reach a
 * little past the capitals, which reads as the same height.
 */
@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val density = LocalDensity.current
    val size = 36.sp
    val capPx = with(density) { size.toPx() } * CAP_HEIGHT_EM
    // The star fills 20 of the icon's 24 units.
    val star = with(density) { (size * (CAP_HEIGHT_EM * 1.12f * 24f / 20f)).toDp() }
    Row(modifier) {
        Icon(
            NovaIcon.STAR,
            glass.textPrimary,
            Modifier
                .size(star)
                .alignBy { it.measuredHeight / 2 + (capPx / 2f).roundToInt() },
        )
        Spacer(Modifier.width(with(density) { (size * 0.18f).toDp() }))
        Text(
            "Nova",
            style = GlassType.display.copy(fontSize = size, lineHeight = 40.sp),
            color = glass.textPrimary,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

private fun LazyListScope.playlistItems(library: Library, playlists: List<Playlist>, mixes: List<MediaFile>, backdrop: Backdrop, actions: LibraryActions) {
    item(key = "new") {
        Row(Modifier.padding(bottom = 12.dp)) {
            GlassButton(
                text = "New playlist",
                onClick = actions.newPlaylist,
                icon = { Icon(NovaIcon.ADD, LocalGlass.current.textPrimary, Modifier.size(18.dp)) },
            )
        }
    }
    if (playlists.isNotEmpty()) {
        item(key = "mine") { SectionLabel("Your playlists", Modifier.padding(top = 6.dp)) }
        items(playlists, key = { "p" + it.id }) { p ->
            val art = p.entries.firstNotNullOfOrNull { library.file(it.fileId)?.art }
            CollectionCard(p.name, songCount(p.entries.size), art, false, backdrop) { actions.openPlaylist(p) }
        }
    }
    item(key = "mixes") { SectionLabel("From videos", Modifier.padding(top = 14.dp)) }
    if (mixes.isEmpty()) {
        item(key = "nomix") {
            Text(
                "Long videos with a tracklist show up here, a song per chapter. Nova reads chapters " +
                    "saved in the file, yt-dlp's .info.json, CUE sheets and timestamps in the description; " +
                    "or add one yourself from a video's menu.",
                style = GlassType.callout,
                color = LocalGlass.current.textSecondary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
    items(mixes, key = { "m" + it.id }) { f ->
        CollectionCard(f.title, "${songCount(f.chapters.size)} · ${originLabel(f.chapterOrigin)}", f.art, f.isVideo, backdrop) { actions.openMix(f) }
    }
}

fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"

fun originLabel(origin: ChapterOrigin) = when (origin) {
    ChapterOrigin.MANUAL -> "your tracklist"
    ChapterOrigin.EMBEDDED -> "chapters"
    ChapterOrigin.INFO_JSON -> "YouTube chapters"
    ChapterOrigin.CUE -> "CUE sheet"
    ChapterOrigin.DESCRIPTION -> "from the description"
    ChapterOrigin.NONE -> ""
}

@Composable
private fun CollectionCard(title: String, subtitle: String, art: String?, video: Boolean, backdrop: Backdrop, onClick: () -> Unit) {
    val glass = LocalGlass.current
    GlassSurface(backdrop, Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Artwork(art, size = 64.dp, video = video, shape = RoundedCornerShape(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = GlassType.headline, color = glass.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = GlassType.caption, color = glass.textSecondary, maxLines = 1)
            }
            Icon(NovaIcon.CHEVRON_RIGHT, glass.textTertiary, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AlbumCard(album: Album, backdrop: Backdrop, onClick: () -> Unit, modifier: Modifier) {
    val glass = LocalGlass.current
    GlassSurface(backdrop, modifier, shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Artwork(album.art, Modifier.fillMaxSize(), size = 400.dp, shape = RoundedCornerShape(16.dp), sizePx = 400)
            }
            Spacer(Modifier.height(8.dp))
            Text(album.title, style = GlassType.headline, color = glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.artist ?: songCount(album.tracks.size), style = GlassType.caption, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(artist.art, size = 48.dp, shape = CircleShape)
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = GlassType.headline, color = glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val albums = artist.albums.size
            Text(
                listOfNotNull(songCount(artist.tracks.size), if (albums > 0) if (albums == 1) "1 album" else "$albums albums" else null).joinToString(" · "),
                style = GlassType.caption,
                color = glass.textSecondary,
            )
        }
        Icon(NovaIcon.CHEVRON_RIGHT, glass.textTertiary, Modifier.size(20.dp))
    }
}

@Composable
private fun NothingFound(query: String, text: String? = null) {
    Text(
        text ?: if (query.isEmpty()) "Nothing here yet." else "Nothing matches “$query”.",
        style = GlassType.body,
        color = LocalGlass.current.textSecondary,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun EmptyLibrary(hasFolder: Boolean, scanning: Boolean, backdrop: Backdrop, onChoose: () -> Unit) {
    val glass = LocalGlass.current
    GlassSurface(backdrop, Modifier.fillMaxWidth().padding(top = 18.dp), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(NovaIcon.STAR, glass.accent, Modifier.size(34.dp))
            Text(
                when {
                    scanning -> "Looking for music…"
                    hasFolder -> "No songs or videos found"
                    else -> "Choose your music folder"
                },
                style = GlassType.title,
                color = glass.textPrimary,
            )
            Text(
                if (hasFolder) {
                    "Nova plays the songs and videos in the folder you chose and its subfolders. Choose another folder, or put music in it and rescan from Settings."
                } else {
                    "Nova plays the songs and videos you have downloaded. Choose the folder they are in: Nova reads it and all its subfolders, and only it."
                },
                style = GlassType.callout,
                color = glass.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                GlassButton(
                    text = if (hasFolder) "Choose another folder" else "Choose folder",
                    onClick = onChoose,
                    primary = true,
                    icon = { Icon(NovaIcon.FOLDER, contentColorOn(glass.accent), Modifier.size(18.dp)) },
                )
            }
        }
    }
}
