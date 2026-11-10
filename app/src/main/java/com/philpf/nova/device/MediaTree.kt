package com.philpf.nova.device

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.philpf.nova.core.ByteSource
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** A file or folder in a music folder. [uri] is what the player and the readers open. */
data class TreeNode(
    val uri: String,
    val name: String,
    val mime: String?,
    val size: Long,
    val modified: Long,
    val isDirectory: Boolean,
    /** What the tree needs to list a folder's children. */
    val key: String,
)

/** A folder the user chose, walked to find their music. */
interface MediaTree {
    val rootName: String
    fun root(): TreeNode?
    fun children(folder: TreeNode): List<TreeNode>
}

/**
 * A folder chosen with the system's folder picker (ACTION_OPEN_DOCUMENT_TREE). The app keeps
 * a persistent permission to read it, and lists it through the Storage Access Framework, so it
 * needs no storage permission.
 */
class DocumentTree(private val context: Context, treeUri: String) : MediaTree {
    private val tree = Uri.parse(treeUri)
    private val resolver = context.contentResolver
    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    override val rootName: String by lazy { root()?.name ?: displayName(treeUri) }

    override fun root(): TreeNode? {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                node(id, c.getString(1), c.getString(2), c.getLong(3), c.getLong(4))
            }
        }.getOrNull()
    }

    override fun children(folder: TreeNode): List<TreeNode> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.key)
        val out = mutableListOf<TreeNode>()
        runCatching {
            resolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out += node(c.getString(0), c.getString(1) ?: continue, c.getString(2), c.getLong(3), c.getLong(4))
                }
            }
        }
        return out
    }

    private fun node(id: String, name: String, mime: String?, size: Long, modified: Long) = TreeNode(
        uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
        name = name,
        mime = mime,
        size = size,
        modified = modified,
        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
        key = id,
    )

    companion object {
        /** A readable name for a folder from its tree URI ("Music/Mixes" for "primary:Music/Mixes"). */
        fun displayName(treeUri: String): String {
            val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)) }.getOrNull() ?: return treeUri
            return id.substringAfter(':').ifEmpty { id.substringBefore(':') }
        }
    }
}

/** A plain folder, for the debug build's sample library (filled over adb). */
class FileTree(private val dir: File) : MediaTree {
    override val rootName: String get() = dir.name

    override fun root(): TreeNode? = if (dir.isDirectory) node(dir) else null

    override fun children(folder: TreeNode): List<TreeNode> = File(folder.key).listFiles().orEmpty().map(::node)

    private fun node(f: File) = TreeNode(Uri.fromFile(f).toString(), f.name, null, f.length(), f.lastModified(), f.isDirectory, f.path)
}

/** A file opened for the tag readers. */
class ChannelSource(private val channel: FileChannel) : ByteSource {
    override val size: Long = channel.size()

    override fun read(position: Long, dst: ByteArray, offset: Int, len: Int): Int {
        if (position >= size) return 0
        var done = 0
        while (done < len) {
            val n = channel.read(ByteBuffer.wrap(dst, offset + done, len - done), position + done)
            if (n <= 0) break
            done += n
        }
        return done
    }

    companion object {
        /** Opens [uri] and passes it to [block]; null when it cannot be opened. */
        fun <T> open(context: Context, uri: String, block: (ByteSource) -> T): T? = runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { block(ChannelSource(it)) }
            }
        }.getOrNull()
    }
}
