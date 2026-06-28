package com.nearexpiry.manager.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the project's GitHub Releases for a newer build and, if found,
 * downloads the signed APK and hands it to the system installer.
 *
 * Android can't silently update a sideloaded app — the system installer
 * prompt always appears — but this automates the check + download so the
 * user just taps "Install".
 *
 * Requires:
 *  • REQUEST_INSTALL_PACKAGES permission (manifest)
 *  • a FileProvider authority "<package>.fileprovider"
 *  • the downloaded APK to be signed with the SAME release key as the
 *    installed app (it is — every CI build uses the fixed keystore), so it
 *    installs as an in-place update.
 */
object AppUpdater {

    // GitHub repo that publishes the releases.
    private const val OWNER = "acbikram"
    private const val REPO = "Near-exp"
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    data class UpdateInfo(
        val versionName: String,   // e.g. "1.3"
        val versionCode: Long,     // parsed from the release (tag or body)
        val apkUrl: String,        // browser_download_url of the .apk asset
        val notes: String          // release body / changelog
    )

    sealed class CheckResult {
        data class UpdateAvailable(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        object NoRelease : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    /**
     * Queries the latest release and compares to [currentVersionCode].
     *
     * Version comparison uses versionCode. The release must encode it; we
     * read it from the tag if it's of the form "v1.3+4" or a "versionCode: N"
     * line in the body, else we fall back to comparing the numeric parts of
     * the versionName tag against [currentVersionName].
     */
    suspend fun check(
        currentVersionCode: Long,
        currentVersionName: String
    ): CheckResult = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "NearExpiry-Updater")
                connectTimeout = 15000
                readTimeout = 15000
            }
            val code = conn.responseCode
            if (code == 404) return@withContext CheckResult.NoRelease
            if (code != 200) return@withContext CheckResult.Error("GitHub returned HTTP $code")

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tag = json.optString("tag_name", json.optString("name", ""))
            val notes = json.optString("body", "")
            // Find the first .apk asset.
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url", null)
                        break
                    }
                }
            }
            if (apkUrl.isNullOrBlank()) return@withContext CheckResult.NoRelease

            val releaseVersionCode = parseVersionCode(tag, notes)
            val releaseVersionName = tag.removePrefix("v").substringBefore("+").trim()

            val isNewer = if (releaseVersionCode != null) {
                releaseVersionCode > currentVersionCode
            } else {
                compareVersionNames(releaseVersionName, currentVersionName) > 0
            }

            if (isNewer) {
                CheckResult.UpdateAvailable(
                    UpdateInfo(
                        versionName = releaseVersionName.ifBlank { tag },
                        versionCode = releaseVersionCode ?: 0L,
                        apkUrl = apkUrl,
                        notes = notes
                    )
                )
            } else {
                CheckResult.UpToDate
            }
        } catch (e: Exception) {
            CheckResult.Error(e.message ?: "Update check failed")
        }
    }

    /** Reads a versionCode from a "v1.3+4" tag suffix or a "versionCode: N" body line. */
    private fun parseVersionCode(tag: String, body: String): Long? {
        Regex("""\+(\d+)""").find(tag)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        Regex("""versionCode[:\s]+(\d+)""", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        return null
    }

    /** Compares dotted version names (e.g. "1.3" vs "1.2"). Returns >0 if a>b. */
    private fun compareVersionNames(a: String, b: String): Int {
        val pa = a.split(".").mapNotNull { it.toIntOrNull() }
        val pb = b.split(".").mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    /**
     * Downloads the APK to cache and launches the system installer.
     * [onProgress] reports 0f..1f. Throws on failure.
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "NearExpiry-Updater")
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = true
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("Download failed: HTTP ${conn.responseCode}")
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L

        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(dir, "near-expiry-update.apk")
        if (apkFile.exists()) apkFile.delete()

        conn.inputStream.use { input ->
            apkFile.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var downloaded = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) onProgress(downloaded.toFloat() / total)
                }
            }
        }

        // Launch the system installer via FileProvider (Android 7+).
        val apkUri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        withContext(Dispatchers.Main) {
            context.startActivity(installIntent)
        }
    }
}
