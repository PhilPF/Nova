package com.philpf.nova.core

/** What a song in the queue was added as part of. */
enum class SourceKind(val label: String) {
    ALBUM("Album"),
    ARTIST("Artist"),
    PLAYLIST("Playlist"),
    TRACKLIST("Tracklist"),
    LIST("Songs"),
}

/**
 * Where queued songs came from: one album, artist, playlist, video's tracklist or list, as it
 * was added ([id] is new each time, so the same album added twice is two groups).
 */
data class QueueSource(val id: String, val kind: SourceKind, val title: String)

/** A song of the player's queue as the next panel shows it; [index] is its place in the queue. */
data class QueueEntry(val index: Int, val mediaId: String, val title: String, val artist: String?, val source: QueueSource?)

/** Upcoming songs from one source, in play order; [source] is null for a song on its own. */
data class QueueBlock(val source: QueueSource?, val entries: List<QueueEntry>)

object QueueSections {
    /**
     * The songs to come, in the order they will play ([order]: queue indices after the current
     * song, as the player's shuffle and repeat give them), in runs of the same source. A run
     * of one song shows as a song on its own (the panel names its source beside it).
     */
    fun upcoming(entries: List<QueueEntry>, order: List<Int>): List<QueueBlock> {
        val byIndex = entries.associateBy { it.index }
        val blocks = mutableListOf<MutableList<QueueEntry>>()
        for (i in order) {
            val e = byIndex[i] ?: continue
            val last = blocks.lastOrNull()
            val sameSource = e.source != null && last?.last()?.source?.id == e.source.id
            if (sameSource) last!! += e else blocks += mutableListOf(e)
        }
        return blocks.map { run -> QueueBlock(if (run.size >= 2) run.first().source else null, run) }
    }
}
