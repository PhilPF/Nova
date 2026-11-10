package com.philpf.nova.core

/**
 * What yt-dlp knows about a video, from the "<name>.info.json" it writes next to it with
 * --write-info-json. Its "chapters" are YouTube's own: the chapters the uploader set, or those
 * YouTube read from timestamps in the description.
 */
data class VideoInfo(
    val id: String?,
    val title: String?,
    /** The song's artist, when YouTube knows it (music videos), else the channel. */
    val artist: String?,
    val track: String?,
    val album: String?,
    val description: String?,
    val durationMs: Long?,
    val chapters: List<Chapter>,
    val url: String?,
)

object InfoJson {
    fun parse(text: String): VideoInfo? {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
        val chapters = (root["chapters"] as? List<*>).orEmpty().mapNotNull { item ->
            val c = item as? Map<*, *> ?: return@mapNotNull null
            val start = c.number("start_time") ?: return@mapNotNull null
            Chapter((start * 1000).toLong(), c.string("title").orEmpty())
        }
        val artists = root["artists"] as? List<*>
        return VideoInfo(
            id = root.string("id"),
            title = root.string("fulltitle") ?: root.string("title"),
            artist = artists?.filterIsInstance<String>()?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ?: root.string("artist") ?: root.string("creator") ?: root.string("uploader") ?: root.string("channel"),
            track = root.string("track"),
            album = root.string("album"),
            description = root.string("description"),
            durationMs = root.number("duration")?.let { (it * 1000).toLong() },
            chapters = chapters,
            url = root.string("webpage_url"),
        )
    }
}
