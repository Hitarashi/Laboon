package org.shilpo.laboon.update

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
    val buildMetadata: String? = null,
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        val p1 = preRelease
        val p2 = other.preRelease
        if (p1 == null) {
            return if (p2 == null) 0 else 1
        }
        if (p2 == null) {
            return -1
        }
        return comparePreRelease(p1, p2)
    }

    override fun toString(): String {
        val base = "$major.$minor.$patch"
        val withPre = if (preRelease != null) "$base-$preRelease" else base
        return if (buildMetadata != null) "$withPre+$buildMetadata" else withPre
    }

    companion object {
        private val SEMVER_REGEX = Regex(
            """^[vV]?(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+([0-9A-Za-z.-]+))?$"""
        )

        fun parse(text: String): SemVer? {
            val trimmed = text.trim()
            val match = SEMVER_REGEX.matchEntire(trimmed) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].ifEmpty { "0" }.toIntOrNull() ?: return null
            val preRelease = match.groupValues[4].ifEmpty { null }
            val buildMetadata = match.groupValues[5].ifEmpty { null }
            return SemVer(major, minor, patch, preRelease, buildMetadata)
        }

        fun isUpdateAvailable(latest: String, current: String): Boolean {
            val latestVer = parse(latest) ?: return false
            val currentVer = parse(current) ?: return false
            return latestVer > currentVer
        }

        private fun comparePreRelease(p1: String, p2: String): Int {
            val parts1 = p1.split(".")
            val parts2 = p2.split(".")
            val minLen = minOf(parts1.size, parts2.size)
            for (i in 0 until minLen) {
                val id1 = parts1[i]
                val id2 = parts2[i]
                if (id1 == id2) continue
                val num1 = id1.toLongOrNull()
                val num2 = id2.toLongOrNull()
                return when {
                    num1 != null && num2 != null -> num1.compareTo(num2)
                    num1 != null && num2 == null -> -1
                    num1 == null && num2 != null -> 1
                    else -> id1.compareTo(id2)
                }
            }
            return parts1.size.compareTo(parts2.size)
        }
    }
}

data class AppRelease(
    val tagName: String,
    val versionName: String = tagName.removePrefix("v").removePrefix("V"),
    val name: String,
    val body: String,
    val htmlUrl: String,
    val downloadUrl: String?,
    val publishedAt: String,
    val apkSizeBytes: Long? = null,
)

sealed interface UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Available(val currentVersion: String, val release: AppRelease) : UpdateCheckResult
    data class Failed(val error: String) : UpdateCheckResult
}
