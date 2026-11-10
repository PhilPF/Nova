package com.philpf.nova.core

/**
 * What a media file says about itself that Android's MediaMetadataRetriever does not read:
 * its chapters, and descriptive texts (description, comment) that may hold a tracklist.
 * yt-dlp writes both with --embed-chapters and --embed-metadata.
 */
data class EmbeddedTags(val chapters: List<Chapter> = emptyList(), val descriptions: List<String> = emptyList()) {
    companion object {
        val EMPTY = EmbeddedTags()
    }
}

object EmbeddedTagReader {
    /** Reads [source] by its first bytes, whatever its name says. Never throws. */
    fun read(source: ByteSource): EmbeddedTags {
        val src = if (source is BufferedSource || source is ArraySource) source else BufferedSource(source)
        val head = src.bytes(0, 12)
        if (head.size < 8) return EmbeddedTags.EMPTY
        return runCatching {
            when {
                head.ascii(4, 4) == "ftyp" -> Mp4Tags.read(src)
                head.u32be(0) == 0x1A45DFA3L -> MatroskaTags.read(src)
                head.ascii(0, 3) == "ID3" -> Id3Tags.read(src)
                head.ascii(0, 4) == "fLaC" -> VorbisTags.readFlac(src)
                head.ascii(0, 4) == "OggS" -> VorbisTags.readOgg(src)
                else -> EmbeddedTags.EMPTY
            }
        }.getOrDefault(EmbeddedTags.EMPTY)
    }
}

/** Text in an ID3 or MP4 field: UTF-8 unless it starts with a UTF-16 byte-order mark. */
internal fun decodeText(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): String {
    if (to - from >= 2) {
        val a = bytes.u8(from)
        val b = bytes.u8(from + 1)
        if (a == 0xFE && b == 0xFF) return String(bytes, from + 2, to - from - 2, Charsets.UTF_16BE)
        if (a == 0xFF && b == 0xFE) return String(bytes, from + 2, to - from - 2, Charsets.UTF_16LE)
    }
    return String(bytes, from, to - from, Charsets.UTF_8)
}

/**
 * MP4 and M4A (QuickTime's format, ISO base media file format). Chapters are in a Nero "chpl"
 * box (moov/udta/chpl), which ffmpeg writes, or in a QuickTime chapter track: a text track
 * that another track points to with a "chap" reference, a sample per chapter. The description
 * is an iTunes-style tag (moov/udta/meta/ilst: "desc", "ldes" or "©cmt").
 */
internal object Mp4Tags {
    private class Box(val type: String, val start: Long, val dataStart: Long, val end: Long)

    private fun boxes(src: ByteSource, from: Long, to: Long): List<Box> {
        val list = mutableListOf<Box>()
        var at = from
        while (at + 8 <= to) {
            val h = src.bytes(at, 16)
            if (h.size < 8) break
            var size = h.u32be(0)
            val type = h.ascii(4, 4)
            var header = 8
            if (size == 1L) {
                if (h.size < 16) break
                size = h.u64be(8)
                header = 16
            } else if (size == 0L) {
                size = to - at
            }
            if (size < header) break
            val end = minOf(at + size, to)
            list += Box(type, at, at + header, end)
            at += size
        }
        return list
    }

    private fun child(src: ByteSource, box: Box, type: String, skip: Int = 0): Box? =
        boxes(src, box.dataStart + skip, box.end).firstOrNull { it.type == type }

    private fun data(src: ByteSource, box: Box, limit: Int = 1 shl 20): ByteArray =
        src.bytes(box.dataStart, minOf(box.end - box.dataStart, limit.toLong()).toInt())

    fun read(src: ByteSource): EmbeddedTags {
        val moov = boxes(src, 0, src.size).firstOrNull { it.type == "moov" } ?: return EmbeddedTags.EMPTY
        val children = boxes(src, moov.dataStart, moov.end)
        val udta = children.firstOrNull { it.type == "udta" }
        val descriptions = udta?.let { descriptions(src, it) }.orEmpty()
        val nero = udta?.let { child(src, it, "chpl") }?.let { chpl(data(src, it)) }.orEmpty()
        val chapters = nero.ifEmpty { chapterTrack(src, children.filter { it.type == "trak" }) }
        return EmbeddedTags(chapters, descriptions)
    }

    private fun chpl(d: ByteArray): List<Chapter> {
        if (d.size < 5) return emptyList()
        val version = d.u8(0)
        var at = if (version == 1) 8 else 4
        if (at >= d.size) return emptyList()
        val count = d.u8(at++)
        val out = mutableListOf<Chapter>()
        repeat(count) {
            if (at + 9 > d.size) return out
            val start = d.u64be(at) / 10_000 // 100 ns units
            val len = d.u8(at + 8)
            at += 9
            if (at + len > d.size) return out
            out += Chapter(start, String(d, at, len, Charsets.UTF_8))
            at += len
        }
        return out
    }

    private fun descriptions(src: ByteSource, udta: Box): List<String> {
        val meta = child(src, udta, "meta") ?: return emptyList()
        // "meta" is a full box (4 bytes of version and flags) in MP4, a plain one in QuickTime.
        val ilst = child(src, meta, "ilst", skip = 4) ?: child(src, meta, "ilst") ?: return emptyList()
        val wanted = listOf("desc", "ldes", "©cmt")
        val out = mutableListOf<String>()
        for (type in wanted) {
            val item = boxes(src, ilst.dataStart, ilst.end).firstOrNull { it.type == type } ?: continue
            val dataBox = child(src, item, "data") ?: continue
            val d = data(src, dataBox)
            if (d.size > 8) out += decodeText(d, 8).trimEnd('\u0000')
        }
        return out
    }

    /** The text track that another track names with a "chap" reference. */
    private fun chapterTrack(src: ByteSource, traks: List<Box>): List<Chapter> {
        val ids = traks.flatMap { trak ->
            val tref = child(src, trak, "tref") ?: return@flatMap emptyList()
            val chap = child(src, tref, "chap") ?: return@flatMap emptyList()
            val d = data(src, chap)
            (0 until d.size / 4).map { d.u32be(it * 4) }
        }.toSet()
        if (ids.isEmpty()) return emptyList()
        val trak = traks.firstOrNull { trackId(src, it) in ids } ?: return emptyList()
        val mdia = child(src, trak, "mdia") ?: return emptyList()
        val mdhd = child(src, mdia, "mdhd")?.let { data(src, it) } ?: return emptyList()
        val timescale = if (mdhd.u8(0) == 1) mdhd.u32be(20) else mdhd.u32be(12)
        if (timescale <= 0) return emptyList()
        val stbl = child(src, mdia, "minf")?.let { child(src, it, "stbl") } ?: return emptyList()
        val table = boxes(src, stbl.dataStart, stbl.end).associateBy { it.type }

        // Start times from the sample durations (stts).
        val stts = table["stts"]?.let { data(src, it) } ?: return emptyList()
        val starts = mutableListOf<Long>()
        var t = 0L
        for (e in 0 until stts.u32be(4).toInt()) {
            val at = 8 + e * 8
            if (at + 8 > stts.size) break
            repeat(stts.u32be(at).toInt().coerceAtMost(10_000)) {
                starts += t
                t += stts.u32be(at + 4)
            }
        }

        // Sizes (stsz), and offsets from chunks (stco or co64) and samples per chunk (stsc).
        val stsz = table["stsz"]?.let { data(src, it) } ?: return emptyList()
        val fixed = stsz.u32be(4)
        val count = stsz.u32be(8).toInt().coerceAtMost(starts.size)
        val sizes = List(count) { if (fixed != 0L) fixed else stsz.u32be(12 + it * 4) }
        val chunkOffsets: List<Long> = table["stco"]?.let { b ->
            val d = data(src, b)
            List(d.u32be(4).toInt()) { d.u32be(8 + it * 4) }
        } ?: table["co64"]?.let { b ->
            val d = data(src, b)
            List(d.u32be(4).toInt()) { d.u64be(8 + it * 8) }
        } ?: return emptyList()
        val stsc = table["stsc"]?.let { data(src, it) } ?: return emptyList()
        val runs = List(stsc.u32be(4).toInt()) { Pair(stsc.u32be(8 + it * 12).toInt(), stsc.u32be(12 + it * 12).toInt()) }

        val offsets = mutableListOf<Long>()
        var sample = 0
        for (chunk in 1..chunkOffsets.size) {
            val perChunk = runs.lastOrNull { it.first <= chunk }?.second ?: 1
            var at = chunkOffsets[chunk - 1]
            repeat(perChunk) {
                if (sample < count) {
                    offsets += at
                    at += sizes[sample]
                    sample++
                }
            }
        }

        return offsets.indices.map { i ->
            val d = src.bytes(offsets[i], sizes[i].toInt().coerceAtMost(4096))
            val len = if (d.size >= 2) d.u16be(0).coerceAtMost(d.size - 2) else 0
            Chapter(starts[i] * 1000 / timescale, if (len > 0) decodeText(d, 2, 2 + len) else "")
        }
    }

    private fun trackId(src: ByteSource, trak: Box): Long {
        val tkhd = child(src, trak, "tkhd")?.let { data(src, it) } ?: return -1
        return if (tkhd.u8(0) == 1) tkhd.u32be(20) else tkhd.u32be(12)
    }
}

/**
 * Matroska and WebM (EBML). Chapters are in Segment/Chapters (the default edition's atoms,
 * start in nanoseconds), the description in Segment/Tags as a DESCRIPTION, SYNOPSIS or COMMENT
 * tag. The SeekHead says where those are; otherwise the top level is walked.
 */
internal object MatroskaTags {
    private const val SEGMENT = 0x18538067L
    private const val SEEK_HEAD = 0x114D9B74L
    private const val SEEK = 0x4DBBL
    private const val SEEK_ID = 0x53ABL
    private const val SEEK_POSITION = 0x53ACL
    private const val CHAPTERS = 0x1043A770L
    private const val EDITION = 0x45B9L
    private const val EDITION_DEFAULT = 0x45DBL
    private const val EDITION_HIDDEN = 0x45BDL
    private const val ATOM = 0xB6L
    private const val TIME_START = 0x91L
    private const val ATOM_HIDDEN = 0x98L
    private const val ATOM_ENABLED = 0x4598L
    private const val DISPLAY = 0x80L
    private const val CHAP_STRING = 0x85L
    private const val TAGS = 0x1254C367L
    private const val TAG = 0x7373L
    private const val TARGETS = 0x63C0L
    private const val TAG_TRACK = 0x63C5L
    private const val SIMPLE_TAG = 0x67C8L
    private const val TAG_NAME = 0x45A3L
    private const val TAG_STRING = 0x4487L
    private const val CLUSTER = 0x1F43B675L

    private class Element(val id: Long, val dataStart: Long, val size: Long) {
        val end: Long get() = if (size < 0) Long.MAX_VALUE else dataStart + size
    }

    /** An element header at [at], or null. An unknown size is -1. */
    private fun header(src: ByteSource, at: Long): Element? {
        val h = src.bytes(at, 12)
        if (h.isEmpty()) return null
        val idLen = vintLength(h.u8(0))
        if (idLen == 0 || idLen > 4 || h.size < idLen + 1) return null
        var id = 0L
        for (i in 0 until idLen) id = (id shl 8) or h.u8(i).toLong()
        val sizeLen = vintLength(h.u8(idLen))
        if (sizeLen == 0 || h.size < idLen + sizeLen) return null
        var size = (h.u8(idLen) and (0xFF shr sizeLen)).toLong()
        var allOnes = size == (0xFF shr sizeLen).toLong()
        for (i in 1 until sizeLen) {
            val b = h.u8(idLen + i)
            if (b != 0xFF) allOnes = false
            size = (size shl 8) or b.toLong()
        }
        return Element(id, at + idLen + sizeLen, if (allOnes) -1 else size)
    }

    private fun vintLength(first: Int): Int {
        for (i in 0 until 8) if (first and (0x80 shr i) != 0) return i + 1
        return 0
    }

    private fun children(src: ByteSource, parent: Element, limitEnd: Long = src.size): List<Element> {
        val out = mutableListOf<Element>()
        val end = minOf(parent.end, limitEnd)
        var at = parent.dataStart
        while (at < end) {
            val e = header(src, at) ?: break
            out += e
            if (e.size < 0) break
            at = e.dataStart + e.size
        }
        return out
    }

    private fun uint(src: ByteSource, e: Element): Long {
        val d = src.bytes(e.dataStart, e.size.toInt().coerceIn(0, 8))
        var v = 0L
        for (b in d) v = (v shl 8) or (b.toLong() and 0xFF)
        return v
    }

    private fun text(src: ByteSource, e: Element): String =
        String(src.bytes(e.dataStart, e.size.toInt().coerceIn(0, 1 shl 20)), Charsets.UTF_8).trimEnd('\u0000')

    fun read(src: ByteSource): EmbeddedTags {
        val ebml = header(src, 0) ?: return EmbeddedTags.EMPTY
        val segment = header(src, ebml.end) ?: return EmbeddedTags.EMPTY
        if (segment.id != SEGMENT) return EmbeddedTags.EMPTY

        // Positions of Chapters and Tags: from the SeekHead, else by walking the top level.
        val found = mutableMapOf<Long, Long>()
        var at = segment.dataStart
        val segmentEnd = minOf(segment.end, src.size)
        var steps = 0
        while (at < segmentEnd && steps++ < 4096) {
            val e = header(src, at) ?: break
            when (e.id) {
                SEEK_HEAD -> for (seek in children(src, e).filter { it.id == SEEK }) {
                    val parts = children(src, seek)
                    val idEl = parts.firstOrNull { it.id == SEEK_ID } ?: continue
                    val posEl = parts.firstOrNull { it.id == SEEK_POSITION } ?: continue
                    val id = uint(src, idEl)
                    if (id == CHAPTERS || id == TAGS) found.putIfAbsent(id, segment.dataStart + uint(src, posEl))
                }
                CHAPTERS, TAGS -> found.putIfAbsent(e.id, at)
            }
            if (e.size < 0) break
            // Once both are known, or at the first cluster when the SeekHead told us where they are.
            if (found.size == 2 || (e.id == CLUSTER && found.isNotEmpty())) break
            at = e.dataStart + e.size
        }

        val chapters = found[CHAPTERS]?.let { header(src, it) }?.takeIf { it.id == CHAPTERS }?.let { chapters(src, it) }.orEmpty()
        val descriptions = found[TAGS]?.let { header(src, it) }?.takeIf { it.id == TAGS }?.let { descriptions(src, it) }.orEmpty()
        return EmbeddedTags(chapters, descriptions)
    }

    private fun chapters(src: ByteSource, chaptersEl: Element): List<Chapter> {
        val editions = children(src, chaptersEl).filter { it.id == EDITION }.map { children(src, it) }
        val visible = editions.filter { parts -> parts.none { it.id == EDITION_HIDDEN && uint(src, it) == 1L } }
        val edition = visible.firstOrNull { parts -> parts.any { it.id == EDITION_DEFAULT && uint(src, it) == 1L } }
            ?: visible.firstOrNull() ?: return emptyList()
        return edition.filter { it.id == ATOM }.mapNotNull { atom ->
            val parts = children(src, atom)
            if (parts.any { it.id == ATOM_HIDDEN && uint(src, it) == 1L }) return@mapNotNull null
            if (parts.any { it.id == ATOM_ENABLED && uint(src, it) == 0L }) return@mapNotNull null
            val start = parts.firstOrNull { it.id == TIME_START }?.let { uint(src, it) } ?: return@mapNotNull null
            val title = parts.firstOrNull { it.id == DISPLAY }
                ?.let { d -> children(src, d).firstOrNull { it.id == CHAP_STRING } }
                ?.let { text(src, it) }
                .orEmpty()
            Chapter(start / 1_000_000, title)
        }
    }

    private fun descriptions(src: ByteSource, tagsEl: Element): List<String> {
        val out = mutableListOf<Pair<Int, String>>()
        val order = listOf("DESCRIPTION", "SYNOPSIS", "COMMENT")
        for (tag in children(src, tagsEl).filter { it.id == TAG }) {
            val parts = children(src, tag)
            val targets = parts.firstOrNull { it.id == TARGETS }
            // Tags of the whole file only, not of one track.
            if (targets != null && children(src, targets).any { it.id == TAG_TRACK }) continue
            for (simple in parts.filter { it.id == SIMPLE_TAG }) {
                val fields = children(src, simple)
                val name = fields.firstOrNull { it.id == TAG_NAME }?.let { text(src, it) }?.uppercase() ?: continue
                val rank = order.indexOf(name)
                if (rank < 0) continue
                val value = fields.firstOrNull { it.id == TAG_STRING }?.let { text(src, it) } ?: continue
                out += rank to value
            }
        }
        return out.sortedBy { it.first }.map { it.second }
    }
}

/**
 * ID3v2.3 and 2.4 tags, at the start of MP3 files. Chapters are CHAP frames (start in
 * milliseconds, title in a TIT2 sub-frame); the description a TXXX frame named "description"
 * (as ffmpeg writes it) or a COMM frame.
 */
internal object Id3Tags {
    fun read(src: ByteSource): EmbeddedTags {
        val h = src.bytes(0, 10)
        if (h.size < 10) return EmbeddedTags.EMPTY
        val major = h.u8(3)
        if (major != 3 && major != 4) return EmbeddedTags.EMPTY
        val flags = h.u8(5)
        val size = synchsafe(h, 6)
        val tag = src.bytes(10, size.coerceAtMost(64 shl 20))
        var at = 0
        if (flags and 0x40 != 0 && tag.size >= 4) {
            at = if (major == 4) synchsafe(tag, 0) else (tag.u32be(0).toInt() + 4)
        }
        val chapters = mutableListOf<Chapter>()
        val texts = mutableListOf<Pair<Int, String>>()
        frames(tag, at, tag.size, major) { id, from, to ->
            when (id) {
                "CHAP" -> chap(tag, from, to, major)?.let { chapters += it }
                "TXXX" -> {
                    val enc = tag.u8(from)
                    val (desc, valueAt) = terminated(tag, from + 1, to, enc)
                    val rank = listOf("description", "synopsis", "comment").indexOf(desc.lowercase())
                    if (rank >= 0) texts += rank to decode(tag, valueAt, to, enc).trimEnd('\u0000')
                }
                "COMM" -> {
                    val enc = tag.u8(from)
                    val (_, valueAt) = terminated(tag, from + 4, to, enc)
                    texts += 3 to decode(tag, valueAt, to, enc).trimEnd('\u0000')
                }
            }
        }
        return EmbeddedTags(chapters, texts.sortedBy { it.first }.map { it.second })
    }

    private fun synchsafe(b: ByteArray, i: Int): Int =
        ((b.u8(i) and 0x7F) shl 21) or ((b.u8(i + 1) and 0x7F) shl 14) or ((b.u8(i + 2) and 0x7F) shl 7) or (b.u8(i + 3) and 0x7F)

    private inline fun frames(b: ByteArray, from: Int, to: Int, major: Int, onFrame: (String, Int, Int) -> Unit) {
        var at = from
        while (at + 10 <= to) {
            if (b.u8(at) == 0) break // padding
            val id = b.ascii(at, 4)
            val size = if (major == 4) synchsafe(b, at + 4) else b.u32be(at + 4).toInt()
            if (size < 0 || at + 10 + size > to) break
            onFrame(id, at + 10, at + 10 + size)
            at += 10 + size
        }
    }

    private fun chap(b: ByteArray, from: Int, to: Int, major: Int): Chapter? {
        var at = from
        while (at < to && b[at] != 0.toByte()) at++
        at++ // the element id and its terminator
        if (at + 16 > to) return null
        val start = b.u32be(at)
        at += 16
        var title = ""
        frames(b, at, to, major) { id, f, t ->
            if (id == "TIT2" && t > f) title = decode(b, f + 1, t, b.u8(f)).trimEnd('\u0000')
        }
        return Chapter(start, title)
    }

    private fun charset(enc: Int) = when (enc) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }

    private fun decode(b: ByteArray, from: Int, to: Int, enc: Int): String =
        if (to <= from) "" else String(b, from, to - from, charset(enc)).substringBefore('\u0000')

    /** A terminated string at [from] and where what follows starts. */
    private fun terminated(b: ByteArray, from: Int, to: Int, enc: Int): Pair<String, Int> {
        val wide = enc == 1 || enc == 2
        var at = from
        if (wide) {
            while (at + 1 < to && !(b[at] == 0.toByte() && b[at + 1] == 0.toByte())) at += 2
            return decode(b, from, at, enc) to minOf(at + 2, to)
        }
        while (at < to && b[at] != 0.toByte()) at++
        return decode(b, from, at, enc) to minOf(at + 1, to)
    }
}

/**
 * Vorbis comments, the tags of FLAC, Opus and Ogg Vorbis files. Chapters follow the
 * "CHAPTERxxx=HH:MM:SS.mmm" and "CHAPTERxxxNAME=title" convention; the description is a
 * DESCRIPTION or COMMENT field.
 */
internal object VorbisTags {
    private const val MAX_COMMENTS = 64 shl 20

    fun readFlac(src: ByteSource): EmbeddedTags {
        var at = 4L
        while (at + 4 <= src.size) {
            val h = src.bytes(at, 4)
            if (h.size < 4) break
            val last = h.u8(0) and 0x80 != 0
            val type = h.u8(0) and 0x7F
            val length = (h.u8(1) shl 16) or (h.u8(2) shl 8) or h.u8(3)
            if (type == 4) return fromComments(src.bytes(at + 4, length), 0)
            if (last) break
            at += 4 + length
        }
        return EmbeddedTags.EMPTY
    }

    /** The second packet of the first stream is its comment header, in Opus and Vorbis alike. */
    fun readOgg(src: ByteSource): EmbeddedTags {
        var at = 0L
        var serial = -1L
        var packet = 0
        val current = java.io.ByteArrayOutputStream()
        while (at + 27 <= src.size) {
            val h = src.bytes(at, 27)
            if (h.ascii(0, 4) != "OggS") break
            val pageSerial = h.u32le(14)
            val segments = h.u8(26)
            val table = src.bytes(at + 27, segments)
            var body = at + 27 + segments
            if (serial < 0) serial = pageSerial
            for (lace in table) {
                val n = lace.toInt() and 0xFF
                if (pageSerial == serial && packet == 1) {
                    if (current.size() + n > MAX_COMMENTS) return EmbeddedTags.EMPTY
                    current.write(src.bytes(body, n))
                }
                body += n
                if (pageSerial == serial && n < 255) {
                    if (packet == 1) return comments(current.toByteArray())
                    packet++
                }
            }
            at = body
        }
        return EmbeddedTags.EMPTY
    }

    private fun comments(p: ByteArray): EmbeddedTags = when {
        p.size > 8 && p.ascii(0, 8) == "OpusTags" -> fromComments(p, 8)
        p.size > 7 && p.u8(0) == 3 && p.ascii(1, 6) == "vorbis" -> fromComments(p, 7)
        else -> EmbeddedTags.EMPTY
    }

    private fun fromComments(d: ByteArray, from: Int): EmbeddedTags {
        var at = from
        if (at + 4 > d.size) return EmbeddedTags.EMPTY
        at += 4 + d.u32le(at).toInt() // vendor
        if (at + 4 > d.size) return EmbeddedTags.EMPTY
        val count = d.u32le(at)
        at += 4
        val fields = mutableListOf<Pair<String, String>>()
        for (i in 0 until count) {
            if (at + 4 > d.size) break
            val len = d.u32le(at).toInt()
            at += 4
            if (len < 0 || at + len > d.size) break
            val text = String(d, at, len, Charsets.UTF_8)
            at += len
            val eq = text.indexOf('=')
            if (eq > 0) fields += text.substring(0, eq).uppercase() to text.substring(eq + 1)
        }
        return fromFields(fields)
    }

    private val chapterKey = Regex("""CHAPTER(\d{1,4})""")
    private val chapterTime = Regex("""(\d+):(\d{1,2}):(\d{1,2})(?:\.(\d{1,9}))?""")

    fun fromFields(fields: List<Pair<String, String>>): EmbeddedTags {
        val starts = mutableMapOf<Int, Long>()
        val names = mutableMapOf<Int, String>()
        for ((key, value) in fields) {
            if (key.endsWith("NAME")) {
                chapterKey.matchEntire(key.removeSuffix("NAME"))?.let { names[it.groupValues[1].toInt()] = value }
            } else {
                val n = chapterKey.matchEntire(key)?.groupValues?.get(1)?.toInt() ?: continue
                val m = chapterTime.matchEntire(value.trim()) ?: continue
                val (h, min, s, frac) = m.destructured
                val ms = if (frac.isEmpty()) 0 else frac.padEnd(3, '0').take(3).toLong()
                starts[n] = ((h.toLong() * 60 + min.toLong()) * 60 + s.toLong()) * 1000 + ms
            }
        }
        val chapters = starts.keys.sorted().map { Chapter(starts.getValue(it), names[it].orEmpty()) }
        val order = listOf("DESCRIPTION", "SYNOPSIS", "COMMENT")
        val descriptions = fields.filter { it.first in order }.sortedBy { order.indexOf(it.first) }.map { it.second }
        return EmbeddedTags(chapters, descriptions)
    }
}
