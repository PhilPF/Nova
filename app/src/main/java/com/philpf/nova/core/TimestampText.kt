package com.philpf.nova.core

/**
 * Tracklists written as text: a video's description or a comment under it, with one song per
 * line and its start time, as YouTube reads them to make chapters:
 *
 *     0:00 Artist - First song
 *     3:41 Another Artist - Second song
 *     1:02:10 ...
 *
 * The time may be anywhere on the line, in brackets or parentheses, and a line may hold a
 * range ("0:00 - 3:41 Song"), of which the first time is the start.
 */
object TimestampText {
    private val time = Regex("""(?<![\d:.])(?:(\d{1,2}):)?(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?(?![\d:])""")
    private val edges = Regex("""^[\s\-–—|:.,;)\]}>•·*]+|[\s\-–—|:,;(\[{<•·*]+$""")
    private val emptyBrackets = Regex("""[(\[{]\s*[)\]}]""")

    /** Milliseconds in "1:02:03", "62:03", "2:03" or "2:03.5", or null. */
    fun parseTime(text: String): Long? {
        val m = time.matchEntire(text.trim()) ?: return null
        return toMs(m)
    }

    private fun toMs(m: MatchResult): Long? {
        val h = m.groupValues[1].toLongOrNull() ?: 0
        val min = m.groupValues[2].toLong()
        val s = m.groupValues[3].toLong()
        if (s >= 60) return null
        if (m.groupValues[1].isNotEmpty() && min >= 60) return null
        val frac = m.groupValues[4]
        val ms = if (frac.isEmpty()) 0 else frac.padEnd(3, '0').toLong()
        return ((h * 60 + min) * 60 + s) * 1000 + ms
    }

    /**
     * The tracklist in [text], or an empty list when there is none. Lines with a time are read
     * in order; a run of them stops where the times stop increasing. The run that starts at
     * 0:00 is taken when there is one (descriptions often list other times too), else the
     * longest; it needs at least two lines.
     */
    fun parse(text: String): List<Chapter> {
        val entries = text.lineSequence().mapNotNull { line ->
            val m = time.find(line) ?: return@mapNotNull null
            val start = toMs(m) ?: return@mapNotNull null
            var title = time.replace(line, " ")
            title = title.replace(emptyBrackets, " ")
            title = title.replace(Regex("""\s+"""), " ").replace(edges, "").trim()
            Chapter(start, title)
        }.toList()

        val runs = mutableListOf<MutableList<Chapter>>()
        for (entry in entries) {
            val run = runs.lastOrNull()
            if (run != null && entry.startMs > run.last().startMs) run += entry else runs += mutableListOf(entry)
        }
        val candidates = runs.filter { it.size >= 2 }
        val chosen = candidates.firstOrNull { it.first().startMs == 0L } ?: candidates.maxByOrNull { it.size } ?: return emptyList()
        return chosen.mapIndexed { i, c -> if (c.title.isEmpty()) c.copy(title = "Track ${i + 1}") else c }
    }

    /** The tracklist as text that [parse] reads back: one "m:ss Title" line per chapter. */
    fun format(chapters: List<Chapter>): String = chapters.joinToString("\n") { c ->
        val name = if (c.artist != null) "${c.artist} - ${c.title}" else c.title
        val fraction = c.startMs % 1000
        val time = Chapters.formatTime(c.startMs) + if (fraction != 0L) ".%03d".format(fraction) else ""
        "$time $name"
    }
}
