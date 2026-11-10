package com.philpf.nova.core

data class ArtistTitle(val artist: String?, val title: String)

/** Which side of "A - B" names the artist. */
enum class NameOrder { ARTIST_FIRST, TITLE_FIRST }

/** Reads an artist and a title out of the names videos and their chapters are given. */
object TrackNames {
    // Spaced dashes only, so names such as "Jay-Z" or "Sigur Rós - Hoppípolla" split correctly.
    private val separators = listOf(" - ", " – ", " — ", " -- ", " | ")
    private val numbering = Regex("""^\s*(?:#?\d{1,3}\s*[.):\]]\s*|\[\d{1,3}]\s*)""")
    private val quotes = Regex("""^["“”'‘’«»]+|["“”'‘’«»]+$""")

    /**
     * "Artist - Title" (also with an en or em dash, "--" or "|"), after any leading track
     * number ("1.", "01)", "[3]"). Without a separator the whole text is the title. Quotes
     * around either part are dropped.
     */
    fun split(text: String): ArtistTitle {
        val clean = text.replace(numbering, "").trim()
        for (separator in separators) {
            val at = clean.indexOf(separator)
            if (at > 0) {
                val artist = clean.substring(0, at).trim().replace(quotes, "").trim()
                val title = clean.substring(at + separator.length).trim().replace(quotes, "").trim()
                if (artist.isNotEmpty() && title.isNotEmpty()) return ArtistTitle(artist, title)
            }
        }
        return ArtistTitle(null, clean.replace(quotes, "").trim().ifEmpty { text.trim() })
    }

    /** [split] read in [order]: with [NameOrder.TITLE_FIRST], "Title - Artist". */
    fun split(text: String, order: NameOrder): ArtistTitle {
        val a = split(text)
        return if (order == NameOrder.TITLE_FIRST && a.artist != null) ArtistTitle(a.title, a.artist) else a
    }

    /**
     * Whether names written "A - B" put the artist first or last. The side that names more
     * artists already known (from the library) is the artist's; when that does not decide, the
     * side whose names repeat more is, as an artist often has several songs in one tracklist
     * and a title rarely appears twice. Ties keep the usual order, artist first.
     */
    fun detectOrder(names: List<String>, knownArtists: Collection<String> = emptyList()): NameOrder {
        val pairs = names.map(::split).filter { it.artist != null }
        if (pairs.isEmpty()) return NameOrder.ARTIST_FIRST
        val known = knownArtists.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        fun isKnown(s: String) = s.trim().lowercase().let { n -> n in known || n.split(Regex("""\s*(?:,|&| feat\.? | ft\.? | x )\s*""")).any { it in known } }
        val left = pairs.count { isKnown(it.artist!!) }
        val right = pairs.count { isKnown(it.title) }
        if (left != right) return if (right > left) NameOrder.TITLE_FIRST else NameOrder.ARTIST_FIRST
        val leftRepeats = pairs.size - pairs.map { it.artist!!.lowercase() }.distinct().size
        val rightRepeats = pairs.size - pairs.map { it.title.lowercase() }.distinct().size
        return if (rightRepeats > leftRepeats) NameOrder.TITLE_FIRST else NameOrder.ARTIST_FIRST
    }

    /** A file name without its extension, as a title. */
    fun baseName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    /** yt-dlp's default names end in " [videoId]"; that is not part of the title. */
    fun withoutVideoId(name: String): String = name.replace(Regex("""\s*\[[A-Za-z0-9_-]{11}]$"""), "").trim()
}
