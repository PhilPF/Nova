package com.philpf.nova.core

/**
 * A CUE sheet: the classic way to describe an album kept as one long file, a track per
 * "TRACK" with its "INDEX 01" start in minutes, seconds and frames (75 a second).
 */
data class CueSheet(val title: String?, val performer: String?, val files: List<CueFile>) {
    data class CueFile(val name: String, val tracks: List<CueTrack>)
    data class CueTrack(val number: Int, val title: String?, val performer: String?, val startMs: Long)

    /** The chapters of the file called [fileName], or of the only file when there is one. */
    fun chaptersFor(fileName: String?): List<Chapter> {
        val file = files.firstOrNull { fileName != null && it.name.substringAfterLast('/').substringAfterLast('\\').equals(fileName, ignoreCase = true) }
            ?: files.singleOrNull()
            ?: return emptyList()
        return file.tracks.map { t ->
            Chapter(t.startMs, t.title ?: "Track ${t.number}", t.performer ?: performer)
        }
    }

    companion object {
        private val index = Regex("""(\d+):(\d{1,2}):(\d{1,2})""")

        fun parse(text: String): CueSheet {
            var title: String? = null
            var performer: String? = null
            val files = mutableListOf<CueFile>()
            var fileName: String? = null
            val tracks = mutableListOf<CueTrack>()
            var number = 0
            var trackTitle: String? = null
            var trackPerformer: String? = null
            var trackStart: Long? = null
            var inTrack = false

            fun endTrack() {
                if (inTrack && trackStart != null) tracks += CueTrack(number, trackTitle, trackPerformer, trackStart!!)
                inTrack = false
                trackTitle = null
                trackPerformer = null
                trackStart = null
            }

            fun endFile() {
                endTrack()
                fileName?.let { files += CueFile(it, tracks.toList()) }
                tracks.clear()
                fileName = null
            }

            for (raw in text.removePrefix("﻿").lineSequence()) {
                val line = raw.trim()
                val command = line.substringBefore(' ').uppercase()
                val rest = line.substringAfter(' ', "").trim()
                when (command) {
                    "FILE" -> {
                        endFile()
                        fileName = if (rest.startsWith('"')) rest.substring(1).substringBefore('"') else rest.substringBeforeLast(' ')
                    }
                    "TRACK" -> {
                        endTrack()
                        inTrack = true
                        number = rest.substringBefore(' ').toIntOrNull() ?: (tracks.size + 1)
                    }
                    "TITLE" -> if (inTrack) trackTitle = unquote(rest) else title = unquote(rest)
                    "PERFORMER" -> if (inTrack) trackPerformer = unquote(rest) else performer = unquote(rest)
                    "INDEX" -> {
                        val parts = rest.split(Regex("""\s+"""))
                        val m = parts.getOrNull(1)?.let { index.matchEntire(it) }
                        if (inTrack && parts.firstOrNull()?.toIntOrNull() == 1 && m != null) {
                            val (mm, ss, ff) = m.destructured
                            trackStart = mm.toLong() * 60_000 + ss.toLong() * 1000 + ff.toLong() * 1000 / 75
                        }
                    }
                }
            }
            endFile()
            return CueSheet(title, performer, files)
        }

        private fun unquote(s: String): String? =
            (if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) s.substring(1, s.length - 1) else s).trim().ifEmpty { null }
    }
}
