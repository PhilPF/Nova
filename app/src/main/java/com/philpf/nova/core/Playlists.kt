package com.philpf.nova.core

/**
 * A playlist the user made. An entry is a whole file or a part of one (a song inside a long
 * video), and keeps its title and artist, so it still reads well if the file's tracklist
 * changes or the file goes missing.
 */
data class Playlist(val id: String, val name: String, val entries: List<Entry>) {
    data class Entry(val fileId: String, val startMs: Long, val endMs: Long?, val title: String, val artist: String?) {
        val mediaId: String get() = MediaIds.of(fileId, startMs, endMs)

        companion object {
            fun of(t: Track) = Entry(t.file.id, t.startMs, t.endMs, t.title, t.artist)
        }
    }
}

object PlaylistCodec {
    fun encode(playlists: List<Playlist>): String = Json.write(
        mapOf(
            "version" to 1,
            "playlists" to playlists.map { p ->
                mapOf(
                    "id" to p.id,
                    "name" to p.name,
                    "entries" to p.entries.map { e ->
                        mapOf("file" to e.fileId, "start" to e.startMs, "end" to e.endMs, "title" to e.title, "artist" to e.artist)
                    },
                )
            },
        ),
    )

    fun decode(text: String): List<Playlist> {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return emptyList()
        return (root["playlists"] as? List<*>).orEmpty().mapNotNull { item ->
            val p = item as? Map<*, *> ?: return@mapNotNull null
            val id = p.string("id") ?: return@mapNotNull null
            Playlist(
                id = id,
                name = p.string("name") ?: "Playlist",
                entries = (p["entries"] as? List<*>).orEmpty().mapNotNull { e ->
                    val m = e as? Map<*, *> ?: return@mapNotNull null
                    Playlist.Entry(
                        fileId = m.string("file") ?: return@mapNotNull null,
                        startMs = m.long("start") ?: 0,
                        endMs = m.long("end"),
                        title = m.string("title") ?: "",
                        artist = m.string("artist"),
                    )
                },
            )
        }
    }
}

object PlaylistOps {
    fun create(all: List<Playlist>, id: String, name: String, entries: List<Playlist.Entry> = emptyList()) =
        all + Playlist(id, uniqueName(all, name), entries)

    fun uniqueName(all: List<Playlist>, name: String): String {
        val base = name.trim().ifEmpty { "Playlist" }
        val taken = all.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var n = 2
        while ("$base $n".lowercase() in taken) n++
        return "$base $n"
    }

    fun rename(all: List<Playlist>, id: String, name: String) =
        all.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }

    fun delete(all: List<Playlist>, id: String) = all.filter { it.id != id }

    /** Adds entries at the end; an entry already in the playlist is not added twice. */
    fun add(all: List<Playlist>, id: String, entries: List<Playlist.Entry>) = all.map { p ->
        if (p.id != id) return@map p
        val have = p.entries.map { it.mediaId }.toMutableSet()
        p.copy(entries = p.entries + entries.filter { have.add(it.mediaId) })
    }

    fun removeAt(all: List<Playlist>, id: String, index: Int) = all.map { p ->
        if (p.id == id && index in p.entries.indices) p.copy(entries = p.entries.toMutableList().apply { removeAt(index) }) else p
    }

    fun move(all: List<Playlist>, id: String, from: Int, to: Int) = all.map { p ->
        if (p.id != id || from !in p.entries.indices || to !in p.entries.indices) return@map p
        val list = p.entries.toMutableList()
        list.add(to, list.removeAt(from))
        p.copy(entries = list)
    }
}
