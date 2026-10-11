package org.shilpo.laboon.update

import android.os.Build
import org.json.JSONObject
import org.shilpo.laboon.BuildConfig
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome
import org.shilpo.laboon.net.getOrNull

interface AppUpdateRepository {
    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckResult
    suspend fun fetchLatestRelease(): AppRelease?
}

class GithubUpdateRepository(
    private val httpClient: HttpJsonClient = HttpJsonClient(),
    private val preferences: AppUpdatePreferences,
    private val owner: String = DEFAULT_OWNER,
    private val repo: String = DEFAULT_REPO,
    private val supportedAbisProvider: () -> Array<String> = { getSupportedAbisSafe() },
    private val currentVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
) : AppUpdateRepository {

    override suspend fun checkForUpdate(force: Boolean): UpdateCheckResult {
        val currentVersion = currentVersionProvider()
        if (!force && !preferences.shouldAutoCheck()) {
            return UpdateCheckResult.UpToDate(currentVersion)
        }

        val url = latestReleaseUrl()
        val headers = mapOf("User-Agent" to "Laboon/$currentVersion")
        return when (val outcome = httpClient.getJson(url, headers)) {
            is HttpOutcome.Failure -> {
                UpdateCheckResult.Failed(outcome.error.message)
            }
            is HttpOutcome.Success -> {
                preferences.recordCheckTime()
                val release = parseRelease(outcome.value, supportedAbisProvider())
                    ?: return UpdateCheckResult.Failed("Failed to parse release information")
                if (SemVer.isUpdateAvailable(release.versionName, currentVersion)) {
                    UpdateCheckResult.Available(currentVersion, release)
                } else {
                    UpdateCheckResult.UpToDate(currentVersion)
                }
            }
        }
    }

    override suspend fun fetchLatestRelease(): AppRelease? {
        val currentVersion = currentVersionProvider()
        val url = latestReleaseUrl()
        val headers = mapOf("User-Agent" to "Laboon/$currentVersion")
        val outcome = httpClient.getJson(url, headers)
        val json = outcome.getOrNull() ?: return null
        return parseRelease(json, supportedAbisProvider())
    }

    private fun latestReleaseUrl(): String =
        "https://api.github.com/repos/$owner/$repo/releases/latest"

    data class ReleaseAsset(
        val name: String,
        val size: Long,
        val downloadUrl: String,
    )

    companion object {
        const val DEFAULT_OWNER = "hitarashi"
        const val DEFAULT_REPO = "Laboon"

        fun getSupportedAbisSafe(): Array<String> = try {
            Build.SUPPORTED_ABIS ?: emptyArray()
        } catch (_: Throwable) {
            emptyArray()
        }

        fun parseRelease(
            json: JSONObject,
            supportedAbis: Array<String> = getSupportedAbisSafe(),
        ): AppRelease? {
            val tagName = json.optString("tag_name").trim()
            if (tagName.isEmpty()) return null

            val name = json.optString("name").trim().ifEmpty { tagName }
            val body = json.optString("body")
            val htmlUrl = json.optString("html_url")
            val publishedAt = json.optString("published_at")

            val assetsJson = json.optJSONArray("assets")
            val assets = mutableListOf<ReleaseAsset>()
            if (assetsJson != null) {
                for (i in 0 until assetsJson.length()) {
                    val assetObj = assetsJson.optJSONObject(i) ?: continue
                    val assetName = assetObj.optString("name")
                    val downloadUrl = assetObj.optString("browser_download_url")
                    val size = assetObj.optLong("size", 0L)
                    if (assetName.isNotBlank() && downloadUrl.isNotBlank()) {
                        assets.add(ReleaseAsset(assetName, size, downloadUrl))
                    }
                }
            }

            val selected = resolveApkAsset(assets, supportedAbis)
            val downloadUrl = selected?.downloadUrl ?: htmlUrl.ifEmpty { null }
            val apkSizeBytes = selected?.size

            return AppRelease(
                tagName = tagName,
                versionName = tagName.removePrefix("v").removePrefix("V"),
                name = name,
                body = body,
                htmlUrl = htmlUrl,
                downloadUrl = downloadUrl,
                publishedAt = publishedAt,
                apkSizeBytes = apkSizeBytes,
            )
        }

        fun resolveApkAsset(
            assets: List<ReleaseAsset>,
            supportedAbis: Array<String> = getSupportedAbisSafe(),
        ): ReleaseAsset? {
            val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAssets.isEmpty()) return null

            // 1. Match supported ABIs in priority order
            for (abi in supportedAbis) {
                val match = apkAssets.firstOrNull { matchesAbi(it.name, abi) }
                if (match != null) return match
            }

            // 2. Fall back to universal APK
            val universal = apkAssets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            if (universal != null) return universal

            // 3. Fall back to any APK
            return apkAssets.firstOrNull()
        }

        private fun matchesAbi(assetName: String, abi: String): Boolean {
            val lower = assetName.lowercase()
            val targetAbi = abi.lowercase()
            if (!lower.contains(targetAbi)) return false
            if (targetAbi == "x86" && (lower.contains("x86_64") || lower.contains("x86-64"))) {
                val stripped = lower.replace("x86_64", "").replace("x86-64", "")
                return stripped.contains("x86")
            }
            if (targetAbi == "armeabi" && (lower.contains("armeabi-v7a") || lower.contains("armeabi-v8a"))) {
                val stripped = lower.replace("armeabi-v7a", "").replace("armeabi-v8a", "")
                return stripped.contains("armeabi")
            }
            return true
        }
    }
}
