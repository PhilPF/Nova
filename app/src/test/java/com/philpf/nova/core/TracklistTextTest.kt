package com.philpf.nova.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TracklistTextTest {
    @Test fun parsesTimes() {
        assertEquals(0L, TimestampText.parseTime("0:00"))
        assertEquals(123_000L, TimestampText.parseTime("2:03"))
        assertEquals(3_723_000L, TimestampText.parseTime("1:02:03"))
        assertEquals(3_723_000L, TimestampText.parseTime("62:03"))
        assertEquals(123_500L, TimestampText.parseTime("02:03.5"))
        assertNull(TimestampText.parseTime("2:61"))
        assertNull(TimestampText.parseTime("1:60:00"))
        assertNull(TimestampText.parseTime("abc"))
    }

    @Test fun youtubeDescription() {
        val text = """
            Best of 2024 — a mix 🎧
            Follow me: https://example.com/a:b

            Tracklist:
            00:00 Daft Punk - Something About Us
            03:51 Röyksopp – Eple
            (07:32) Air - La Femme d'Argent
            1. [11:10] Boards of Canada — Roygbiv
            1:02:05 Last Artist - Last Song (Live)

            Thanks for listening! Timestamps for the talk: 2:00 hello 1:00 bye
        """.trimIndent()
        val chapters = TimestampText.parse(text)
        assertEquals(listOf(0L, 231_000L, 452_000L, 670_000L, 3_725_000L), chapters.map { it.startMs })
        assertEquals("Daft Punk - Something About Us", chapters[0].title)
        assertEquals("Röyksopp – Eple", chapters[1].title)
        assertEquals("Air - La Femme d'Argent", chapters[2].title)
        assertEquals("1. Boards of Canada — Roygbiv", chapters[3].title)
        assertEquals(ArtistTitle("Boards of Canada", "Roygbiv"), TrackNames.split(chapters[3].title))
        assertEquals("Last Artist - Last Song (Live)", chapters[4].title)
    }

    @Test fun timeAtTheEndAndRanges() {
        val chapters = TimestampText.parse("Song A - Artist A 0:00\nSong B 4:10 - 8:00\n• Song C | 8:00")
        assertEquals(listOf(Chapter(0, "Song A - Artist A"), Chapter(250_000, "Song B"), Chapter(480_000, "Song C")), chapters)
    }

    @Test fun prefersTheRunFromZero() {
        val text = "Highlights: 5:00 best part\n10:00 other\n\n0:00 One\n2:00 Two\n4:00 Three"
        assertEquals(listOf("One", "Two", "Three"), TimestampText.parse(text).map { it.title })
    }

    @Test fun notATracklist() {
        assertEquals(emptyList<Chapter>(), TimestampText.parse("Just one time 3:00 here"))
        assertEquals(emptyList<Chapter>(), TimestampText.parse("No times at all"))
        assertEquals(emptyList<Chapter>(), TimestampText.parse("Meet at 10:30 and 9:15"))
    }

    @Test fun untitledLinesAreNumbered() {
        assertEquals(listOf("Track 1", "B"), TimestampText.parse("0:00\n1:00 B").map { it.title })
    }

    @Test fun formatReadsBack() {
        val chapters = listOf(Chapter(0, "A - One"), Chapter(61_500, "Two", "B"), Chapter(3_700_000, "Three"))
        val text = TimestampText.format(chapters)
        assertEquals("0:00 A - One\n1:01.500 B - Two\n1:01:40 Three", text)
        assertEquals(listOf(Chapter(0, "A - One"), Chapter(61_500, "B - Two"), Chapter(3_700_000, "Three")), TimestampText.parse(text))
    }

    @Test fun splitsArtistAndTitle() {
        assertEquals(ArtistTitle("Jay-Z", "Empire State of Mind"), TrackNames.split("Jay-Z - Empire State of Mind"))
        assertEquals(ArtistTitle("Sigur Rós", "Hoppípolla"), TrackNames.split("Sigur Rós – Hoppípolla"))
        assertEquals(ArtistTitle("A-ha", "Take On Me"), TrackNames.split("01. A-ha - Take On Me"))
        assertEquals(ArtistTitle("Artist", "Song"), TrackNames.split("\"Artist\" - \"Song\""))
        assertEquals(ArtistTitle(null, "99 Luftballons"), TrackNames.split("99 Luftballons"))
        assertEquals(ArtistTitle(null, "Just a title"), TrackNames.split("Just a title"))
        assertEquals("Song", TrackNames.withoutVideoId("Song [dQw4w9WgXcQ]"))
        assertEquals("a.b", TrackNames.baseName("a.b.mp3"))
    }

    @Test fun cueSheet() {
        val cue = """
            REM GENRE Electronic
            PERFORMER "The Band"
            TITLE "Live at Home"
            FILE "live.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Opening"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Second"
                PERFORMER "Guest"
                INDEX 00 03:58:00
                INDEX 01 04:00:37
        """.trimIndent()
        val sheet = CueSheet.parse(cue)
        assertEquals("Live at Home", sheet.title)
        assertEquals(
            listOf(Chapter(0, "Opening", "The Band"), Chapter(240_493, "Second", "Guest")),
            sheet.chaptersFor("live.flac"),
        )
        assertEquals(sheet.chaptersFor("live.flac"), sheet.chaptersFor("other name.mp3"))
    }

    @Test fun infoJson() {
        val text = """
            {"id": "abcdefghijk", "title": "Night Mix", "fulltitle": "Night Mix (2 hours)",
             "uploader": "Some Channel", "channel": "Some Channel", "duration": 7200.5,
             "description": "0:00 A - B\n1:00 C - D", "webpage_url": "https://www.youtube.com/watch?v=abcdefghijk",
             "chapters": [{"start_time": 0.0, "title": "A - B", "end_time": 60.0},
                          {"start_time": 60.0, "title": "C - D", "end_time": 7200.5}],
             "formats": [{"url": "x", "tbr": 1.5e2, "ok": true, "none": null}]}
        """.trimIndent()
        val info = InfoJson.parse(text)!!
        assertEquals("Night Mix (2 hours)", info.title)
        assertEquals("Some Channel", info.artist)
        assertEquals(7_200_500L, info.durationMs)
        assertEquals(listOf(Chapter(0, "A - B"), Chapter(60_000, "C - D")), info.chapters)
        assertNull(InfoJson.parse("not json"))
    }

    @Test fun jsonRoundTrip() {
        val value = mapOf("a" to listOf(1L, 2.5, "x\"\n\u0001é😀", null, true), "b" to emptyMap<String, Any>())
        assertEquals(value, Json.parse(Json.write(value)))
        assertEquals("\u00e9", Json.parse("\"\\u00e9\""))
    }
}
