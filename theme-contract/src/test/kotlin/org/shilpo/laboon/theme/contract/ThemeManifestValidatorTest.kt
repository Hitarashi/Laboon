package org.shilpo.laboon.theme.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeManifestValidatorTest {
    @Test
    fun semanticActionIdsAreResolvedByThePublicContract() {
        assertEquals(VisualThemeAction.SEARCH, VisualThemeAction.fromId("search"))
        assertEquals(
            VisualThemeAction.SELECT_AUDIO_QUALITY,
            VisualThemeAction.fromId("selectAudioQuality")
        )
        assertEquals(VisualThemeAction.PLAY_QUEUE_ENTRY, VisualThemeAction.fromId("playQueueEntry"))
        assertEquals(VisualThemeAction.MOVE_QUEUE_ENTRY, VisualThemeAction.fromId("moveQueueEntry"))
        assertEquals(null, VisualThemeAction.fromId("executeExtensionCode"))
    }

    @Test
    fun acceptsAnApi29ThemeWithTheCurrentContract() {
        val result = ThemeManifestValidator.validate(validManifest(), androidApi = 29)

        assertTrue(result.isValid)
    }

    @Test
    fun reportsAnAndroid13ThemeAsUnsupportedOnAndroid10() {
        val result = ThemeManifestValidator.validate(
            validManifest(minimumAndroidApi = 33),
            androidApi = 29,
        )

        assertEquals(
            setOf(ThemeManifestIssue.UnsupportedAndroidVersion),
            result.issues,
        )
    }

    @Test
    fun rejectsUnknownContractVersionsBeforeLoadingAssets() {
        val result = ThemeManifestValidator.validate(
            validManifest(contractVersion = CURRENT_THEME_CONTRACT_VERSION + 1),
            androidApi = 33,
        )

        assertEquals(setOf(ThemeManifestIssue.UnsupportedContractVersion), result.issues)
    }

    @Test
    fun rejectsAbsoluteAndTraversalAssetPaths() {
        assertFalse(ThemeManifestValidator.isSafeAssetPath("/theme.json"))
        assertFalse(ThemeManifestValidator.isSafeAssetPath("../theme.json"))
        assertFalse(ThemeManifestValidator.isSafeAssetPath("assets/../theme.json"))
        assertTrue(ThemeManifestValidator.isSafeAssetPath("laboon/theme.json"))
    }

    @Test
    fun rejectsOversizedDisplayMetadata() {
        val result = ThemeManifestValidator.validate(
            validManifest().copy(name = "x".repeat(1_025)),
            androidApi = 33,
        )

        assertEquals(setOf(ThemeManifestIssue.MetadataTooLong), result.issues)
    }

    private fun validManifest(
        minimumAndroidApi: Int = 29,
        contractVersion: Int = CURRENT_THEME_CONTRACT_VERSION,
    ) = ThemeManifest(
        id = "theme.laboon.hitarashi",
        name = "Hitarashi",
        author = "Hitarashi",
        version = "1.0.0",
        contractVersion = contractVersion,
        minimumAndroidApi = minimumAndroidApi,
        definitionAsset = "laboon/theme.json",
    )
}
