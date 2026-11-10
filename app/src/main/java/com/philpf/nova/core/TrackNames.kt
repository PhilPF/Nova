package com.philpf.nova.core

data class ArtistTitle(val artist: String?, val title: String)

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

    /** A file name without its extension, as a title. */
    fun baseName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    /** yt-dlp's default names end in " [videoId]"; that is not part of the title. */
    fun withoutVideoId(name: String): String = name.replace(Regex("""\s*\[[A-Za-z0-9_-]{11}]$"""), "").trim()
}
