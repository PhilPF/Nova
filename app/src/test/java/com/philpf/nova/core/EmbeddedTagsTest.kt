package com.philpf.nova.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sample files in test/resources/media were made with ffmpeg 6.1 (as yt-dlp does with
 * --embed-chapters --embed-metadata), from three chapters: "Alpha Band - First Light" at 0,
 * "Beta – Second Wind" at 2 s and "Gamma - Ünïcode Song" at 4.5 s, and a description with a
 * tracklist.
 */
class EmbeddedTagsTest {
    private fun read(name: String): EmbeddedTags {
        val bytes = javaClass.getResourceAsStream("/media/$name")!!.use { it.readBytes() }
        return EmbeddedTagReader.read(ArraySource(bytes))
    }

    private val expected = listOf(
        Chapter(0, "Alpha Band - First Light"),
        Chapter(2000, "Beta – Second Wind"),
        Chapter(4500, "Gamma - Ünïcode Song"),
    )

    private fun assertSample(tags: EmbeddedTags) {
        assertEquals(expected, tags.chapters)
        assertTrue(tags.descriptions.toString(), tags.descriptions.any { it.startsWith("Tracklist:\n0:00 Alpha Band") })
    }

    @Test fun mp4NeroChapters() = assertSample(read("mix.mp4"))

    @Test fun m4aNeroChapters() = assertSample(read("mix.m4a"))

    @Test fun m4aQuickTimeChapterTrack() = assertSample(read("mix-qt.m4a"))

    @Test fun matroska() = assertSample(read("mix.mkv"))

    @Test fun webm() = assertSample(read("mix.webm"))

    @Test fun mp3Id3Chapters() = assertSample(read("mix.mp3"))

    @Test fun opusVorbisComments() {
        val tags = read("mix.opus")
        // ffmpeg 6.1 writes the third start rounded to 5.5 s in Ogg; it is read as written.
        assertEquals(listOf(expected[0], expected[1], Chapter(5500, expected[2].title)), tags.chapters)
        assertTrue(tags.descriptions.first().startsWith("Tracklist:"))
    }

    @Test fun flacHasOnlyTheDescription() {
        val tags = read("mix.flac")
        assertEquals(emptyList<Chapter>(), tags.chapters)
        assertEquals(listOf(Chapter(0, "Alpha Band - First Light"), Chapter(2000, "Beta – Second Wind")), TimestampText.parse(tags.descriptions.first()))
    }

    @Test fun oggCommentPacketSpanningPages() {
        val tags = read("long.opus")
        // Written by ffmpeg as 0, 2.5 and 3 s (its Ogg writer rounds the seconds of 1.5 s up).
        assertEquals(listOf(0L, 2500L, 3000L), tags.chapters.map { it.startMs })
        assertEquals("Three - C", tags.chapters.last().title)
        assertTrue(tags.descriptions.first().length > 100_000)
    }

    @Test fun bufferedSourceReadsLikeTheFile() {
        val bytes = javaClass.getResourceAsStream("/media/mix.mkv")!!.use { it.readBytes() }
        val tags = EmbeddedTagReader.read(BufferedSource(ArraySource(bytes), blockSize = 256))
        assertEquals(expected, tags.chapters)
    }

    @Test fun unknownOrBrokenFilesGiveNothing() {
        assertEquals(EmbeddedTags.EMPTY, EmbeddedTagReader.read(ArraySource(ByteArray(3))))
        assertEquals(EmbeddedTags.EMPTY, EmbeddedTagReader.read(ArraySource("RIFF....WAVEfmt ".toByteArray())))
        val mkv = javaClass.getResourceAsStream("/media/mix.mkv")!!.use { it.readBytes() }
        // Cut short: no crash, whatever is left is read.
        EmbeddedTagReader.read(ArraySource(mkv.copyOf(200)))
        val mp4 = javaClass.getResourceAsStream("/media/mix.mp4")!!.use { it.readBytes() }
        EmbeddedTagReader.read(ArraySource(mp4.copyOf(mp4.size - 50)))
    }

    @Test fun vorbisChapterFields() {
        val tags = VorbisTags.fromFields(
            listOf(
                "CHAPTER001" to "00:00:00.000", "CHAPTER001NAME" to "Intro",
                "CHAPTER002" to "00:01:30.5", "CHAPTER002NAME" to "Next",
                "CHAPTER010" to "01:00:00", "CHAPTER010NAME" to "Late",
                "COMMENT" to "c", "DESCRIPTION" to "d",
            ),
        )
        assertEquals(listOf(Chapter(0, "Intro"), Chapter(90_500, "Next"), Chapter(3_600_000, "Late")), tags.chapters)
        assertEquals(listOf("d", "c"), tags.descriptions)
    }
}
