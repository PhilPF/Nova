package com.philpf.nova.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private fun file(
        id: String,
        title: String,
        artist: String? = null,
        album: String? = null,
        track: Int? = null,
        duration: Long = 200_000,
        chapters: List<Chapter> = emptyList(),
        video: Boolean = false,
    ) = MediaFile(id, "$id.mp3", "", title, artist, album, null, track, null, null, duration, video, chapters)

    private val mix = file(
        "mix", "Night Mix", "Some Channel", duration = 600_000, video = true,
        chapters = listOf(Chapter(0, "Alpha - First"), Chapter(200_000, "Beta - Second"), Chapter(400_000, "Untitled bit")),
    )
    private val library = Library(
        listOf(
            file("b2", "Second", "Beta", "Beta Album", 2),
            file("b1", "First", "beta", "Beta Album", 1),
            file("s", "Single", "Alpha"),
            mix,
        ),
    )

    @Test fun albumsInTrackOrder() {
        val album = library.albums.single()
        assertEquals("Beta Album", album.title)
        assertEquals(listOf("First", "Second"), album.tracks.map { it.title })
    }

    @Test fun artistsIncludeSongsInsideMixes() {
        val names = library.artists.map { it.name }
        // Spellings of a name are one artist, named as most of their songs name them.
        assertEquals(listOf("Alpha", "Beta", "Some Channel"), names)
        val alpha = library.artists.first { it.name == "Alpha" }
        assertEquals(setOf("Single", "First"), alpha.tracks.map { it.title }.toSet())
        // The chapter without an artist is credited to the video's channel, and so is the video.
        val channel = library.artists.first { it.name == "Some Channel" }
        assertEquals(listOf("Untitled bit", "Night Mix"), channel.tracks.sortedBy { it.chapterIndex < 0 }.map { it.title })
    }

    @Test fun chapterTracksAreClips() {
        val t = mix.chapterTrack(1)
        assertEquals("Second", t.title)
        assertEquals("Beta", t.artist)
        assertEquals("Night Mix", t.album)
        assertEquals(200_000L, t.startMs)
        assertEquals(400_000L, t.endMs)
        assertNull(mix.chapterTrack(2).endMs)
        assertEquals(t, library.track(t.mediaId))
        assertEquals(mix.wholeTrack, library.track("mix"))
        assertEquals(1, library.mixes.size)
    }

    @Test fun mediaIds() {
        assertEquals("content://a/b", MediaIds.of("content://a/b", 0, null))
        val id = MediaIds.of("content://x/y:z", 1000, 5000)
        assertEquals(MediaIds.Parsed("content://x/y:z", 1000, 5000), MediaIds.parse(id))
        assertEquals(MediaIds.Parsed("f", 10, null), MediaIds.parse(MediaIds.of("f", 10, null)))
        // A clip that matches no chapter still plays, under the file's name.
        assertEquals(123L, library.track(MediaIds.of("mix", 123, null))!!.startMs)
    }

    @Test fun search() {
        assertEquals(listOf("First", "Second"), library.search("beta").map { it.title }.distinct().sorted())
        assertTrue(library.search("alpha").any { it.chapterIndex == 0 })
    }

    @Test fun chapterNavigation() {
        val c = listOf(Chapter(0, "a"), Chapter(10_000, "b"), Chapter(20_000, "c"))
        assertEquals(10_000L, ChapterNav.next(c, 0))
        assertEquals(20_000L, ChapterNav.next(c, 15_000))
        assertNull(ChapterNav.next(c, 25_000))
        assertEquals(10_000L, ChapterNav.previous(c, 15_000)) // more than 3 s in: its start
        assertEquals(0L, ChapterNav.previous(c, 11_000)) // just started: the one before
        assertNull(ChapterNav.previous(c, 2_000)) // first chapter: the previous item
        assertEquals(0L, ChapterNav.previous(c, 5_000))
        val late = listOf(Chapter(5_000, "a"), Chapter(9_000, "b"))
        assertEquals(5_000L, ChapterNav.next(late, 1_000))
        assertNull(ChapterNav.previous(late, 1_000))
        assertEquals(-1, Chapters.indexAt(late, 1_000))
        assertEquals(1, Chapters.indexAt(late, 9_000))
    }

    @Test fun normalize() {
        val list = Chapters.normalize(listOf(Chapter(5000, " b "), Chapter(0, ""), Chapter(5000, "dup"), Chapter(99_000, "past end")), 60_000)
        assertEquals(listOf(Chapter(0, "Track 1"), Chapter(5000, "b")), list)
        assertEquals(emptyList<Chapter>(), Chapters.normalize(listOf(Chapter(0, "only")), 0))
        assertEquals("1:01:01", Chapters.formatTime(3_661_000))
        assertEquals("0:09", Chapters.formatTime(9_999))
    }

    @Test fun resolverOrder() {
        val embedded = EmbeddedTags(listOf(Chapter(0, "E1"), Chapter(1000, "E2")), listOf("0:00 D1\n0:01 D2"))
        val info = VideoInfo(null, null, null, null, null, "0:00 I1\n0:01 I2", null, listOf(Chapter(0, "J1"), Chapter(1000, "J2")), null)
        assertEquals(ChapterOrigin.EMBEDDED, ChapterResolver.resolve(ChapterSources(embedded = embedded, info = info), 5000).second)
        assertEquals(ChapterOrigin.INFO_JSON, ChapterResolver.resolve(ChapterSources(info = info), 5000).second)
        val (fromDescription, origin) = ChapterResolver.resolve(ChapterSources(embedded = embedded.copy(chapters = emptyList())), 5000)
        assertEquals(ChapterOrigin.DESCRIPTION, origin)
        assertEquals(listOf("D1", "D2"), fromDescription.map { it.title })
        assertEquals(emptyList<Chapter>() to ChapterOrigin.MANUAL, ChapterResolver.resolve(ChapterSources(manual = emptyList(), embedded = embedded), 5000))
        assertEquals(ChapterOrigin.NONE, ChapterResolver.resolve(ChapterSources(), 5000).second)
    }

    @Test fun libraryCodecRoundTrip() {
        val files = library.files.map { it.copy(art = "/cache/a.jpg", chapterOrigin = if (it.isMix) ChapterOrigin.EMBEDDED else ChapterOrigin.NONE) }
        assertEquals(files, LibraryCodec.decode(LibraryCodec.encode(files)))
        val manual = mapOf("x" to listOf(Chapter(0, "a", "b")), "y" to emptyList())
        assertEquals(manual, ManualChaptersCodec.decode(ManualChaptersCodec.encode(manual)))
    }

    @Test fun playlists() {
        var all = PlaylistOps.create(emptyList(), "p1", "Road")
        all = PlaylistOps.create(all, "p2", "road")
        assertEquals(listOf("Road", "road 2"), all.map { it.name })
        val a = Playlist.Entry.of(library.track("b1")!!)
        val b = Playlist.Entry.of(mix.chapterTrack(1))
        all = PlaylistOps.add(all, "p1", listOf(a, b, a))
        assertEquals(listOf(a, b), all[0].entries)
        all = PlaylistOps.move(all, "p1", 1, 0)
        assertEquals(listOf(b, a), all[0].entries)
        assertEquals(all, PlaylistCodec.decode(PlaylistCodec.encode(all)))
        all = PlaylistOps.removeAt(all, "p1", 0)
        all = PlaylistOps.rename(all, "p1", "Drive")
        all = PlaylistOps.delete(all, "p2")
        assertEquals(listOf(Playlist("p1", "Drive", listOf(a))), all)
        assertEquals(mix.chapterTrack(1), library.track(b.mediaId))
    }

    @Test fun messagingAndHiddenFolders() {
        assertTrue(FolderFiles.isMessaging("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202401", "PTT-20240101-WA0003.opus"))
        assertTrue(FolderFiles.isMessaging("Telegram/Telegram Audio", "song.mp3"))
        assertTrue(FolderFiles.isMessaging("Downloads", "AUD-20231224-WA0010.m4a"))
        assertFalse(FolderFiles.isMessaging("Music/Albums/Signals", "01 Song.mp3"))
        assertFalse(FolderFiles.isMessaging("Music/Luma - Glass Harbor", "01 Glass Harbor.mp3"))
        assertTrue(FolderFiles.isHidden("Recordings/2024", listOf("Recordings")))
        assertTrue(FolderFiles.isHidden("recordings", listOf("Recordings")))
        assertFalse(FolderFiles.isHidden("Recordings Old", listOf("Recordings")))
        assertFalse(FolderFiles.isHidden("", listOf("")))
    }

    @Test fun folderFiles() {
        assertEquals(FolderFiles.Kind.VIDEO, FolderFiles.kind("a.WEBM", null))
        assertEquals(FolderFiles.Kind.AUDIO, FolderFiles.kind("a.opus", null))
        assertEquals(FolderFiles.Kind.AUDIO, FolderFiles.kind("noext", "audio/mpeg"))
        assertNull(FolderFiles.kind("a.txt", "text/plain"))
        val names = listOf("Mix [abcdefghijk].mkv", "Mix [abcdefghijk].info.json", "Mix [abcdefghijk].webp", "Cover.JPG", "x.cue")
        assertEquals("Mix [abcdefghijk].info.json", FolderFiles.sidecar(names[0], names, ".info.json"))
        assertNull(FolderFiles.sidecar(names[0], names, ".cue"))
        assertEquals("Cover.JPG", FolderFiles.cover(names))
        assertEquals("Mix [abcdefghijk].webp", FolderFiles.thumbnail(names[0], names))
    }
}

class ArtColorsTest {
    @Test fun dominantColours() {
        val red = 0xFFE01010.toInt()
        val blue = 0xFF1030E0.toInt()
        val grey = 0xFF808080.toInt()
        val pixels = IntArray(100) { i -> if (i < 60) red else if (i < 85) blue else grey }
        assertEquals(listOf(red, blue), ArtColors.dominant(pixels))
        assertEquals(emptyList<Int>(), ArtColors.dominant(IntArray(10) { grey }))
    }
}
