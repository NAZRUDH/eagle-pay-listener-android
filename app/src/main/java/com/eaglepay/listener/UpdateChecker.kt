package com.eaglepay.listener

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Phase 2: Checks GitHub releases for a newer APK version.
 * Compares current versionCode with latest release tag on GitHub.
 */
object UpdateChecker {

    private const val TAG = "EaglePayUpdate"
    private const val GITHUB_API = "https://api.github.com/repos/nrudheen3-coder/eagle-upi-hub/releases/latest"
    private const val APK_DOWNLOAD = "https://github.com/nrudheen3-coder/eagle-upi-hub/releases/latest/download/app-debug.apk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    data class Release(
        val tag_name: String = "",
        val name: String = "",
        val body: String = "",
        val html_url: String = "",
    )

    data class UpdateInfo(
        val available: Boolean,
        val latestVersion: String,
        val currentVersion: String,
        val releaseNotes: String,
        val downloadUrl: String,
    )

    /**
     * Check for updates. Runs on background thread — do not call on main thread.
     * Returns null if check fails or no internet.
     */
    fun check(ctx: Context): UpdateInfo? {
        val currentVersion = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }

        return try {
            val req = Request.Builder()
                .url(GITHUB_API)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                val release = gson.fromJson(body, Release::class.java)
                val latestVersion = release.tag_name.removePrefix("v").trim()

                UpdateInfo(
                    available = isNewer(latestVersion, currentVersion),
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    releaseNotes = release.body.take(300),
                    downloadUrl = APK_DOWNLOAD,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed: ${e.message}")
            null
        }
    }

    /** Open browser to download latest APK */
    fun openDownload(ctx: Context) {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(APK_DOWNLOAD)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** Simple version comparison: "2.1.0" > "2.0.0" */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
