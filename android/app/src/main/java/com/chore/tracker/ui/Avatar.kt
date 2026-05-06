package com.chore.tracker.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.ChoreApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Decode a `data:image/...;base64,...` string to an ImageBitmap, or null if malformed. */
fun decodeAvatarDataUrl(dataUrl: String?): ImageBitmap? {
    if (dataUrl.isNullOrBlank()) return null
    val comma = dataUrl.indexOf(',')
    if (comma <= 0 || !dataUrl.startsWith("data:image/")) return null
    val b64 = dataUrl.substring(comma + 1)
    return runCatching {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** Read the picked image, scale longest edge to [maxEdge] keeping aspect, and emit a JPEG data URL. */
fun encodeAvatarFromUri(
    resolver: ContentResolver,
    uri: Uri,
    maxEdge: Int = 256,
    quality: Int = 80,
): String? = runCatching {
    val source = resolver.openInputStream(uri).use { input ->
        BitmapFactory.decodeStream(input)
    } ?: return@runCatching null
    val scaled = scaleToFit(source, maxEdge)
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    if (scaled !== source) scaled.recycle()
    source.recycle()
    "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}.getOrNull()

private fun scaleToFit(src: Bitmap, maxEdge: Int): Bitmap {
    val w = src.width
    val h = src.height
    val longest = maxOf(w, h)
    if (longest <= maxEdge) return src
    val scale = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
}

/** Process-wide LRU-ish cache of decoded avatars keyed by user id.
 *
 *  Members and activity rows ship only an `avatarVersion` for each user;
 *  this cache fetches the actual data URL on first render and reuses the
 *  decoded bitmap until the version changes. Configure once on app start. */
object AvatarCache {
    private data class Entry(val version: Int, val bitmap: ImageBitmap?, val profileColorHex: String? = null)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val mutex = Mutex()

    @Volatile private var api: ChoreApi? = null

    fun configure(api: ChoreApi) {
        this.api = api
    }

    /** Returns the decoded bitmap for [userId] at [version], fetching if missing or stale. */
    suspend fun get(userId: String, version: Int): ImageBitmap? {
        cache[userId]?.let { if (it.version == version) return it.bitmap }
        val api = this.api ?: return null
        return mutex.withLock {
            cache[userId]?.let { if (it.version == version) return it.bitmap }
            val resp = runCatching { api.userAvatar(userId) }.getOrNull()
            val bmp = decodeAvatarDataUrl(resp?.avatar)
            cache[userId] = Entry(resp?.avatarVersion ?: version, bmp, resp?.profileColor)
            bmp
        }
    }

    /** Returns the profile ring color for [userId], fetching if missing or stale. */
    suspend fun getProfileColor(userId: String, version: Int): Color? {
        cache[userId]?.let { if (it.version == version) return it.profileColorHex?.let(::parseHexColor) }
        get(userId, version)
        return cache[userId]?.profileColorHex?.let(::parseHexColor)
    }

    /** Seed the cache from a known data URL — used after the user uploads their own. */
    fun put(userId: String, version: Int, dataUrl: String?, profileColorHex: String? = null) {
        cache[userId] = Entry(version, decodeAvatarDataUrl(dataUrl), profileColorHex)
    }

    fun clear() {
        cache.clear()
    }

    private fun parseHexColor(hex: String): Color? {
        val s = hex.trim().removePrefix("#")
        if (s.length != 6) return null
        return runCatching { Color(android.graphics.Color.parseColor("#$s")) }.getOrNull()
    }
}

/** Cache-backed avatar — fetches once per (userId, version) pair. */
@Composable
fun AvatarBadge(
    userId: String?,
    avatarVersion: Int,
    fallbackText: String,
    size: Int,
    ringColor: Color? = null,
) {
    var bitmap by remember(userId, avatarVersion) { mutableStateOf<ImageBitmap?>(null) }
    var fetchedRingColor by remember(userId, avatarVersion) { mutableStateOf<Color?>(ringColor) }
    LaunchedEffect(userId, avatarVersion) {
        if (userId != null) {
            bitmap = AvatarCache.get(userId, avatarVersion)
            fetchedRingColor = ringColor ?: AvatarCache.getProfileColor(userId, avatarVersion)
        } else {
            bitmap = null
            fetchedRingColor = ringColor
        }
    }
    AvatarSurface(bitmap = bitmap, fallbackText = fallbackText, size = size, ringColor = fetchedRingColor)
}

/** Avatar driven directly by a local data URL — used for the live preview while editing. */
@Composable
fun AvatarPreview(
    avatarDataUrl: String?,
    fallbackText: String,
    size: Int,
    ringColor: Color? = null,
) {
    val bitmap = remember(avatarDataUrl) { decodeAvatarDataUrl(avatarDataUrl) }
    AvatarSurface(bitmap = bitmap, fallbackText = fallbackText, size = size, ringColor = ringColor)
}

@Composable
private fun AvatarSurface(
    bitmap: ImageBitmap?,
    fallbackText: String,
    size: Int,
    ringColor: Color? = null,
) {
    val ringWidth = if (ringColor != null && size >= 24) 3.dp else 0.dp
    val innerSize = if (ringColor != null && size >= 24) (size - 4).coerceAtLeast(1) else size
    Box(
        modifier = Modifier
            .size(size.dp)
            .then(
                if (ringColor != null)
                    Modifier.border(ringWidth, ringColor, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(innerSize.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .testTag("avatarPreview"),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier
                        .size(innerSize.dp)
                        .clip(CircleShape),
                )
            } else {
                val style = when {
                    size <= 28 -> MaterialTheme.typography.labelSmall
                    size <= 48 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.headlineSmall
                }
                Text(
                    fallbackText,
                    style = style,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
