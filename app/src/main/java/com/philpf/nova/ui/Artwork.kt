package com.philpf.nova.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.philpf.nova.core.ArtColors
import com.philpf.nova.ui.art.Icon
import com.philpf.nova.ui.art.NovaIcon
import com.philpf.nova.ui.theme.LocalGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Album art decoded from the app's art cache, kept in memory by path and size. */
object ArtCache {
    private val bitmaps = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val colors = LruCache<String, List<Color>>(256)

    private fun key(path: String, size: Int) = "$path|$size|${File(path).lastModified()}"

    fun cached(path: String, size: Int): Bitmap? = bitmaps.get(key(path, size))

    suspend fun load(path: String, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        val k = key(path, size)
        bitmaps.get(k)?.let { return@withContext it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return@withContext null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
        bitmaps.put(k, bitmap)
        bitmap
    }

    /** The art's main colours, for the glows behind the glass. */
    suspend fun colors(path: String): List<Color> = withContext(Dispatchers.Default) {
        colors.get(path)?.let { return@withContext it }
        val small = load(path, 64) ?: return@withContext emptyList()
        val scaled = Bitmap.createScaledBitmap(small, 24, 24, true)
        val pixels = IntArray(24 * 24).also { scaled.getPixels(it, 0, 24, 0, 0, 24, 24) }
        val result = ArtColors.dominant(pixels).map { Color(it) }
        colors.put(path, result)
        result
    }
}

/**
 * The picture at [path], decoded at about [sizePx]. While a new picture loads, the previous one
 * stays (rather than a blank), so changing songs does not flash.
 */
@Composable
fun rememberArt(path: String?, sizePx: Int): ImageBitmap? {
    var bitmap by remember { mutableStateOf(path?.let { ArtCache.cached(it, sizePx) }) }
    LaunchedEffect(path, sizePx) {
        val cached = path?.let { ArtCache.cached(it, sizePx) }
        bitmap = cached ?: path?.let { ArtCache.load(it, sizePx) }
    }
    return remember(bitmap) { bitmap?.asImageBitmap() }
}

@Composable
fun rememberArtColors(path: String?): List<Color> {
    val colors by produceState(initialValue = emptyList<Color>(), path) {
        value = path?.let { ArtCache.colors(it) }.orEmpty()
    }
    return colors
}

/** A song's picture, or a glass placeholder with a note (or a film strip for a video). */
@Composable
fun Artwork(
    path: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    video: Boolean = false,
    sizePx: Int = 256,
) {
    val glass = LocalGlass.current
    val image = rememberArt(path, sizePx)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(glass.accent.copy(alpha = 0.35f), glass.controlFill)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(if (video) NovaIcon.VIDEO else NovaIcon.NOTE, glass.textSecondary, Modifier.size(size * 0.42f))
        }
    }
}
