package com.philpf.nova.core

/**
 * Random-access bytes of a file, which the tag and chapter readers read from. On the phone it
 * is a file descriptor opened through the Storage Access Framework; in tests, a byte array.
 */
interface ByteSource {
    val size: Long

    /** Reads up to [len] bytes at [position] into [dst]; fewer only at the end of the file. */
    fun read(position: Long, dst: ByteArray, offset: Int = 0, len: Int = dst.size - offset): Int
}

class ArraySource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, dst: ByteArray, offset: Int, len: Int): Int {
        if (position >= bytes.size || position < 0) return 0
        val n = minOf(len.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), dst, offset, n)
        return n
    }
}

/**
 * Keeps one block of [source] in memory, so readers that walk headers a few bytes at a time
 * do not reach the file for each of them.
 */
class BufferedSource(private val source: ByteSource, private val blockSize: Int = 64 * 1024) : ByteSource {
    override val size: Long get() = source.size
    private val block = ByteArray(blockSize)
    private var blockStart = -1L
    private var blockLength = 0

    override fun read(position: Long, dst: ByteArray, offset: Int, len: Int): Int {
        if (len > blockSize / 2) return source.read(position, dst, offset, len)
        var done = 0
        while (done < len) {
            val at = position + done
            if (blockStart < 0 || at < blockStart || at >= blockStart + blockLength) {
                if (at >= size) break
                blockStart = at
                blockLength = source.read(at, block, 0, blockSize)
                if (blockLength <= 0) {
                    blockStart = -1
                    break
                }
            }
            val from = (at - blockStart).toInt()
            val n = minOf(len - done, blockLength - from)
            System.arraycopy(block, from, dst, offset + done, n)
            done += n
        }
        return done
    }
}

/** [n] bytes at [position], or fewer at the end of the file. */
fun ByteSource.bytes(position: Long, n: Int): ByteArray {
    if (n <= 0 || position >= size) return ByteArray(0)
    val count = minOf(n.toLong(), size - position).toInt()
    val out = ByteArray(count)
    var done = 0
    while (done < count) {
        val r = read(position + done, out, done, count - done)
        if (r <= 0) return out.copyOf(done)
        done += r
    }
    return out
}

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF

internal fun ByteArray.u16be(i: Int): Int = (u8(i) shl 8) or u8(i + 1)

internal fun ByteArray.u32be(i: Int): Long =
    (u8(i).toLong() shl 24) or (u8(i + 1).toLong() shl 16) or (u8(i + 2).toLong() shl 8) or u8(i + 3).toLong()

internal fun ByteArray.u64be(i: Int): Long = (u32be(i) shl 32) or u32be(i + 4)

internal fun ByteArray.u32le(i: Int): Long =
    u8(i).toLong() or (u8(i + 1).toLong() shl 8) or (u8(i + 2).toLong() shl 16) or (u8(i + 3).toLong() shl 24)

internal fun ByteArray.ascii(i: Int, n: Int): String = String(this, i, n, Charsets.ISO_8859_1)
