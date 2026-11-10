package com.philpf.nova.core

/** The scanned library as JSON, so the app opens with it and a rescan only reads changed files. */
object LibraryCodec {
    fun encode(files: List<MediaFile>): String = Json.write(
        mapOf(
            "version" to VERSION,
            "files" to files.map { f ->
                mapOf(
                    "id" to f.id, "name" to f.fileName, "folder" to f.folder, "title" to f.title,
                    "artist" to f.artist, "album" to f.album, "albumArtist" to f.albumArtist,
                    "track" to f.trackNumber, "disc" to f.discNumber, "year" to f.year,
                    "duration" to f.durationMs, "video" to f.isVideo, "art" to f.art,
                    "modified" to f.modified, "size" to f.size, "origin" to f.chapterOrigin.name,
                    "chapters" to f.chapters.map { c -> mapOf("start" to c.startMs, "title" to c.title, "artist" to c.artist) },
                )
            },
        ),
    )

    fun decode(text: String): List<MediaFile> {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return emptyList()
        if (root.long("version") != VERSION.toLong()) return emptyList()
        return (root["files"] as? List<*>).orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            MediaFile(
                id = m.string("id") ?: return@mapNotNull null,
                fileName = m.string("name") ?: "",
                folder = (m["folder"] as? String) ?: "",
                title = m.string("title") ?: "",
                artist = m.string("artist"),
                album = m.string("album"),
                albumArtist = m.string("albumArtist"),
                trackNumber = m.long("track")?.toInt(),
                discNumber = m.long("disc")?.toInt(),
                year = m.long("year")?.toInt(),
                durationMs = m.long("duration") ?: 0,
                isVideo = m["video"] == true,
                chapters = (m["chapters"] as? List<*>).orEmpty().mapNotNull { c ->
                    val cm = c as? Map<*, *> ?: return@mapNotNull null
                    Chapter(cm.long("start") ?: return@mapNotNull null, cm.string("title") ?: "", cm.string("artist"))
                },
                chapterOrigin = m.string("origin")?.let { name -> ChapterOrigin.entries.firstOrNull { it.name == name } } ?: ChapterOrigin.NONE,
                art = m.string("art"),
                modified = m.long("modified") ?: 0,
                size = m.long("size") ?: 0,
            )
        }
    }

    /** Raise when what a scan records changes, so older caches are read again from the files. */
    const val VERSION = 1
}

/** Tracklists entered in the app, by file id. An empty list turns a file's chapters off. */
object ManualChaptersCodec {
    fun encode(map: Map<String, List<Chapter>>): String = Json.write(
        map.mapValues { (_, list) -> list.map { mapOf("start" to it.startMs, "title" to it.title, "artist" to it.artist) } },
    )

    fun decode(text: String): Map<String, List<Chapter>> {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return emptyMap()
        return root.entries.mapNotNull { (k, v) ->
            val list = (v as? List<*>)?.mapNotNull { c ->
                val m = c as? Map<*, *> ?: return@mapNotNull null
                Chapter(m.long("start") ?: return@mapNotNull null, m.string("title") ?: "", m.string("artist"))
            } ?: return@mapNotNull null
            (k as? String ?: return@mapNotNull null) to list
        }.toMap()
    }
}

/**
 * Which files in a folder are songs and videos, and which are their companions: yt-dlp's
 * "<name>.info.json" and "<name>.description", a "<name>.cue" (or a CUE sheet naming the file),
 * and a folder picture ("cover.jpg", "folder.png", ...).
 */
object FolderFiles {
    val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "mka", "alac", "aiff", "aif", "amr", "3ga", "weba")
    val videoExtensions = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "avi", "ts")
    private val coverNames = listOf("cover", "folder", "front", "album", "albumart")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    /** Whether it is a song or video, by its name or else by its MIME type. */
    fun kind(name: String, mime: String?): Kind? {
        val ext = extension(name)
        return when {
            ext in videoExtensions -> Kind.VIDEO
            ext in audioExtensions -> Kind.AUDIO
            mime?.startsWith("video/") == true -> Kind.VIDEO
            mime?.startsWith("audio/") == true -> Kind.AUDIO
            else -> null
        }
    }

    enum class Kind { AUDIO, VIDEO }

    /** The sidecar called [suffix] (".info.json", ".description", ".cue") of [mediaName] among [names]. */
    fun sidecar(mediaName: String, names: Collection<String>, suffix: String): String? {
        val base = TrackNames.baseName(mediaName)
        return names.firstOrNull { it.equals(base + suffix, ignoreCase = true) }
            ?: names.firstOrNull { it.equals(mediaName + suffix, ignoreCase = true) }
    }

    /** The folder's picture, by the usual names ("cover.jpg", "folder.png", ...). */
    fun cover(names: Collection<String>): String? {
        val images = names.filter { extension(it) in imageExtensions }
        return coverNames.firstNotNullOfOrNull { c -> images.firstOrNull { TrackNames.baseName(it).equals(c, ignoreCase = true) } }
    }

    /** A picture of the file itself, "<name>.jpg" or ".webp" (yt-dlp's --write-thumbnail). */
    fun thumbnail(mediaName: String, names: Collection<String>): String? {
        val base = TrackNames.baseName(mediaName)
        return names.firstOrNull { extension(it) in imageExtensions && TrackNames.baseName(it).equals(base, ignoreCase = true) }
    }
}
