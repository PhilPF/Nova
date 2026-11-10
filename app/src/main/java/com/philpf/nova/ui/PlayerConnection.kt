package com.philpf.nova.ui

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.philpf.nova.core.Chapter
import com.philpf.nova.core.ChapterNav
import com.philpf.nova.core.Track
import com.philpf.nova.service.MediaItems
import com.philpf.nova.service.PlaybackService

/** What the player is doing, as the screens show it. Positions are read separately, as they move. */
@Immutable
data class PlayerState(
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueItem> = emptyList(),
    val index: Int = -1,
    val videoSize: VideoSize = VideoSize.UNKNOWN,
    /** Whether the current item has a video track (known before any frame is drawn). */
    val hasVideoTrack: Boolean = false,
) {
    val hasVideo: Boolean get() = hasVideoTrack || (videoSize.width > 0 && videoSize.height > 0)
}

@Immutable
data class QueueItem(val mediaId: String, val title: String, val artist: String?)

/**
 * The app's side of the playback service: a MediaController, and its state for Compose.
 * Tracks are sent by id; the service finds their files.
 */
class PlayerConnection(private val context: Context) {
    var controller by mutableStateOf<MediaController?>(null)
        private set
    var state by mutableStateOf(PlayerState())
        private set

    /** Changes with every seek made here, so the position shown is read again at once. */
    var seeks by mutableIntStateOf(0)
        private set

    private var future: ListenableFuture<MediaController>? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            c.addListener(listener)
            controller = c
            refresh()
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun refresh() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            QueueItem(item.mediaId, item.mediaMetadata.title?.toString() ?: "", item.mediaMetadata.artist?.toString())
        }
        state = PlayerState(
            mediaId = c.currentMediaItem?.mediaId,
            title = c.mediaMetadata.title?.toString(),
            artist = c.mediaMetadata.artist?.toString(),
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            queue = queue,
            index = c.currentMediaItemIndex,
            videoSize = c.videoSize,
            hasVideoTrack = c.currentTracks.containsType(C.TRACK_TYPE_VIDEO),
        )
    }

    /** Plays [tracks] from [index] (at [startMs] into it), replacing the queue. */
    fun play(tracks: List<Track>, index: Int = 0, startMs: Long = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        val start = if (shuffle && index == 0) tracks.indices.random() else index
        c.setMediaItems(tracks.map(MediaItems::request), start.coerceIn(tracks.indices), startMs)
        c.prepare()
        c.play()
    }

    fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(tracks)
        c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map(MediaItems::request))
    }

    fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(tracks)
        c.addMediaItems(tracks.map(MediaItems::request))
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
        if (c.playWhenReady) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    /**
     * The songs of the item playing, when it is a whole long video (set by the screen, which
     * has the library). Next and Previous work them out here, by the rule the service uses for
     * the notification and headphones ([ChapterNav]), and seek to them directly: a
     * MediaController guesses the outcome of its own seekToNext as "the next item of the
     * queue" and shows it until the service answers, which made the screen jump to the wrong
     * song and back.
     */
    var chaptersOf: (MediaItem) -> List<Chapter> = { emptyList() }

    private fun currentChapters(): List<Chapter> {
        val item = controller?.currentMediaItem ?: return emptyList()
        if (item.clippingConfiguration != MediaItem.ClippingConfiguration.UNSET) return emptyList()
        return chaptersOf(item)
    }

    /** Whether Next goes anywhere: a later song in this video, or a later item. */
    fun canNext(): Boolean {
        val c = controller ?: return false
        return ChapterNav.next(currentChapters(), c.currentPosition) != null || c.hasNextMediaItem()
    }

    fun next() {
        seeks++
        val c = controller ?: return
        val target = ChapterNav.next(currentChapters(), c.currentPosition)
        when {
            target != null -> c.seekTo(target)
            c.hasNextMediaItem() -> c.seekToNextMediaItem()
            // The last song of the queue: Next does nothing rather than restart something.
        }
    }

    fun previous() {
        seeks++
        val c = controller ?: return
        val chapters = currentChapters()
        val position = c.currentPosition
        val target = ChapterNav.previous(chapters, position)
        when {
            target != null -> c.seekTo(target)
            position > ChapterNav.RESTART_MS -> c.seekTo(0)
            c.hasPreviousMediaItem() -> c.seekToPreviousMediaItem()
            else -> c.seekTo(0)
        }
    }

    fun seekTo(ms: Long) {
        seeks++
        controller?.seekTo(ms)
    }

    fun skipTo(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeAt(index: Int) = controller?.removeMediaItem(index)

    fun moveItem(from: Int, to: Int) = controller?.moveMediaItem(from, to)

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Where playback is now, in the current item (a clip counts from its own start). */
    fun position(): Long = controller?.currentPosition ?: 0

    fun currentItem(): MediaItem? = controller?.currentMediaItem
}
