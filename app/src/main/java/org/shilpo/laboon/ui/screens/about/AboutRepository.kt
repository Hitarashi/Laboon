package org.shilpo.laboon.ui.screens.about

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.shilpo.laboon.BuildConfig
import org.shilpo.laboon.net.HttpJsonClient
import org.shilpo.laboon.net.HttpOutcome

/**
 * Contributor metadata fetched from GitHub.
 */
data class Contributor(
    val login: String,
    val name: String?,
    val avatarUrl: String,
    val htmlUrl: String,
    val contributions: Int,
    val role: String,
)

/**
 * Aggregated about information combining runtime build specs, remote GitHub contributor data,
 * and current release changelog.
 */
data class AboutData(
    val versionName: String,
    val leadDeveloper: Contributor,
    val contributors: List<Contributor>,
    val releaseTag: String,
    val changelogItems: List<String>,
)

/**
 * Seam for fetching about and contributor data.
 * Adheres to deep module principles: small interface, rich internal logic, high leverage.
 */
interface AboutRepository {
    suspend fun getAboutData(): AboutData
}

/**
 * Deep implementation coordinating BuildConfig metadata with GitHub REST APIs,
 * in-memory caching, changelog parsing, and resilient offline fallbacks.
 */
class GithubAboutRepository(
    private val httpClient: HttpJsonClient = HttpJsonClient(),
) : AboutRepository {

    private var cachedData: AboutData? = null

    override suspend fun getAboutData(): AboutData = withContext(Dispatchers.IO) {
        cachedData?.let { return@withContext it }

        val leadDevFallback = Contributor(
            login = "sayeed205",
            name = "Hitarashi",
            avatarUrl = "https://avatars.githubusercontent.com/u/63891956?v=4",
            htmlUrl = "https://github.com/sayeed205",
            contributions = 66,
            role = "Lead Architect & Developer",
        )

        val defaultChangelog = listOf(
            "Implement automatic background track ripping, availability caching, and sync pipeline",
            "Include client metadata in auth exchange and add lyric API URL session support",
            "Implement album auto-ripping, availability lookup, and cache management",
            "Implement album search support, catalog resolution, and player album navigation",
            "Implement comprehensive artist details screen, caching repository, and catalog search integration",
            "Implement advanced search filters, playlist/station support, and swipe-to-dismiss queue items",
            "Implement record label details screen, repository, and auto-rip support",
            "Enhance liquid glass backdrop rendering and scroll-driven synchronization",
            "Refactor queue drag reordering, add track format badges, and expressive settings screen",
        )

        val leadDev = fetchUser("sayeed205") ?: leadDevFallback
        val fetchedContributors = fetchContributors("hitarashi", "Laboon")
        val contributorsList = if (fetchedContributors.isNotEmpty()) {
            fetchedContributors
        } else {
            listOf(leadDev)
        }

        val (releaseTag, changelogList) = fetchLatestRelease("hitarashi", "Laboon") ?: (
                BuildConfig.VERSION_NAME to defaultChangelog
                )

        val data = AboutData(
            versionName = BuildConfig.VERSION_NAME,
            leadDeveloper = leadDev,
            contributors = contributorsList,
            releaseTag = releaseTag,
            changelogItems = if (changelogList.isNotEmpty()) changelogList else defaultChangelog,
        )

        cachedData = data
        data
    }

    private suspend fun fetchUser(username: String): Contributor? {
        val outcome = httpClient.getJson(
            url = "https://api.github.com/users/$username",
            headers = mapOf("User-Agent" to "Laboon-Android"),
        )
        return when (outcome) {
            is HttpOutcome.Success -> {
                val json = outcome.value
                val login = json.optString("login", username)
                val name = json.optString("name").takeIf { it.isNotBlank() }
                val avatarUrl = json.optString("avatar_url", "")
                val htmlUrl = json.optString("html_url", "https://github.com/$username")
                Contributor(
                    login = login,
                    name = name,
                    avatarUrl = avatarUrl,
                    htmlUrl = htmlUrl,
                    contributions = 66,
                    role = "Lead Architect & Developer",
                )
            }

            is HttpOutcome.Failure -> null
        }
    }

    private suspend fun fetchContributors(owner: String, repo: String): List<Contributor> {
        val outcome = httpClient.get(
            url = "https://api.github.com/repos/$owner/$repo/contributors",
            headers = mapOf("User-Agent" to "Laboon-Android"),
        )
        return when (outcome) {
            is HttpOutcome.Success -> {
                try {
                    val array = JSONArray(outcome.value)
                    val result = mutableListOf<Contributor>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val login = obj.optString("login")
                        val avatarUrl = obj.optString("avatar_url")
                        val htmlUrl = obj.optString("html_url")
                        val contributions = obj.optInt("contributions", 0)
                        if (login.isNotBlank()) {
                            val isLead = login.equals("sayeed205", ignoreCase = true)
                            result.add(
                                Contributor(
                                    login = login,
                                    name = if (isLead) "Hitarashi" else null,
                                    avatarUrl = avatarUrl,
                                    htmlUrl = htmlUrl,
                                    contributions = contributions,
                                    role = if (isLead) "Lead Architect & Developer" else "Contributor",
                                )
                            )
                        }
                    }
                    result
                } catch (_: Exception) {
                    emptyList()
                }
            }

            is HttpOutcome.Failure -> emptyList()
        }
    }

    private suspend fun fetchLatestRelease(
        owner: String,
        repo: String
    ): Pair<String, List<String>>? {
        val outcome = httpClient.getJson(
            url = "https://api.github.com/repos/$owner/$repo/releases/latest",
            headers = mapOf("User-Agent" to "Laboon-Android"),
        )
        return when (outcome) {
            is HttpOutcome.Success -> {
                val json = outcome.value
                val tagName = json.optString("tag_name", BuildConfig.VERSION_NAME)
                val body = json.optString("body", "")
                val parsedItems = parseChangelogBody(body)
                tagName to parsedItems
            }

            is HttpOutcome.Failure -> null
        }
    }

    private fun parseChangelogBody(body: String): List<String> {
        return body.lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { line ->
                line.removePrefix("- ")
                    .removePrefix("* ")
                    .replace(Regex("—\\s*by\\s*@\\w+.*$"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
    }
}
