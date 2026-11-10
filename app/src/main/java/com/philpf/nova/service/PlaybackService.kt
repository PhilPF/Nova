package com.philpf.nova.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.philpf.nova.MainActivity
import com.philpf.nova.NovaApp
import com.philpf.nova.core.ChapterNav
import com.philpf.nova.core.Chapter
import com.philpf.nova.core.Chapters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays in the background: ExoPlayer in a media session, which gives the notification, the
 * lock-screen controls and headset buttons. The app's screens control it through a
 * MediaController.
 *
 * Inside a long video with a tracklist, Next and Previous move between its songs (from the
 * app, the notification and headphones alike), and the notification names the song playing.
 */
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var exo: ExoPlayer
    private var session: MediaSession? = null
    private var chapterJob: Job? = null
    private val app get() = application as NovaApp

    override fun onCreate() {
        super.onCreate()
        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val player = ChapterPlayer(exo) { chaptersOfCurrent() }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(Callback())
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = followChapters(isPlaying)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Updating the metadata below is itself a transition, to the same item.
                if (mediaItem?.mediaId == lastMediaId) return
                lastMediaId = mediaItem?.mediaId
                lastChapter = -2
                followChapters(exo.isPlaying)
            }
        })
    }

    /** The current item's chapters, when it is a whole file (a clip is one song already). */
    private fun chaptersOfCurrent(): List<Chapter> {
        val item = exo.currentMediaItem ?: return emptyList()
        if (item.clippingConfiguration != MediaItem.ClippingConfiguration.UNSET) return emptyList()
        return app.library.library.value.track(item.mediaId)?.takeIf { !it.isClip }?.file?.chapters.orEmpty()
    }

    private var lastChapter = -2
    private var lastMediaId: String? = null

    /** While playing, puts the song of the tracklist into the item's metadata when it changes. */
    private fun followChapters(playing: Boolean) {
        chapterJob?.cancel()
        if (!playing) return
        chapterJob = scope.launch {
            while (isActive) {
                updateChapterMetadata()
                delay(500)
            }
        }
    }

    private fun updateChapterMetadata() {
        val chapters = chaptersOfCurrent()
        if (chapters.isEmpty()) return
        val index = Chapters.indexAt(chapters, exo.currentPosition)
        if (index == lastChapter) return
        lastChapter = index
        val item = exo.currentMediaItem ?: return
        val library = app.library.library.value
        val meta = MediaItems.chapterMetadata(library, item.mediaId, index)
            ?: MediaItems.resolve(library, item.mediaId)?.mediaMetadata
            ?: return
        // The same file with new metadata: ExoPlayer updates it in place, without rebuffering.
        exo.replaceMediaItem(exo.currentMediaItemIndex, item.buildUpon().setMediaMetadata(meta).build())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Items arrive from the app with only an id; the file and the part to play are added here. */
    private inner class Callback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            val library = app.library.awaitLoaded()
            mediaItems.mapNotNull { MediaItems.resolve(library, it.mediaId, it.mediaMetadata.takeIf { m -> m.title != null }) }.toMutableList()
        }
    }
}

/**
 * Next and Previous that move between the songs of a long video before moving between items.
 * Both are always offered, so a single long video still shows them.
 */
class ChapterPlayer(player: Player, private val chapters: () -> List<Chapter>) : ForwardingPlayer(player) {
    override fun seekToNext() {
        val target = ChapterNav.next(chapters(), currentPosition)
        if (target != null) seekTo(target) else super.seekToNext()
    }

    override fun seekToPrevious() {
        val list = chapters()
        val target = ChapterNav.previous(list, currentPosition)
        when {
            target != null -> seekTo(target)
            // In the first song of a video: back to its start, as in any song.
            list.isNotEmpty() && currentPosition > ChapterNav.RESTART_MS -> seekTo(0)
            else -> super.seekToPrevious()
        }
    }

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_PREVIOUS || super.isCommandAvailable(command)
}
