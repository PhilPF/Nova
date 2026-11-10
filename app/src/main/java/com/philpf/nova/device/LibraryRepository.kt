package com.philpf.nova.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.philpf.nova.core.Chapter
import com.philpf.nova.core.ChapterOrigin
import com.philpf.nova.core.Chapters
import com.philpf.nova.core.FileEdits
import com.philpf.nova.core.FileEditsCodec
import com.philpf.nova.core.FolderFiles
import com.philpf.nova.core.Tracklists
import com.philpf.nova.core.Library
import com.philpf.nova.core.LibraryCodec
import com.philpf.nova.core.ManualChaptersCodec
import com.philpf.nova.core.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** How far a scan has got: files read of those found. */
data class ScanProgress(val done: Int, val total: Int)

/**
 * The library: loaded from the last scan when the app starts, then rescanned from the music
 * folder. Tracklists entered in the app are kept apart and laid over the scanned files, so
 * editing one needs no rescan and removing it brings back the file's own. Hidden folders and
 * messaging apps' audio are left out the same way, so they come back without a rescan.
 */
class LibraryRepository(
    private val context: Context,
    private val settings: SettingsStore,
    /** The debug build's sample folder, scanned instead of the chosen folders. */
    private val demoDir: File? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheFile = File(context.filesDir, if (demoDir != null) "library-demo.json" else "library.json")
    private val manualFile = File(context.filesDir, "tracklists.json")
    private val editsFile = File(context.filesDir, "edits.json")
    private val scanner = LibraryScanner(context, File(context.filesDir, if (demoDir != null) "art-demo" else "art"))

    private var scanned: List<MediaFile> = emptyList()
    private var manual: Map<String, List<Chapter>> = emptyMap()
    private var edits: Map<String, FileEdits> = emptyMap()

    private val _library = MutableStateFlow(Library.EMPTY)
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _progress = MutableStateFlow<ScanProgress?>(null)
    /** Non-null while scanning. */
    val progress: StateFlow<ScanProgress?> = _progress.asStateFlow()

    private var scanJob: Job? = null

    init {
        scope.launch {
            scanned = runCatching { LibraryCodec.decode(cacheFile.readText()) }.getOrDefault(emptyList())
            manual = runCatching { ManualChaptersCodec.decode(manualFile.readText()) }.getOrDefault(emptyMap())
            edits = runCatching { FileEditsCodec.decode(editsFile.readText()) }.getOrDefault(emptyMap())
            publish()
            _loaded.value = true
            rescan()
        }
        scope.launch {
            combine(settings.hiddenFolders, settings.skipMessaging) { _, _ -> }.drop(1).collect { publish() }
        }
    }

    suspend fun awaitLoaded(): Library {
        loaded.first { it }
        return library.value
    }

    fun rescan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            val trees: List<MediaTree> = if (demoDir != null) {
                listOf(FileTree(demoDir))
            } else {
                listOfNotNull(settings.folder.value).map { DocumentTree(context, it) }
            }
            _progress.value = ScanProgress(0, 0)
            try {
                val files = scanner.scan(trees, scanned.associateBy { it.id }) { done, total -> _progress.value = ScanProgress(done, total) }
                scanned = files
                publish()
                runCatching { cacheFile.writeText(LibraryCodec.encode(files)) }
            } finally {
                _progress.value = null
            }
        }
    }

    /**
     * Makes a folder picked with ACTION_OPEN_DOCUMENT_TREE the music folder, keeping the
     * permission to read it (and giving up the previous folder's), and scans it.
     */
    fun setFolder(uri: Uri) {
        val old = settings.folder.value
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (old != null && old != uri.toString()) {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            // Hidden subfolders were paths inside the old folder.
            settings.setHidden(emptyList())
        }
        settings.setFolder(uri.toString())
        rescan()
    }

    /** Sets the tracklist of a file; null goes back to the file's own. */
    fun setTracklist(fileId: String, chapters: List<Chapter>?) {
        manual = if (chapters == null) manual - fileId else manual + (fileId to chapters)
        publish()
        scope.launch { runCatching { manualFile.writeText(ManualChaptersCodec.encode(manual)) } }
    }

    fun hasManualTracklist(fileId: String) = fileId in manual

    /** Sets a file's title, artist and album in the app; null (or no edits) goes back to its tags. */
    fun setEdits(fileId: String, e: FileEdits?) {
        edits = if (e == null || e.isEmpty) edits - fileId else edits + (fileId to e)
        publish()
        scope.launch { runCatching { editsFile.writeText(FileEditsCodec.encode(edits)) } }
    }

    fun edits(fileId: String): FileEdits? = edits[fileId]

    /** The file as its tags describe it, before the edits made in the app. */
    fun original(fileId: String): MediaFile? = scanned.firstOrNull { it.id == fileId }

    /** The tracklist the file itself gives, without the one entered in the app. */
    fun ownChapters(fileId: String): List<Chapter> = scanned.firstOrNull { it.id == fileId }?.chapters.orEmpty()

    private fun publish() {
        val hidden = settings.hiddenFolders.value
        val skipMessaging = settings.skipMessaging.value
        val shown = scanned.filter { f ->
            !FolderFiles.isHidden(f.folder, hidden) && !(skipMessaging && FolderFiles.isMessaging(f.folder, f.fileName))
        }
        val edited = shown.map { f -> edits[f.id]?.applyTo(f) ?: f }
        // Artists of whole songs, to tell which side of "A - B" in a tracklist is the artist.
        val known = edited.filter { !it.isMix }.mapNotNull { it.artist }.toSet()
        val files = edited.map { f ->
            val m = manual[f.id]
            when {
                m != null -> f.copy(chapters = Chapters.normalize(m, f.durationMs), chapterOrigin = ChapterOrigin.MANUAL)
                f.isMix -> f.copy(chapters = Tracklists.ordered(f.chapters, known))
                else -> f
            }
        }
        _library.value = Library(files)
    }
}
