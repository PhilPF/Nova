package com.philpf.nova.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.philpf.nova.core.Album
import com.philpf.nova.core.Artist
import com.philpf.nova.core.Chapters
import com.philpf.nova.core.FileEdits
import com.philpf.nova.core.NameOrder
import com.philpf.nova.core.TrackNames
import com.philpf.nova.core.Tracklists
import com.philpf.nova.core.MediaFile
import com.philpf.nova.core.Playlist
import com.philpf.nova.core.TimestampText
import com.philpf.nova.core.Track
import com.philpf.nova.device.ThemeMode
import com.philpf.nova.ui.art.Icon
import com.philpf.nova.ui.art.NovaIcon
import com.philpf.nova.ui.glass.GlassButton
import com.philpf.nova.ui.glass.GlassSwitch
import com.philpf.nova.ui.glass.GlassTextField
import com.philpf.nova.ui.glass.SectionLabel
import com.philpf.nova.ui.glass.SegmentedControl
import com.philpf.nova.ui.glass.SettingRow
import com.philpf.nova.ui.theme.GlassType
import com.philpf.nova.ui.theme.LocalGlass
import com.philpf.nova.ui.theme.contentColorOn

/** Play and Shuffle, side by side, at the top of a list. */
@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    val glass = LocalGlass.current
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton("Play", onPlay, Modifier.weight(1f), primary = true, icon = { Icon(NovaIcon.PLAY, contentColorOn(glass.accent), Modifier.size(18.dp)) })
        GlassButton("Shuffle", onShuffle, Modifier.weight(1f), icon = { Icon(NovaIcon.SHUFFLE, glass.textPrimary, Modifier.size(18.dp)) })
    }
}

@Composable
fun AlbumSheet(album: Album, playingId: String?, play: (List<Track>, Int, Boolean) -> Unit, onMore: (Track) -> Unit) {
    val minutes = album.tracks.sumOf { it.durationMs } / 60_000
    SheetHeader(album.title, listOfNotNull(album.artist, album.year?.toString(), "${songCount(album.tracks.size)}, $minutes min").joinToString(" · "), album.art)
    PlayShuffleRow({ play(album.tracks, 0, false) }, { play(album.tracks, 0, true) })
    album.tracks.forEachIndexed { i, t ->
        TrackRow(t, t.mediaId == playingId, { play(album.tracks, i, false) }, { onMore(t) }, number = (t.file.trackNumber ?: (i + 1)).toString(), subtitle = t.artist.takeIf { it != album.artist } ?: "")
    }
}

@Composable
fun ArtistSheet(artist: Artist, playingId: String?, play: (List<Track>, Int, Boolean) -> Unit, onMore: (Track) -> Unit, openAlbum: (Album) -> Unit) {
    val glass = LocalGlass.current
    SheetHeader(artist.name, songCount(artist.tracks.size), artist.art, round = true)
    PlayShuffleRow({ play(artist.tracks, 0, false) }, { play(artist.tracks, 0, true) })
    if (artist.albums.isNotEmpty()) {
        SectionLabel("Albums", Modifier.padding(top = 6.dp))
        artist.albums.forEach { a ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { openAlbum(a) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(a.art, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = GlassType.headline, color = glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(a.year?.toString(), songCount(a.tracks.size)).joinToString(" · "), style = GlassType.caption, color = glass.textSecondary)
                }
                Icon(NovaIcon.CHEVRON_RIGHT, glass.textTertiary, Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    SectionLabel("Songs", Modifier.padding(top = 6.dp))
    artist.tracks.forEachIndexed { i, t ->
        val from = if (t.chapterIndex >= 0) "in ${t.file.title}" else t.album
        TrackRow(t, t.mediaId == playingId, { play(artist.tracks, i, false) }, { onMore(t) }, subtitle = from ?: "")
    }
}

@Composable
fun PlaylistSheet(
    playlist: Playlist,
    tracks: List<Track?>,
    playingId: String?,
    play: (List<Track>, Int, Boolean) -> Unit,
    onMore: (Track, Int) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val glass = LocalGlass.current
    var renaming by remember { mutableStateOf(false) }
    var name by remember(playlist.id) { mutableStateOf(TextFieldValue(playlist.name)) }
    val playable = tracks.filterNotNull()
    SheetHeader(playlist.name, songCount(playlist.entries.size), playable.firstNotNullOfOrNull { it.file.art })
    if (renaming) {
        GlassTextField(name, { name = it }, "Name")
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Save", { onRename(name.text); renaming = false }, primary = true)
            GlassButton("Cancel", { renaming = false })
        }
    } else {
        PlayShuffleRow({ play(playable, 0, false) }, { play(playable, 0, true) })
    }
    if (playlist.entries.isEmpty()) {
        Text("Add songs from their menu (⋮ or a long press): Add to playlist.", style = GlassType.callout, color = glass.textSecondary, modifier = Modifier.padding(8.dp))
    }
    playlist.entries.forEachIndexed { i, e ->
        val t = tracks.getOrNull(i)
        if (t != null) {
            val from = if (t.isClip) "${t.artist ?: ""} · in ${t.file.title}".trim(' ', '·') else t.artist
            TrackRow(t, t.mediaId == playingId, { play(playable, playable.indexOf(t), false) }, { onMore(t, i) }, subtitle = from ?: "")
        } else {
            Text("${e.title} (missing)", style = GlassType.body, color = glass.textTertiary, modifier = Modifier.padding(10.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
    ActionRow(NovaIcon.EDIT, "Rename", { renaming = true })
    ActionRow(NovaIcon.DELETE, "Delete playlist", onDelete, danger = true)
}

/** A long video's songs, from its tracklist. */
@Composable
fun MixSheet(
    file: MediaFile,
    playingId: String?,
    positionInPlaying: () -> Long,
    onPlayFrom: (Long) -> Unit,
    onMore: (Track) -> Unit,
    onShuffle: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    onEdit: () -> Unit,
) {
    val glass = LocalGlass.current
    SheetHeader(file.title, listOfNotNull(file.artist, "${songCount(file.chapters.size)} · ${originLabel(file.chapterOrigin)}").joinToString(" · "), file.art, file.isVideo)
    PlayShuffleRow({ onPlayFrom(0) }, onShuffle)
    val current = if (playingId == file.id) Chapters.indexAt(file.chapters, positionInPlaying()) else -1
    file.chapterTracks.forEachIndexed { i, t ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (i == current) glass.controlFillSelected else androidx.compose.ui.graphics.Color.Transparent)
                .clickable { onPlayFrom(t.startMs) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(Chapters.formatTime(t.startMs), style = GlassType.caption, color = glass.textTertiary, modifier = Modifier.padding(end = 4.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = GlassType.body, color = if (i == current) glass.accent else glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.artist != null) Text(t.artist, style = GlassType.caption, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(Chapters.formatTime(t.durationMs), style = GlassType.caption, color = glass.textTertiary)
            PlainIconButton(NovaIcon.MORE, "More", { onMore(t) }, glass.textSecondary, size = 34.dp, iconSize = 18.dp)
        }
    }
    Spacer(Modifier.height(8.dp))
    ActionRow(NovaIcon.PLAYLIST_ADD, "Save as a playlist", onSaveAsPlaylist)
    ActionRow(NovaIcon.EDIT, "Edit tracklist", onEdit)
}

/** What can be done with a song. */
@Composable
fun SongMenu(
    track: Track,
    hasAlbum: Boolean,
    hasArtist: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onTracklist: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)?,
    onDetails: () -> Unit = {},
    hideFolder: String? = null,
    onHideFolder: () -> Unit = {},
) {
    val sub = listOfNotNull(track.artist, if (track.chapterIndex >= 0) "in ${track.file.title}" else track.album).joinToString(" · ")
    SheetHeader(track.title, sub, track.file.art, track.file.isVideo)
    ActionRow(NovaIcon.PLAY, "Play", onPlay)
    ActionRow(NovaIcon.NEXT, "Play next", onPlayNext)
    ActionRow(NovaIcon.QUEUE, "Add to queue", onQueue)
    ActionRow(NovaIcon.PLAYLIST_ADD, "Add to playlist…", onAddToPlaylist)
    if (hasAlbum) ActionRow(NovaIcon.NOTE, "Go to album", onAlbum)
    if (hasArtist) ActionRow(NovaIcon.STAR, "Go to artist", onArtist)
    // A song inside a video is named by the tracklist; the file itself by its details.
    if (track.chapterIndex < 0) ActionRow(NovaIcon.EDIT, "Edit details…", onDetails)
    if (onTracklist != null) ActionRow(NovaIcon.LIST, if (track.file.isMix) "Tracklist…" else "Add a tracklist…", onTracklist)
    if (onRemoveFromPlaylist != null) ActionRow(NovaIcon.DELETE, "Remove from this playlist", onRemoveFromPlaylist, danger = true)
    if (hideFolder != null) ActionRow(NovaIcon.FOLDER, "Hide the folder “${hideFolder.substringAfterLast('/')}”", onHideFolder)
}

@Composable
fun AddToPlaylistSheet(title: String, playlists: List<Playlist>, onPick: (Playlist) -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    SheetTitle("Add “$title” to")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { GlassTextField(name, { name = it }, "New playlist") }
        GlassButton("Create", { onCreate(name.text.ifBlank { "Playlist" }) }, primary = true)
    }
    Spacer(Modifier.height(10.dp))
    playlists.forEach { p -> ActionRow(NovaIcon.QUEUE, "${p.name} (${p.entries.size})", { onPick(p) }) }
}

@Composable
fun NewPlaylistSheet(onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    SheetTitle("New playlist")
    GlassTextField(name, { name = it }, "Name")
    Spacer(Modifier.height(12.dp))
    Row { GlassButton("Create", { onCreate(name.text.ifBlank { "Playlist" }) }, primary = true) }
}

/**
 * A file's title, artist and album, set in the app over its tags: for files whose tags cannot
 * be edited elsewhere, or that have none ("Unknown artist"). The tags stay as they are.
 */
@Composable
fun DetailsEditor(file: MediaFile, original: MediaFile, hasEdits: Boolean, onSave: (FileEdits?) -> Unit) {
    val glass = LocalGlass.current
    var title by remember(file.id) { mutableStateOf(TextFieldValue(file.title)) }
    var artist by remember(file.id) { mutableStateOf(TextFieldValue(file.artist.orEmpty())) }
    var album by remember(file.id) { mutableStateOf(TextFieldValue(file.album.orEmpty())) }
    SheetTitle("Details")
    Text(file.fileName, style = GlassType.caption, color = glass.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(12.dp))
    SectionLabel("Title")
    GlassTextField(title, { title = it }, original.title)
    Spacer(Modifier.height(10.dp))
    SectionLabel("Artist")
    GlassTextField(artist, { artist = it }, original.artist ?: "Unknown artist")
    Spacer(Modifier.height(10.dp))
    SectionLabel("Album")
    GlassTextField(album, { album = it }, original.album ?: "None")
    Spacer(Modifier.height(8.dp))
    Text(
        "Kept in Nova; the file is not changed. A long video's songs take this artist when its tracklist names none.",
        style = GlassType.caption,
        color = glass.textTertiary,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton("Save", { onSave(FileEdits.of(original, title.text, artist.text, album.text)) }, primary = true)
        if (hasEdits) GlassButton("Use the file's own", { onSave(null) })
    }
}

/**
 * The tracklist of a file, as text: one song per line with its start time. Pasting a YouTube
 * description or comment works, as only the lines with a time are read. Whether lines say
 * "Artist - Title" or "Title - Artist" is guessed (from the artists in the library, and from
 * which side repeats) and can be switched; the songs are shown as they will be saved.
 */
@Composable
fun TracklistEditor(file: MediaFile, isManual: Boolean, knownArtists: Collection<String>, onSave: (List<com.philpf.nova.core.Chapter>?) -> Unit) {
    val glass = LocalGlass.current
    var text by remember(file.id) { mutableStateOf(TextFieldValue(if (file.chapters.isNotEmpty()) TimestampText.format(file.chapters) else "")) }
    var defaultArtist by remember(file.id) { mutableStateOf(TextFieldValue(file.artist.orEmpty())) }
    // Guessed from the text until the switch is used. Songs that already name their artist
    // are written back "Artist - Title", so they start that way.
    var chosenOrder by remember(file.id) {
        mutableStateOf(if (file.chapters.isNotEmpty() && file.chapters.all { it.artist != null }) NameOrder.ARTIST_FIRST else null)
    }
    val lines = remember(text.text) { TimestampText.parse(text.text).map { it.title } }
    val detected = remember(lines, knownArtists) { TrackNames.detectOrder(lines, knownArtists) }
    val order = chosenOrder ?: detected
    val songs = remember(text.text, order, defaultArtist.text) { Tracklists.fromText(text.text, order, defaultArtist.text, file.durationMs) }

    SheetTitle("Tracklist")
    Text(file.title, style = GlassType.callout, color = glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(8.dp))
    Text(
        "One song per line, starting with its time: “3:41 Artist - Title”. Paste a video's description " +
            "or the comment with its tracklist; lines without a time are skipped.",
        style = GlassType.caption,
        color = glass.textTertiary,
    )
    Spacer(Modifier.height(10.dp))
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = GlassType.callout.copy(color = glass.textPrimary),
        cursorBrush = SolidColor(glass.accent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 280.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(glass.controlFill)
            .padding(14.dp),
        decorationBox = { inner ->
            if (text.text.isEmpty()) Text("0:00 First Artist - First Song\n3:41 Second Artist - Second Song", style = GlassType.callout, color = glass.textTertiary)
            inner()
        },
    )
    Spacer(Modifier.height(12.dp))
    SectionLabel("Each line says")
    SegmentedControl(
        listOf("Artist – Title", "Title – Artist"),
        if (order == NameOrder.ARTIST_FIRST) 0 else 1,
        { chosenOrder = if (it == 0) NameOrder.ARTIST_FIRST else NameOrder.TITLE_FIRST },
    )
    Spacer(Modifier.height(10.dp))
    SectionLabel("Artist of lines that name none")
    GlassTextField(defaultArtist, { defaultArtist = it }, "Unknown artist")

    Spacer(Modifier.height(14.dp))
    SectionLabel(if (songs.isEmpty()) "No songs yet: at least two lines with a time" else "As it will show · ${songCount(songs.size)}")
    songs.take(60).forEach { c ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Chapters.formatTime(c.startMs), style = GlassType.caption, color = glass.textTertiary, modifier = Modifier.width(52.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = GlassType.body, color = glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.artist ?: "Unknown artist", style = GlassType.caption, color = if (c.artist == null) glass.danger else glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (songs.size > 60) Text("and ${songs.size - 60} more", style = GlassType.caption, color = glass.textTertiary, modifier = Modifier.padding(6.dp))
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton("Save", { onSave(songs) }, primary = true, enabled = songs.isNotEmpty() || text.text.isBlank())
        if (isManual) GlassButton("Use the file's own", { onSave(null) })
    }
}

@Composable
fun SettingsSheet(
    folderName: String?,
    hiddenFolders: List<String>,
    skipMessaging: Boolean,
    scanning: Boolean,
    theme: ThemeMode,
    showVideo: Boolean,
    onChangeFolder: () -> Unit,
    onShowFolder: (String) -> Unit,
    onSkipMessaging: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onShowVideo: (Boolean) -> Unit,
) {
    val glass = LocalGlass.current
    SheetTitle("Settings")
    SectionLabel("Music folder", Modifier.padding(top = 4.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(NovaIcon.FOLDER, glass.textSecondary, Modifier.size(20.dp))
        Text(folderName ?: "None chosen yet", style = GlassType.body, color = glass.textPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton(if (folderName == null) "Choose folder" else "Change folder", onChangeFolder, icon = { Icon(NovaIcon.FOLDER, glass.textPrimary, Modifier.size(18.dp)) })
        GlassButton(if (scanning) "Scanning…" else "Rescan", onRescan, enabled = !scanning, icon = { Icon(NovaIcon.REFRESH, glass.textPrimary, Modifier.size(18.dp)) })
    }

    SectionLabel("Left out", Modifier.padding(top = 12.dp))
    SettingRow("WhatsApp and Telegram audio") { GlassSwitch(skipMessaging, onSkipMessaging, "Leave out WhatsApp and Telegram audio") }
    if (hiddenFolders.isEmpty()) {
        Text("Hide a folder from the menu of one of its songs.", style = GlassType.caption, color = glass.textTertiary, modifier = Modifier.padding(horizontal = 16.dp))
    }
    hiddenFolders.forEach { path ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(path, style = GlassType.body, color = glass.textSecondary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            GlassButton("Show", { onShowFolder(path) })
        }
        Spacer(Modifier.height(6.dp))
    }

    SectionLabel("Appearance", Modifier.padding(top = 12.dp))
    SegmentedControl(listOf("System", "Light", "Dark"), theme.ordinal, { onTheme(ThemeMode.entries[it]) })
    SettingRow("Play videos as video") { GlassSwitch(showVideo, onShowVideo, "Play videos as video") }

    SectionLabel("Tracklists of long videos", Modifier.padding(top = 12.dp))
    Text(
        "A long video with several songs shows each as a song of its own, in Playlists › From videos " +
            "and under their artists. Nova reads its tracklist from, in this order:\n" +
            "• the tracklist you entered (a video's menu › Tracklist)\n" +
            "• chapters saved in the file: download with yt-dlp --embed-chapters --embed-metadata\n" +
            "• yt-dlp's “.info.json” next to the file (--write-info-json)\n" +
            "• a “.cue” sheet next to the file\n" +
            "• timestamps in the description: the file's, “.description” (--write-description) or the info file's.\n" +
            "YouTube's chapters are the uploader's or those YouTube read from the description; " +
            "a tracklist in a comment can be pasted.",
        style = GlassType.caption,
        color = glass.textSecondary,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}
