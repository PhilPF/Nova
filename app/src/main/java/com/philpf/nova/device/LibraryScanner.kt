package com.philpf.nova.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.philpf.nova.core.ChapterResolver
import com.philpf.nova.core.ChapterSources
import com.philpf.nova.core.CueSheet
import com.philpf.nova.core.EmbeddedTagReader
import com.philpf.nova.core.FolderFiles
import com.philpf.nova.core.InfoJson
import com.philpf.nova.core.MediaFile
import com.philpf.nova.core.TrackNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * Walks the music folders and reads every song and video in them: their tags (Android's
 * MediaMetadataRetriever), their chapters and descriptions (the app's own readers), their
 * sidecar files and a picture. A file whose size and date (and its sidecars') have not changed
 * since [previous] is not read again.
 */
class LibraryScanner(private val context: Context, private val artDir: File) {

    private class Found(val node: TreeNode, val folder: String, val siblings: List<TreeNode>, val kind: FolderFiles.Kind)

    suspend fun scan(
        trees: List<MediaTree>,
        previous: Map<String, MediaFile>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val found = mutableListOf<Found>()
        for (tree in trees) {
            val root = tree.root() ?: continue
            walk(tree, root, "", found)
        }
        onProgress(0, found.size)
        val done = AtomicInteger()
        val gate = Semaphore(4)
        val cueCache = HashMap<String, CueSheet?>()
        val files = coroutineScope {
            found.map { f ->
                async {
                    gate.withPermit {
                        ensureActive()
                        val sidecars = sidecarsOf(f)
                        val modified = maxOf(f.node.modified, sidecars.maxOfOrNull { it.modified } ?: 0)
                        val old = previous[f.node.uri]
                        val result = if (old != null && old.size == f.node.size && old.modified == modified && (old.art == null || File(old.art).exists())) {
                            old
                        } else {
                            runCatching { read(f, modified, cueCache) }.getOrNull()
                        }
                        onProgress(done.incrementAndGet(), found.size)
                        result
                    }
                }
            }.awaitAll().filterNotNull()
        }
        cleanArt(files)
        files
    }

    private fun walk(tree: MediaTree, folder: TreeNode, path: String, out: MutableList<Found>) {
        val children = tree.children(folder)
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                walk(tree, child, if (path.isEmpty()) child.name else "$path/${child.name}", out)
            } else {
                val kind = FolderFiles.kind(child.name, child.mime) ?: continue
                out += Found(child, path, children, kind)
            }
        }
    }

    private fun sidecarsOf(f: Found): List<TreeNode> {
        val names = f.siblings.map { it.name }
        return listOfNotNull(
            FolderFiles.sidecar(f.node.name, names, ".info.json"),
            FolderFiles.sidecar(f.node.name, names, ".description"),
            FolderFiles.sidecar(f.node.name, names, ".cue"),
        ).mapNotNull { n -> f.siblings.firstOrNull { it.name == n } }
    }

    private fun readText(node: TreeNode?, limit: Int = 8 shl 20): String? = node?.let {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(it.uri))?.use { s -> String(s.readNBytesCompat(limit), Charsets.UTF_8) }
        }.getOrNull()
    }

    private fun read(f: Found, modified: Long, cueCache: HashMap<String, CueSheet?>): MediaFile {
        val names = f.siblings.map { it.name }
        fun sibling(name: String?) = name?.let { n -> f.siblings.firstOrNull { it.name == n } }

        val info = readText(sibling(FolderFiles.sidecar(f.node.name, names, ".info.json")))?.let(InfoJson::parse)
        val descriptionFile = readText(sibling(FolderFiles.sidecar(f.node.name, names, ".description")))
        val cue = sibling(FolderFiles.sidecar(f.node.name, names, ".cue"))?.let { node ->
            synchronized(cueCache) { cueCache.getOrPut(node.uri) { readText(node)?.let(CueSheet::parse) } }
        } ?: f.siblings.filter { FolderFiles.extension(it.name) == "cue" }.firstNotNullOfOrNull { node ->
            val sheet = synchronized(cueCache) { cueCache.getOrPut(node.uri) { readText(node)?.let(CueSheet::parse) } }
            sheet?.takeIf { s -> s.files.any { it.name.substringAfterLast('/').equals(f.node.name, ignoreCase = true) } }
        }
        val embedded = ChannelSource.open(context, f.node.uri) { EmbeddedTagReader.read(it) } ?: com.philpf.nova.core.EmbeddedTags.EMPTY

        val mmr = MediaMetadataRetriever()
        try {
            runCatching { mmr.setDataSource(context, Uri.parse(f.node.uri)) }
            fun tag(key: Int) = runCatching { mmr.extractMetadata(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val duration = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: info?.durationMs ?: 0
            val hasVideo = tag(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val tagTitle = tag(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val tagArtist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val fallbackName = TrackNames.withoutVideoId(TrackNames.baseName(f.node.name))

            // A title tag (or a file name) "Artist - Title" names the artist when no tag does.
            val rawTitle = info?.track ?: tagTitle ?: info?.title ?: fallbackName
            val split = TrackNames.split(rawTitle)
            val artist = tagArtist ?: info?.artist ?: split.artist
            val title = if (tagArtist == null && info?.artist == null && split.artist != null) split.title else rawTitle

            val (chapters, origin) = ChapterResolver.resolve(
                ChapterSources(embedded = embedded, info = info, cue = cue, fileName = f.node.name, descriptionFile = descriptionFile),
                duration,
            )
            val art = saveArt(f, mmr, hasVideo || f.kind == FolderFiles.Kind.VIDEO, duration)
            return MediaFile(
                id = f.node.uri,
                fileName = f.node.name,
                folder = f.folder,
                title = title,
                artist = artist,
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: info?.album ?: cue?.title,
                albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                trackNumber = tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull(),
                discNumber = tag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull(),
                year = (tag(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: tag(MediaMetadataRetriever.METADATA_KEY_DATE))
                    ?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toIntOrNull() },
                durationMs = duration,
                isVideo = hasVideo || (f.kind == FolderFiles.Kind.VIDEO && tag(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes"),
                chapters = chapters,
                chapterOrigin = origin,
                art = art,
                modified = modified,
                size = f.node.size,
            )
        } finally {
            runCatching { mmr.release() }
        }
    }

    /**
     * A picture for the file, saved small in the app's storage: its embedded art, else its own
     * thumbnail file, else the folder's cover, else (for a video) a frame from it.
     */
    private fun saveArt(f: Found, mmr: MediaMetadataRetriever, video: Boolean, durationMs: Long): String? {
        val names = f.siblings.map { it.name }
        val embedded = runCatching { mmr.embeddedPicture }.getOrNull()
        val bitmap: Bitmap? = embedded?.let { decode(it) }
            ?: FolderFiles.thumbnail(f.node.name, names)?.let { n -> f.siblings.firstOrNull { it.name == n } }?.let { decodeUri(it.uri) }
            ?: FolderFiles.cover(names)?.let { n -> f.siblings.firstOrNull { it.name == n } }?.let { decodeUri(it.uri) }
            ?: if (video) {
                runCatching {
                    val at = (durationMs / 5).coerceAtMost(30_000) * 1000
                    mmr.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, ART_SIZE, ART_SIZE)
                }.getOrNull()
            } else {
                null
            }
        bitmap ?: return null
        artDir.mkdirs()
        val file = File(artDir, sha1(f.node.uri) + ".jpg")
        return runCatching {
            val scaled = scale(bitmap)
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            file.path
        }.getOrNull()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
    }

    private fun decodeUri(uri: String): Bitmap? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readNBytesCompat(32 shl 20) }?.let(::decode)
    }.getOrNull()

    private fun sampleSize(w: Int, h: Int): Int {
        var s = 1
        while (w / (s * 2) >= ART_SIZE && h / (s * 2) >= ART_SIZE) s *= 2
        return s
    }

    /** At most [ART_SIZE] on its shorter side, cropped square from the centre (videos are wide). */
    private fun scale(b: Bitmap): Bitmap {
        val side = minOf(b.width, b.height)
        val square = if (b.width != b.height) Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side) else b
        return if (side > ART_SIZE) Bitmap.createScaledBitmap(square, ART_SIZE, ART_SIZE, true) else square
    }

    private fun cleanArt(files: List<MediaFile>) {
        val keep = files.mapNotNull { it.art }.toSet()
        artDir.listFiles()?.forEach { if (it.path !in keep) it.delete() }
    }

    companion object {
        const val ART_SIZE = 1024

        fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** InputStream.readNBytes is Android 13+; this reads up to [limit] bytes on any version. */
internal fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
