package com.chore.tracker.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/** Decode a `data:image/...;base64,...` string to an ImageBitmap, or null if malformed. */
fun decodeAvatarDataUrl(dataUrl: String?): ImageBitmap? {
    if (dataUrl.isNullOrBlank()) return null
    val comma = dataUrl.indexOf(',')
    if (comma <= 0 || !dataUrl.startsWith("data:image/")) return null
    val b64 = dataUrl.substring(comma + 1)
    return runCatching {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
        bmp.asImageBitmap()
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
