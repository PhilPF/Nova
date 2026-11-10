package com.philpf.nova.core

/** A song or video file found in the music folder, with what its tags and sidecars say. */
data class MediaFile(
    /** Its document URI: unique, and what the player opens. */
    val id: String,
    val fileName: String,
    /** The folder it is in, relative to the chosen folder ("" at its top). */
    val folder: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val durationMs: Long,
    val isVideo: Boolean,
    val chapters: List<Chapter> = emptyList(),
    val chapterOrigin: ChapterOrigin = ChapterOrigin.NONE,
    /** A cached picture for it (album art, else a frame of the video), as a file path. */
    val art: String? = null,
    val modified: Long = 0,
    val size: Long = 0,
) {
    /** Whether it holds several songs: a long video with a tracklist. */
    val isMix: Boolean get() = chapters.size >= 2

    /** Its chapter [index] as a song of its own. */
    fun chapterTrack(index: Int): Track {
        val c = chapters[index]
        val named = if (c.artist != null) ArtistTitle(c.artist, c.title) else TrackNames.split(c.title)
        return Track(
            file = this,
            title = named.title,
            artist = named.artist ?: artist,
            album = title,
            startMs = c.startMs,
            endMs = Chapters.endOf(chapters, index, durationMs).takeIf { index + 1 < chapters.size },
            chapterIndex = index,
        )
    }

    val wholeTrack: Track get() = Track(this, title, artist, album, 0, null, -1)

    val chapterTracks: List<Track> get() = chapters.indices.map(::chapterTrack)
}

/**
 * Something to play: a whole file, or a part of one ([startMs] to [endMs], null meaning its
 * end), such as one song of a long video.
 */
data class Track(
    val file: MediaFile,
    val title: String,
    val artist: String?,
    val album: String?,
    val startMs: Long,
    val endMs: Long?,
    /** The chapter it is, or -1. */
    val chapterIndex: Int,
) {
    val isClip: Boolean get() = startMs > 0 || endMs != null
    val mediaId: String get() = MediaIds.of(file.id, startMs, endMs)
    val durationMs: Long get() = (endMs ?: file.durationMs) - startMs
}

/**
 * The player's item ids: a file's URI, or "clip:start:end:URI" for a part of it (end -1 for
 * the end of the file).
 */
object MediaIds {
    private const val CLIP = "clip:"

    fun of(fileId: String, startMs: Long, endMs: Long?): String =
        if (startMs <= 0 && endMs == null) fileId else "$CLIP$startMs:${endMs ?: -1}:$fileId"

    data class Parsed(val fileId: String, val startMs: Long, val endMs: Long?)

    fun parse(mediaId: String): Parsed {
        if (!mediaId.startsWith(CLIP)) return Parsed(mediaId, 0, null)
        val parts = mediaId.removePrefix(CLIP).split(':', limit = 3)
        if (parts.size < 3) return Parsed(mediaId, 0, null)
        val start = parts[0].toLongOrNull() ?: 0
        val end = parts[1].toLongOrNull()?.takeIf { it >= 0 }
        return Parsed(parts[2], start, end)
    }
}

data class Album(val key: String, val title: String, val artist: String?, val tracks: List<Track>) {
    val art: String? get() = tracks.firstNotNullOfOrNull { it.file.art }
    val year: Int? get() = tracks.firstNotNullOfOrNull { it.file.year }
}

/** An artist's songs: whole files, and the songs credited to them inside long videos. */
data class Artist(val name: String, val tracks: List<Track>, val albums: List<Album>) {
    val art: String? get() = albums.firstNotNullOfOrNull { it.art } ?: tracks.firstNotNullOfOrNull { it.file.art }
}

const val UNKNOWN_ARTIST = "Unknown artist"
const val UNKNOWN_ALBUM = "Unknown album"

/** The library, arranged by songs, albums and artists, and the long videos that hold several songs. */
class Library(val files: List<MediaFile>) {
    private val byId = files.associateBy { it.id }

    fun file(id: String): MediaFile? = byId[id]

    /** Every file as a song, by title. */
    val songs: List<Track> by lazy { files.map { it.wholeTrack }.sortedWith(compareBy(collator) { it.title }) }

    /** Files with album tags, grouped by album and album artist, each in disc and track order. */
    val albums: List<Album> by lazy {
        files.filter { !it.album.isNullOrBlank() }
            .groupBy { albumKey(it) }
            .map { (key, list) ->
                val sorted = list.sortedWith(compareBy<MediaFile>({ it.discNumber ?: 0 }, { it.trackNumber ?: Int.MAX_VALUE }).thenBy(collator) { it.title })
                val artists = list.mapNotNull { it.albumArtist ?: it.artist }.distinct()
                Album(key, sorted.first().album!!, artists.singleOrNull() ?: list.firstNotNullOfOrNull { it.albumArtist } ?: if (artists.size > 1) "Various artists" else null, sorted.map { it.wholeTrack })
            }
            .sortedWith(compareBy(collator) { it.title })
    }

    /** Long videos with a tracklist. */
    val mixes: List<MediaFile> by lazy { files.filter { it.isMix }.sortedWith(compareBy(collator) { it.title }) }

    /**
     * Artists of whole files (a mix counts as its uploader's only when its songs have no
     * artists of their own) and of the songs inside mixes.
     */
    val artists: List<Artist> by lazy {
        val tracks = mutableListOf<Track>()
        for (f in files) {
            if (f.isMix) {
                val parts = f.chapterTracks
                tracks += parts
                if (parts.all { it.artist == f.artist }) continue
            }
            tracks += f.wholeTrack
        }
        val albumsByArtist = albums.groupBy { normalize(it.artist ?: UNKNOWN_ARTIST) }
        tracks.groupBy { normalize(it.artist ?: UNKNOWN_ARTIST) }
            .map { (key, list) ->
                val name = list.groupingBy { it.artist ?: UNKNOWN_ARTIST }.eachCount().maxByOrNull { it.value }!!.key
                val ordered = list.distinctBy { it.mediaId }.sortedWith(compareBy<Track, String>(collator) { it.album ?: "￿" }.thenBy { it.file.discNumber ?: 0 }.thenBy { it.file.trackNumber ?: Int.MAX_VALUE }.thenBy { it.startMs })
                Artist(name, ordered, albumsByArtist[key].orEmpty())
            }
            .sortedWith(compareBy(collator) { it.name })
    }

    /** A playable item from its id, as the player and playlists name them. */
    fun track(mediaId: String): Track? {
        val p = MediaIds.parse(mediaId)
        val f = byId[p.fileId] ?: return null
        if (p.startMs <= 0 && p.endMs == null) return f.wholeTrack
        val i = f.chapters.indexOfFirst { it.startMs == p.startMs }
        if (i >= 0) {
            val t = f.chapterTrack(i)
            if (t.endMs == p.endMs) return t
        }
        return Track(f, f.title, f.artist, f.album, p.startMs, p.endMs, -1)
    }

    fun search(query: String): List<Track> {
        val q = query.trim()
        if (q.isEmpty()) return songs
        val all = files.flatMap { if (it.isMix) listOf(it.wholeTrack) + it.chapterTracks else listOf(it.wholeTrack) }
        return all.filter { t -> listOfNotNull(t.title, t.artist, t.album).any { it.contains(q, ignoreCase = true) } }
            .sortedWith(compareBy(collator) { it.title })
    }

    companion object {
        val EMPTY = Library(emptyList())
        private val collator: Comparator<String> = Comparator { a, b -> a.compareTo(b, ignoreCase = true) }

        fun normalize(name: String) = name.trim().lowercase()

        fun albumKey(f: MediaFile) = normalize(f.album.orEmpty()) + "\u0000" + normalize(f.albumArtist ?: "")
    }
}
