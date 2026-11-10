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
import java.io.File

/** Library tracks as the player's items, and back. */
object MediaItems {
    /** What the app sends to the player: an id and what to show; [resolve] adds the rest. */
    fun request(track: Track): MediaItem =
        MediaItem.Builder().setMediaId(track.mediaId).setMediaMetadata(metadata(track.title, track.artist, track.album, track.file.art, track.file.isVideo)).build()

    /**
     * A playable item for [mediaId]: the file to open and, for a song inside a longer file,
     * the part to play. Its title and artist are the track's unless [fallback] (what the app
     * sent) names it otherwise, as a playlist entry may.
     */
    fun resolve(library: Library, mediaId: String, fallback: MediaMetadata? = null): MediaItem? {
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
        return builder.setMediaMetadata(meta).build()
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
