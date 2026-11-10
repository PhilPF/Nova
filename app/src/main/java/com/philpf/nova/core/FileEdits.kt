package com.philpf.nova.core

/**
 * A file's title, artist and album as set in the app, over what its tags say (null keeps the
 * tag). For files whose tags cannot be changed from elsewhere, such as videos in a folder only
 * this app reads, or tags that are simply missing ("Unknown artist").
 */
data class FileEdits(val title: String? = null, val artist: String? = null, val album: String? = null) {
    val isEmpty: Boolean get() = title == null && artist == null && album == null

    fun applyTo(f: MediaFile): MediaFile = f.copy(
        title = title ?: f.title,
        artist = artist ?: f.artist,
        album = album ?: f.album,
        // An album artist from the tags would keep the album under its old artist.
        albumArtist = if (artist != null && f.albumArtist == f.artist) null else f.albumArtist,
    )

    companion object {
        /** Edits from what a form holds: a field left as the file's own, or blank, is not an edit. */
        fun of(f: MediaFile, title: String, artist: String, album: String) = FileEdits(
            title = title.trim().takeIf { it.isNotEmpty() && it != f.title },
            artist = artist.trim().takeIf { it.isNotEmpty() && it != f.artist },
            album = album.trim().takeIf { it.isNotEmpty() && it != f.album },
        )
    }
}

object FileEditsCodec {
    fun encode(map: Map<String, FileEdits>): String =
        Json.write(map.mapValues { (_, e) -> mapOf("title" to e.title, "artist" to e.artist, "album" to e.album) })

    fun decode(text: String): Map<String, FileEdits> {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return emptyMap()
        return root.entries.mapNotNull { (k, v) ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            (k as? String ?: return@mapNotNull null) to FileEdits(m.string("title"), m.string("artist"), m.string("album"))
        }.toMap()
    }
}

object Tracklists {
    /**
     * A tracklist typed or pasted in the app, read in [order]; each song gets its own artist
     * (a line without one gets [defaultArtist]), so the library shows them as written.
     */
    fun fromText(text: String, order: NameOrder, defaultArtist: String?, durationMs: Long): List<Chapter> =
        Chapters.normalize(TimestampText.parse(text), durationMs).map { c ->
            val named = TrackNames.split(c.title, order)
            Chapter(c.startMs, named.title, named.artist ?: defaultArtist?.trim()?.ifEmpty { null })
        }

    /**
     * A file's chapters with the artist and title of each read in the order detected for the
     * whole list (see [TrackNames.detectOrder]); chapters that already name their artist stay.
     */
    fun ordered(chapters: List<Chapter>, knownArtists: Collection<String>): List<Chapter> {
        val untagged = chapters.filter { it.artist == null }
        if (untagged.isEmpty()) return chapters
        if (TrackNames.detectOrder(untagged.map { it.title }, knownArtists) != NameOrder.TITLE_FIRST) return chapters
        return chapters.map { c ->
            if (c.artist != null) return@map c
            val named = TrackNames.split(c.title, NameOrder.TITLE_FIRST)
            if (named.artist == null) c else Chapter(c.startMs, named.title, named.artist)
        }
    }
}
