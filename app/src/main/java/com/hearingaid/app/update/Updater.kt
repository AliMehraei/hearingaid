package com.hearingaid.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.hearingaid.app.AppInfo
import com.hearingaid.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class Release(
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkName: String,
    val checksumsUrl: String?,
)

/** A failure with a message the person can act on; [detail] is shown after it for bug reports. */
class UpdateException(@StringRes val messageRes: Int, val detail: String = "") : Exception(detail)

/**
 * Finds, downloads, verifies and installs a newer Hearing Aid from GitHub releases.
 * A release must ship `hearingaid-<version>.apk` and `SHA256SUMS.txt`; the APK is only handed
 * to Android's installer when its SHA-256 matches. Android itself then refuses the update unless
 * it is signed with the same key as the installed app.
 */
object Updater {
    private const val FEED = "https://api.github.com/repos/AliMehraei/hearingaid/releases/latest"
    private const val CHECKSUMS = "SHA256SUMS.txt"
    private const val TIMEOUT_MS = 20_000

    fun apkName(version: String) = "hearingaid-${Versions.normalize(version)}.apk"

    suspend fun fetchLatest(): Release = withContext(Dispatchers.IO) {
        val json = try {
            open(FEED, accept = "application/vnd.github+json").use { it.readBytes().decodeToString() }
        } catch (e: IOException) {
            throw UpdateException(R.string.upd_err_network, e.message.orEmpty())
        }
        parse(json)
    }

    private fun parse(json: String): Release {
        val root = JSONObject(json)
        val tag = root.optString("tag_name")
        if (Versions.parse(tag) == null) throw UpdateException(R.string.upd_err_no_apk, "tag \"$tag\"")
        val version = Versions.normalize(tag)
        val wanted = apkName(version)
        var apkUrl: String? = null
        var sumsUrl: String? = null
        val assets = root.optJSONArray("assets")
        for (i in 0 until (assets?.length() ?: 0)) {
            val asset = assets!!.getJSONObject(i)
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url").takeIf { it.isNotEmpty() }
            if (name.equals(wanted, ignoreCase = true)) apkUrl = url
            if (name.equals(CHECKSUMS, ignoreCase = true)) sumsUrl = url
        }
        val page = root.optString("html_url").ifEmpty { "${AppInfo.GITHUB_URL}/releases/latest" }
        return Release(version, page, apkUrl, wanted, sumsUrl)
    }

    /** Downloads the APK into the app's cache, reporting 0–100, and verifies it against SHA256SUMS.txt. */
    suspend fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val apkUrl = release.apkUrl ?: throw UpdateException(R.string.upd_err_no_apk)
            val sumsUrl = release.checksumsUrl ?: throw UpdateException(R.string.upd_err_checksum, "$CHECKSUMS missing")
            val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
            val target = File(dir, release.apkName)
            try {
                val expected = open(sumsUrl).use { it.readBytes().decodeToString() }
                    .lineSequence()
                    .map { it.trim().split(Regex("\\s+"), limit = 2) }
                    .firstOrNull { it.size == 2 && it[1].trimStart('*').equals(release.apkName, ignoreCase = true) }
                    ?.get(0)?.lowercase()
                    ?: throw UpdateException(R.string.upd_err_checksum, "${release.apkName} not listed")

                val connection = connect(apkUrl)
                val total = connection.contentLengthLong
                val digest = MessageDigest.getInstance("SHA-256")
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            val percent = if (total > 0) (done * 100 / total).toInt() else 0
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) { onProgress(percent) }
                            }
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (actual != expected) {
                    target.delete()
                    throw UpdateException(R.string.upd_err_checksum)
                }
                target
            } catch (e: IOException) {
                target.delete()
                throw UpdateException(R.string.upd_err_network, e.message.orEmpty())
            }
        }

    /**
     * Hands the APK to Android's installer. Returns false when the person must first allow this app
     * to install apps; the system settings page for that is opened instead.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(settings)
            } catch (_: ActivityNotFoundException) {
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun connect(url: String, accept: String? = null): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "hearingaid/${AppInfo.version}")
            if (accept != null) setRequestProperty("Accept", accept)
        }
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")
        return connection
    }

    private fun open(url: String, accept: String? = null) = connect(url, accept).inputStream
}
