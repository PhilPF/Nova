package com.philpf.nova.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.philpf.nova.core.Chapters
import com.philpf.nova.core.Library
import com.philpf.nova.core.MediaIds
import com.philpf.nova.core.Track
import com.philpf.nova.core.TrackNames
import android.os.Bundle
import com.philpf.nova.core.QueueSource
import com.philpf.nova.core.SourceKind
import java.io.File

/** Library tracks as the player's items, and back. */
object MediaItems {
    /** What the app sends to the player: an id and what to show; [resolve] adds the rest. */
    fun request(track: Track, source: QueueSource? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.mediaId)
            .setMediaMetadata(withSource(metadata(track.title, track.artist, track.album, track.file.art, track.file.isVideo), source))
            .build()

    private const val SOURCE_ID = "nova.source.id"
    private const val SOURCE_KIND = "nova.source.kind"
    private const val SOURCE_TITLE = "nova.source.title"

    /** [meta] carrying where the song was queued from, in its extras (which travel to the service and back). */
    fun withSource(meta: MediaMetadata, source: QueueSource?): MediaMetadata {
        if (source == null) return meta
        val extras = Bundle(meta.extras ?: Bundle()).apply {
            putString(SOURCE_ID, source.id)
            putString(SOURCE_KIND, source.kind.name)
            putString(SOURCE_TITLE, source.title)
        }
        return meta.buildUpon().setExtras(extras).build()
    }

    fun source(meta: MediaMetadata): QueueSource? {
        val e = meta.extras ?: return null
        val id = e.getString(SOURCE_ID) ?: return null
        val kind = SourceKind.entries.firstOrNull { it.name == e.getString(SOURCE_KIND) } ?: SourceKind.LIST
        return QueueSource(id, kind, e.getString(SOURCE_TITLE).orEmpty())
    }

    /**
     * A playable item for [mediaId]: the file to open and, for a song inside a longer file,
     * the part to play. Its title and artist are the track's unless [fallback] (what the app
     * sent) names it otherwise, as a playlist entry may.
     */
    fun resolve(library: Library, mediaId: String, fallback: MediaMetadata? = null, source: QueueSource? = null): MediaItem? {
        val track = library.track(mediaId)
        val parsed = MediaIds.parse(mediaId)
        val uri = track?.file?.id ?: parsed.fileId.takeIf { it.startsWith("content:") || it.startsWith("file:") } ?: return null
        val builder = MediaItem.Builder().setMediaId(mediaId).setUri(Uri.parse(uri))
        if (parsed.startMs > 0 || parsed.endMs != null) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(parsed.startMs)
                    .setEndPositionMs(parsed.endMs ?: C.TIME_END_OF_SOURCE)
                    .build(),
            )
        }
        val meta = if (track != null) {
            metadata(
                fallback?.title?.toString() ?: track.title,
                fallback?.artist?.toString() ?: track.artist,
                track.album,
                track.file.art,
                track.file.isVideo,
            )
        } else {
            fallback ?: metadata(TrackNames.baseName(Uri.parse(uri).lastPathSegment.orEmpty()), null, null, null, false)
        }
        return builder.setMediaMetadata(withSource(meta, source)).build()
    }

    /** The metadata of a whole file while chapter [index] of it plays (for the notification). */
    fun chapterMetadata(library: Library, mediaId: String, index: Int): MediaMetadata? {
        val track = library.track(mediaId) ?: return null
        if (track.isClip || index !in track.file.chapters.indices) return null
        val part = track.file.chapterTrack(index)
        return metadata(part.title, part.artist, track.file.title, track.file.art, track.file.isVideo)
            .buildUpon()
            .setSubtitle("${index + 1} of ${track.file.chapters.size} · ${Chapters.formatTime(part.startMs)}")
            .build()
    }

    fun metadata(title: String, artist: String?, album: String?, art: String?, video: Boolean): MediaMetadata =
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(art?.let { Uri.fromFile(File(it)) })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (video) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
}
