package com.garetto.jukebox.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.garetto.jukebox.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads and remembers album covers so lists scroll smoothly. */
object ArtCache {
    // Up to ~40 MB of covers.
    private val cache = object : LruCache<String, Bitmap>(40 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val missing = HashSet<String>()

    private fun key(song: Song, px: Int): String {
        val group = if (song.albumId > 0L) "a${song.albumId}" else "s${song.id}"
        return "$group@$px"
    }

    fun peek(song: Song, px: Int): Bitmap? = cache.get(key(song, px))

    suspend fun load(context: Context, song: Song, px: Int): Bitmap? {
        val k = key(song, px)
        cache.get(k)?.let { return it }
        if (synchronized(missing) { missing.contains(k) }) return null
        val bmp = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(song.uri, android.util.Size(px, px), null)
            } catch (e: Exception) {
                null
            }
        }
        if (bmp != null) cache.put(k, bmp) else synchronized(missing) { missing.add(k) }
        return bmp
    }
}

@Composable
fun rememberArt(song: Song?, px: Int): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val art by produceState<ImageBitmap?>(initialValue = null, song?.id, px) {
        value = song?.let { ArtCache.peek(it, px)?.asImageBitmap() }
        if (value == null && song != null) {
            value = ArtCache.load(context, song, px)?.asImageBitmap()
        }
    }
    return art
}

private val fallbackPalettes = listOf(
    listOf(Color(0xFF8B3DFF), Color(0xFFFF2E93)),
    listOf(Color(0xFF3A1CFF), Color(0xFF8B3DFF)),
    listOf(Color(0xFFFF2E93), Color(0xFF5B1A8A)),
    listOf(Color(0xFF241046), Color(0xFF8B3DFF)),
    listOf(Color(0xFF5B1A8A), Color(0xFF38E8FF)),
)

/** Album cover, or a neon gradient tile when the file has no cover. */
@Composable
fun ArtBox(
    song: Song?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    px: Int = 160,
) {
    val art = rememberArt(song, px)
    val palette = fallbackPalettes[((song?.albumId ?: 0L).mod(fallbackPalettes.size.toLong())).toInt()]
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(palette)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GlyphIcon(Glyph.Note, Color.White.copy(alpha = 0.85f), Modifier.fillMaxSize(0.5f))
        }
    }
}
