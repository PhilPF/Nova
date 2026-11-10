package com.philpf.nova.core

/**
 * A song inside a longer file: where it starts and what it is called. [artist] is set when the
 * source names it apart (a CUE sheet, yt-dlp's metadata); otherwise it is read from [title]
 * ("Artist - Title") by [TrackNames.split].
 */
data class Chapter(val startMs: Long, val title: String, val artist: String? = null)

/** Where a file's chapters came from, strongest first. */
enum class ChapterOrigin { NONE, MANUAL, EMBEDDED, INFO_JSON, CUE, DESCRIPTION }

object Chapters {
    /**
     * Sorted by start, with blank titles named by their position, duplicates of a start time
     * dropped (the first kept) and chapters at or past [durationMs] (when known) removed. A
     * single chapter that covers the whole file says nothing, so it gives an empty list.
     */
    fun normalize(chapters: List<Chapter>, durationMs: Long = 0): List<Chapter> {
        val sorted = chapters
            .filter { it.startMs >= 0 && (durationMs <= 0 || it.startMs < durationMs) }
            .sortedBy { it.startMs }
            .distinctBy { it.startMs }
            .mapIndexed { i, c -> c.copy(title = c.title.trim().ifEmpty { "Track ${i + 1}" }, artist = c.artist?.trim()?.ifEmpty { null }) }
        return if (sorted.size < 2) emptyList() else sorted
    }

    /** The chapter playing at [positionMs]: the last that starts at or before it, or -1 before the first. */
    fun indexAt(chapters: List<Chapter>, positionMs: Long): Int {
        var lo = 0
        var hi = chapters.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (chapters[mid].startMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    /** Where chapter [index] ends: the next one's start, or [durationMs] for the last. */
    fun endOf(chapters: List<Chapter>, index: Int, durationMs: Long): Long =
        if (index + 1 < chapters.size) chapters[index + 1].startMs else durationMs

    /** "1:02:03" or "2:03" for a time in milliseconds (truncated to whole seconds). */
    fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0)) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

/**
 * Where Next and Previous go inside a file that has chapters. Next goes to the next chapter's
 * start, Previous to the start of the current chapter or, within [RESTART_MS] of it, to the
 * previous chapter's. Null means the file has no chapter that way: the player moves to the
 * next (or previous) item of its queue instead.
 */
object ChapterNav {
    const val RESTART_MS = 3_000L

    fun next(chapters: List<Chapter>, positionMs: Long): Long? {
        if (chapters.isEmpty()) return null
        val i = Chapters.indexAt(chapters, positionMs)
        return chapters.getOrNull(i + 1)?.startMs
    }

    fun previous(chapters: List<Chapter>, positionMs: Long): Long? {
        if (chapters.isEmpty()) return null
        val i = Chapters.indexAt(chapters, positionMs)
        if (i < 0) return null
        val start = chapters[i].startMs
        if (positionMs - start > RESTART_MS) return start
        return chapters.getOrNull(i - 1)?.startMs
    }
}
