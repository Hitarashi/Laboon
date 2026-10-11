package org.shilpo.laboon.update

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.update.GithubUpdateRepository.Companion.parseRelease
import org.shilpo.laboon.update.GithubUpdateRepository.Companion.resolveApkAsset
import org.shilpo.laboon.update.GithubUpdateRepository.ReleaseAsset

class AppUpdateTest {

    // ---------------------------------------------------------
    // 1. SemVer comparison tests
    // ---------------------------------------------------------

    @Test
    fun semVer_0_2_2_greaterThan_0_2_1() {
        val v022 = requireNotNull(SemVer.parse("0.2.2"))
        val v021 = requireNotNull(SemVer.parse("0.2.1"))

        assertTrue("0.2.2 should be greater than 0.2.1", v022 > v021)
        assertTrue(SemVer.isUpdateAvailable(latest = "0.2.2", current = "0.2.1"))
    }

    @Test
    fun semVer_0_2_1_equals_0_2_1() {
        val v1 = requireNotNull(SemVer.parse("0.2.1"))
        val v2 = requireNotNull(SemVer.parse("0.2.1"))

        assertEquals(0, v1.compareTo(v2))
        assertEquals(v1, v2)
        assertFalse(SemVer.isUpdateAvailable(latest = "0.2.1", current = "0.2.1"))
    }

    @Test
    fun semVer_v0_2_2_greaterThan_0_2_1() {
        val vLatest = requireNotNull(SemVer.parse("v0.2.2"))
        val vCurrent = requireNotNull(SemVer.parse("0.2.1"))

        assertTrue("v0.2.2 should be greater than 0.2.1", vLatest > vCurrent)
        assertTrue(SemVer.isUpdateAvailable(latest = "v0.2.2", current = "0.2.1"))
    }

    @Test
    fun semVer_0_3_0_greaterThan_0_2_9() {
        val v030 = requireNotNull(SemVer.parse("0.3.0"))
        val v029 = requireNotNull(SemVer.parse("0.2.9"))

        assertTrue("0.3.0 should be greater than 0.2.9", v030 > v029)
        assertTrue(SemVer.isUpdateAvailable(latest = "0.3.0", current = "0.2.9"))
    }

    @Test
    fun semVer_1_0_0_greaterThan_0_9_9() {
        val v100 = requireNotNull(SemVer.parse("1.0.0"))
        val v099 = requireNotNull(SemVer.parse("0.9.9"))

        assertTrue("1.0.0 should be greater than 0.9.9", v100 > v099)
        assertTrue(SemVer.isUpdateAvailable(latest = "1.0.0", current = "0.9.9"))
    }

    @Test
    fun semVer_1_0_0_rc1_lessThan_1_0_0() {
        val vRc = requireNotNull(SemVer.parse("1.0.0-rc1"))
        val vRelease = requireNotNull(SemVer.parse("1.0.0"))

        assertTrue("1.0.0-rc1 should be less than 1.0.0", vRc < vRelease)
        assertFalse(SemVer.isUpdateAvailable(latest = "1.0.0-rc1", current = "1.0.0"))
        assertTrue(SemVer.isUpdateAvailable(latest = "1.0.0", current = "1.0.0-rc1"))
    }

    @Test
    fun semVer_preReleaseComparisons() {
        val rc1 = requireNotNull(SemVer.parse("1.0.0-rc.1"))
        val rc2 = requireNotNull(SemVer.parse("1.0.0-rc.2"))
        val alpha = requireNotNull(SemVer.parse("1.0.0-alpha"))
        val beta = requireNotNull(SemVer.parse("1.0.0-beta"))

        assertTrue(rc1 < rc2)
        assertTrue(alpha < beta)
        assertTrue(beta < rc1)
    }

    @Test
    fun semVer_parseInvalidStringReturnsNull() {
        assertNull(SemVer.parse("invalid"))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("abc.def.ghi"))
    }

    // ---------------------------------------------------------
    // 2. AppUpdatePreferences tests
    // ---------------------------------------------------------

    @Test
    fun preferences_freshStateDefaults() {
        val fakePrefs = FakeSharedPreferences()
        val prefs = AppUpdatePreferences(fakePrefs)

        assertEquals(0L, prefs.lastCheckTimestamp)
        assertNull(prefs.lastNotifiedVersion)
        assertTrue(prefs.autoCheckEnabled)
        assertTrue("Should auto check when never checked before", prefs.shouldAutoCheck())
    }

    @Test
    fun preferences_cooldownLogic() {
        val fakePrefs = FakeSharedPreferences()
        val prefs = AppUpdatePreferences(fakePrefs)
        val now = System.currentTimeMillis()

        prefs.recordCheckTime(now)
        assertEquals(now, prefs.lastCheckTimestamp)

        // Inside cooldown (1000ms cooldown, elapsed ~0ms)
        assertFalse(prefs.shouldAutoCheck(cooldownMs = 60_000L))

        // Simulated time past cooldown
        prefs.recordCheckTime(now - 100_000L)
        assertTrue(prefs.shouldAutoCheck(cooldownMs = 60_000L))

        // When autoCheck is disabled, always returns false
        prefs.autoCheckEnabled = false
        assertFalse(prefs.shouldAutoCheck(cooldownMs = 60_000L))
    }

    @Test
    fun preferences_lastNotifiedVersionPersistence() {
        val fakePrefs = FakeSharedPreferences()
        val prefs = AppUpdatePreferences(fakePrefs)

        assertNull(prefs.lastNotifiedVersion)

        prefs.lastNotifiedVersion = "0.2.2"
        assertEquals("0.2.2", prefs.lastNotifiedVersion)

        // Create new instance over same storage
        val prefsReloaded = AppUpdatePreferences(fakePrefs)
        assertEquals("0.2.2", prefsReloaded.lastNotifiedVersion)

        prefs.lastNotifiedVersion = null
        assertNull(prefs.lastNotifiedVersion)
    }

    // ---------------------------------------------------------
    // 3. Release JSON parsing and ABI asset selection tests
    // ---------------------------------------------------------

    @Test
    fun releaseParsing_completeJson() {
        val json = JSONObject().apply {
            put("tag_name", "v0.2.2")
            put("name", "Laboon 0.2.2 Release")
            put("body", "Bug fixes and improvements")
            put("html_url", "https://github.com/hitarashi/Laboon/releases/tag/v0.2.2")
            put("published_at", "2026-10-10T12:00:00Z")
            put(
                "assets",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("name", "Laboon-arm64-v8a-release.apk")
                            put("size", 15_000_000L)
                            put("browser_download_url", "https://downloads.example/arm64.apk")
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("name", "Laboon-universal-release.apk")
                            put("size", 30_000_000L)
                            put("browser_download_url", "https://downloads.example/universal.apk")
                        }
                    )
                }
            )
        }

        val release = parseRelease(json, supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a"))
        assertNotNull(release)
        assertEquals("v0.2.2", release!!.tagName)
        assertEquals("0.2.2", release.versionName)
        assertEquals("Laboon 0.2.2 Release", release.name)
        assertEquals("Bug fixes and improvements", release.body)
        assertEquals("https://github.com/hitarashi/Laboon/releases/tag/v0.2.2", release.htmlUrl)
        assertEquals("https://downloads.example/arm64.apk", release.downloadUrl)
        assertEquals(15_000_000L, release.apkSizeBytes)
        assertEquals("2026-10-10T12:00:00Z", release.publishedAt)
    }

    @Test
    fun abiAssetSelection_priorityResolution() {
        val assets = listOf(
            ReleaseAsset("Laboon-universal-release.apk", 30_000_000L, "https://dl.example/universal.apk"),
            ReleaseAsset("Laboon-armeabi-v7a-release.apk", 12_000_000L, "https://dl.example/armv7.apk"),
            ReleaseAsset("Laboon-arm64-v8a-release.apk", 15_000_000L, "https://dl.example/arm64.apk"),
            ReleaseAsset("Laboon-x86_64-release.apk", 16_000_000L, "https://dl.example/x86_64.apk"),
            ReleaseAsset("Laboon-x86-release.apk", 13_000_000L, "https://dl.example/x86.apk"),
        )

        // 1. Device with arm64 prefers arm64 over armeabi-v7a
        val arm64Choice = resolveApkAsset(assets, supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a"))
        assertNotNull(arm64Choice)
        assertEquals("Laboon-arm64-v8a-release.apk", arm64Choice!!.name)

        // 2. Device with only armeabi-v7a selects armeabi-v7a
        val armv7Choice = resolveApkAsset(assets, supportedAbis = arrayOf("armeabi-v7a"))
        assertNotNull(armv7Choice)
        assertEquals("Laboon-armeabi-v7a-release.apk", armv7Choice!!.name)

        // 3. Device with x86_64 selects x86_64
        val x8664Choice = resolveApkAsset(assets, supportedAbis = arrayOf("x86_64"))
        assertNotNull(x8664Choice)
        assertEquals("Laboon-x86_64-release.apk", x8664Choice!!.name)

        // 4. Device with 32-bit x86 selects x86 and does not mistake x86_64 for x86
        val x86Choice = resolveApkAsset(assets, supportedAbis = arrayOf("x86"))
        assertNotNull(x86Choice)
        assertEquals("Laboon-x86-release.apk", x86Choice!!.name)

        // 5. Unsupported ABI falls back to universal
        val unknownAbiChoice = resolveApkAsset(assets, supportedAbis = arrayOf("mips", "riscv64"))
        assertNotNull(unknownAbiChoice)
        assertEquals("Laboon-universal-release.apk", unknownAbiChoice!!.name)
    }

    @Test
    fun abiAssetSelection_fallbacks() {
        // Fallback to any APK when no ABI and no universal
        val singleApk = listOf(
            ReleaseAsset("Laboon-standalone.apk", 20_000_000L, "https://dl.example/standalone.apk")
        )
        val fallbackApk = resolveApkAsset(singleApk, supportedAbis = arrayOf("arm64-v8a"))
        assertNotNull(fallbackApk)
        assertEquals("Laboon-standalone.apk", fallbackApk!!.name)

        // Fallback to html_url when no APK assets exist at all
        val jsonNoApk = JSONObject().apply {
            put("tag_name", "v0.2.2")
            put("html_url", "https://github.com/hitarashi/Laboon/releases/tag/v0.2.2")
            put(
                "assets",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("name", "source_code.tar.gz")
                            put("size", 1_000_000L)
                            put("browser_download_url", "https://downloads.example/source.tar.gz")
                        }
                    )
                }
            )
        }

        val release = parseRelease(jsonNoApk, supportedAbis = arrayOf("arm64-v8a"))
        assertNotNull(release)
        assertEquals("https://github.com/hitarashi/Laboon/releases/tag/v0.2.2", release!!.downloadUrl)
        assertNull(release.apkSizeBytes)
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)
    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = (data[key] as? Number)?.toInt() ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (data[key] as? Number)?.toLong() ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (data[key] as? Number)?.toFloat() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(data)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FakeEditor(private val backing: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val staged = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
            staged[key] = value
            removed.remove(key)
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            staged[key] = values?.toSet()
            removed.remove(key)
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply {
            staged[key] = value
            removed.remove(key)
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply {
            staged[key] = value
            removed.remove(key)
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply {
            staged[key] = value
            removed.remove(key)
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply {
            staged[key] = value
            removed.remove(key)
        }

        override fun remove(key: String): SharedPreferences.Editor = apply {
            removed.add(key)
            staged.remove(key)
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clear = true
            staged.clear()
            removed.clear()
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) backing.clear()
            removed.forEach { backing.remove(it) }
            staged.forEach { (k, v) ->
                if (v == null) backing.remove(k) else backing[k] = v
            }
        }
    }
}
