package com.philpf.nova.core

/**
 * Everything known about a file's songs, from wherever it can be read. Sources found next to
 * the file share its name: "Mix [id].mp4" with "Mix [id].info.json", "Mix [id].description"
 * (yt-dlp's --write-description) or "Mix [id].cue".
 */
data class ChapterSources(
    val manual: List<Chapter>? = null,
    val embedded: EmbeddedTags = EmbeddedTags.EMPTY,
    val info: VideoInfo? = null,
    val cue: CueSheet? = null,
    val fileName: String? = null,
    val descriptionFile: String? = null,
)

object ChapterResolver {
    /**
     * The file's chapters and where they came from, the first source that has at least two:
     * a tracklist entered in the app, chapters in the file, yt-dlp's info file, a CUE sheet,
     * then timestamps in a description (a .description file, the info file's, the file's own).
     */
    fun resolve(s: ChapterSources, durationMs: Long): Pair<List<Chapter>, ChapterOrigin> {
        fun ok(list: List<Chapter>?) = list?.let { Chapters.normalize(it, durationMs) }?.takeIf { it.isNotEmpty() }

        s.manual?.let { manual ->
            // An empty tracklist entered in the app turns the file's chapters off.
            return (ok(manual) ?: emptyList()) to ChapterOrigin.MANUAL
        }
        ok(s.embedded.chapters)?.let { return it to ChapterOrigin.EMBEDDED }
        ok(s.info?.chapters)?.let { return it to ChapterOrigin.INFO_JSON }
        ok(s.cue?.chaptersFor(s.fileName))?.let { return it to ChapterOrigin.CUE }
        val texts = listOfNotNull(s.descriptionFile, s.info?.description) + s.embedded.descriptions
        for (text in texts) ok(TimestampText.parse(text))?.let { return it to ChapterOrigin.DESCRIPTION }
        return emptyList<Chapter>() to ChapterOrigin.NONE
    }
}
