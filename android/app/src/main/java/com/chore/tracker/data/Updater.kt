package com.chore.tracker.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Bootstraps in-app updates from GitHub Releases.
 *
 * The CI workflow publishes one release per push to main, tagged
 * `v<versionName>+<versionCode>`. This class hits `releases/latest`,
 * parses the tag, and (if newer than the running build) downloads the
 * APK via the system DownloadManager and launches the install intent.
 *
 * Caveats:
 *  - Sideload installs require the app to hold REQUEST_INSTALL_PACKAGES
 *    AND the user to grant "install unknown apps" for it (one-time, OS
 *    settings).
 *  - Android requires every update to be signed with the same key as
 *    the installed APK. CI currently builds debug-signed APKs whose
 *    keystore varies per runner — that needs a stable keystore wired
 *    through GH secrets before this flow can succeed end-to-end.
 */
class Updater(
    private val context: Context,
    private val owner: String = "Shadaeiou",
    private val repo: String = "chore-tracker",
) {
    private val client = OkHttpClient.Builder().build()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Scan recent releases and return the highest-versionCode one that's
     * newer than the running build. Uses `/releases` (which includes
     * pre-releases) instead of `/releases/latest` (which silently skips
     * them). Robust to a maintainer publishing a marked-prerelease build.
     */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases?per_page=10")
            .header("Accept", "application/vnd.github+json")
            .build()
        val res = runCatching { client.newCall(req).execute() }.getOrNull() ?: return@withContext null
        res.use {
            if (!it.isSuccessful) return@withContext null
            val body = it.body?.string() ?: return@withContext null
            val releases = runCatching { json.decodeFromString<List<GithubRelease>>(body) }
                .getOrNull() ?: return@withContext null

            val best = releases.mapNotNull { rel ->
                val parsed = parseVersionFromTag(rel.tagName) ?: return@mapNotNull null
                val asset = rel.assets.firstOrNull { a -> a.name.endsWith(".apk") }
                    ?: return@mapNotNull null
                Triple(parsed, asset, rel)
            }.maxByOrNull { (parsed, _, _) -> parsed.code } ?: return@withContext null

            val (parsed, asset, release) = best
            if (parsed.code <= currentVersionCode) return@withContext null
            UpdateInfo(
                versionCode = parsed.code,
                versionName = parsed.name,
                downloadUrl = asset.url,
                notes = release.body,
            )
        }
    }

    /** Enqueue the APK download via DownloadManager. Returns the download id. */
    fun startDownload(info: UpdateInfo): Long {
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Chore Tracker ${info.versionName}")
            .setDescription("Downloading update…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "chore-tracker-${info.versionCode}.apk",
            )
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    /** Suspend until the given download finishes (or fails). */
    suspend fun awaitDownload(downloadId: Long): DownloadResult = withContext(Dispatchers.IO) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        while (true) {
            dm.query(query).use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIdx)
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> return@withContext DownloadResult.Success
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            return@withContext DownloadResult.Failure("reason ${cursor.getInt(reasonIdx)}")
                        }
                    }
                } else return@withContext DownloadResult.Failure("download disappeared")
            }
            delay(500)
        }
        @Suppress("UNREACHABLE_CODE")
        DownloadResult.Failure("unreachable")
    }

    /** Launch the system installer for the just-downloaded APK. */
    fun launchInstall(downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(downloadId) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val notes: String?,
)

sealed class DownloadResult {
    object Success : DownloadResult()
    data class Failure(val reason: String) : DownloadResult()
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("assets") val assets: List<Asset> = emptyList(),
)

@Serializable
private data class Asset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val url: String,
)

private data class ParsedVersion(val code: Int, val name: String)

/** Parse `v0.1.42+42` → ParsedVersion(code=42, name="0.1.42"). */
private fun parseVersionFromTag(tag: String): ParsedVersion? {
    val cleaned = tag.removePrefix("v")
    val plus = cleaned.lastIndexOf('+')
    if (plus < 1 || plus == cleaned.length - 1) return null
    val name = cleaned.substring(0, plus)
    val code = cleaned.substring(plus + 1).toIntOrNull() ?: return null
    return ParsedVersion(code, name)
}
