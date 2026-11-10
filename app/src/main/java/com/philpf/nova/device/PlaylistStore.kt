package com.philpf.nova.device

import android.content.Context
import com.philpf.nova.core.Playlist
import com.philpf.nova.core.PlaylistCodec
import com.philpf.nova.core.PlaylistOps
import com.philpf.nova.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** The user's playlists, in a JSON file in the app's storage. */
class PlaylistStore(context: Context) {
    private val file = File(context.filesDir, "playlists.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _playlists = MutableStateFlow(runCatching { PlaylistCodec.decode(file.readText()) }.getOrDefault(emptyList()))
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private fun update(change: (List<Playlist>) -> List<Playlist>) {
        val next = synchronized(this) { change(_playlists.value).also { _playlists.value = it } }
        scope.launch { synchronized(file) { runCatching { file.writeText(PlaylistCodec.encode(next)) } } }
    }

    /** Creates a playlist (its name made unique) and returns its id. */
    fun create(name: String, tracks: List<Track> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        update { PlaylistOps.create(it, id, name, tracks.map(Playlist.Entry::of)) }
        return id
    }

    fun rename(id: String, name: String) = update { PlaylistOps.rename(it, id, name) }

    fun delete(id: String) = update { PlaylistOps.delete(it, id) }

    fun add(id: String, tracks: List<Track>) = update { PlaylistOps.add(it, id, tracks.map(Playlist.Entry::of)) }

    fun removeAt(id: String, index: Int) = update { PlaylistOps.removeAt(it, id, index) }

    fun move(id: String, from: Int, to: Int) = update { PlaylistOps.move(it, id, from, to) }
}
