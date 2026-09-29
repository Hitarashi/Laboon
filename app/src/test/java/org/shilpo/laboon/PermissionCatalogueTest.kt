package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.permissions.PermissionCatalogue
import org.shilpo.laboon.permissions.PermissionIds
import org.shilpo.laboon.permissions.PermissionKind
import org.shilpo.laboon.permissions.PermissionSpec
import org.shilpo.laboon.permissions.PermissionState
import org.shilpo.laboon.permissions.canLeaveOnboarding
import org.shilpo.laboon.permissions.missingRequiredPermissions
import org.shilpo.laboon.permissions.requiredRuntimePermissions
import java.io.File

class PermissionCatalogueTest {

    @Test
    fun canLeaveOnboarding_isBlockedWhileARequiredRuntimeSpecIsUnsatisfied() {
        val catalogue = listOf(
            runtimeSpec("notif"),
            runtimeSpec("audio"),
        )

        assertFalse(canLeaveOnboarding(catalogue, FakePermissionState(granted = setOf("notif"))))
        assertFalse(canLeaveOnboarding(catalogue, FakePermissionState()))
        assertTrue(
            canLeaveOnboarding(
                catalogue,
                FakePermissionState(granted = setOf("notif", "audio"))
            )
        )
    }

    @Test
    fun canLeaveOnboarding_ignoresOptionalRuntimeSpecs() {
        val catalogue = listOf(
            runtimeSpec("notif"),
            runtimeSpec("bluetooth", isRequired = false),
        )

        assertTrue(canLeaveOnboarding(catalogue, FakePermissionState(granted = setOf("notif"))))
    }

    @Test
    fun canLeaveOnboarding_automaticRequiredSpecDoesNotBlockButRuntimeOneDoes() {
        val automatic = spec(
            id = "auto",
            kind = PermissionKind.Automatic,
            isRequired = true,
        )
        val runtime = runtimeSpec("runtime", isRequired = true)

        val onlyAutomatic = canLeaveOnboarding(listOf(automatic), FakePermissionState())
        val withRuntime = canLeaveOnboarding(listOf(automatic, runtime), FakePermissionState())

        assertTrue("Automatic permissions cannot be requested at runtime", onlyAutomatic)
        assertFalse("Runtime permissions must be granted to continue", withRuntime)
    }

    @Test
    fun canLeaveOnboarding_settingsRequiredSpecDoesNotBlock() {
        val settings = spec(
            id = PermissionIds.BATTERY,
            kind = PermissionKind.Settings,
            isRequired = true,
        )

        assertTrue(canLeaveOnboarding(listOf(settings), FakePermissionState()))
    }

    @Test
    fun canLeaveOnboarding_grantingNothingOnTheRealCatalogueIsBlocked() {
        assertFalse(canLeaveOnboarding(PermissionCatalogue, FakePermissionState()))
    }

    @Test
    fun canLeaveOnboarding_grantingEveryRequiredRuntimeSpecOnTheRealCatalogueUnblocks() {
        val state = FakePermissionState(
            granted = requiredRuntimePermissions(PermissionCatalogue).map { it.id }.toSet(),
        )

        assertTrue(canLeaveOnboarding(PermissionCatalogue, state))
    }

    @Test
    fun canLeaveOnboarding_rechecksAfterAStateChangeThatRevokesAGrant() {
        val state =
            FakePermissionState(granted = requiredRuntimePermissions(PermissionCatalogue).map { it.id }
                .toSet())
        assertTrue(canLeaveOnboarding(PermissionCatalogue, state))

        state.revokeAll()

        assertFalse(canLeaveOnboarding(PermissionCatalogue, state))
    }

    @Test
    fun missingRequiredPermissions_matchesTheBlockingSet() {
        val state = FakePermissionState(granted = setOf(PermissionIds.NOTIFICATIONS))

        val missing = missingRequiredPermissions(PermissionCatalogue, state::isSatisfied)

        assertEquals(listOf(PermissionIds.STORAGE), missing.map { it.id })
        assertFalse(canLeaveOnboarding(PermissionCatalogue, state))
    }

    @Test
    fun catalogue_hasNoDuplicateIds() {
        val ids = PermissionCatalogue.map { it.id }

        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun catalogue_everyRuntimeSpecDeclaresAManifestPermissionThatExists() {
        val manifest = androidManifestText()
        val runtimeSpecs = PermissionCatalogue.filter { it.kind == PermissionKind.Runtime }

        assertTrue(runtimeSpecs.isNotEmpty())
        runtimeSpecs.forEach { spec ->
            val permission = spec.manifestPermission
            assertNotNull("${spec.id} must declare a manifest permission", permission)
            assertTrue(
                "${spec.id} declares $permission which is absent from AndroidManifest.xml",
                manifest.contains("android:name=\"$permission\""),
            )
        }
    }

    @Test
    fun catalogue_nonRuntimeSpecsCarryNoManifestRuntimeStrings() {
        val nonRuntime = PermissionCatalogue.filter { it.kind != PermissionKind.Runtime }

        assertTrue(nonRuntime.isNotEmpty())
        nonRuntime.forEach { spec ->
            assertTrue(
                "${spec.id} is ${spec.kind} but declares ${spec.manifestPermission}",
                spec.manifestPermission == null ||
                        spec.manifestPermission.startsWith("android.permission."),
            )
        }
    }

    @Test
    fun request_recordsTheSpecAndMakesItSatisfied() {
        val spec = runtimeSpec("notif")
        val state = FakePermissionState()

        assertFalse(state.isSatisfied(spec))
        state.request(spec)
        assertTrue(state.isSatisfied(spec))
        assertEquals(listOf(spec), state.requested)
    }

    private fun runtimeSpec(id: String, isRequired: Boolean = true) = spec(
        id = id,
        kind = PermissionKind.Runtime,
        isRequired = isRequired,
    )

    private fun spec(id: String, kind: PermissionKind, isRequired: Boolean) = PermissionSpec(
        id = id,
        manifestPermission = if (kind == PermissionKind.Runtime) "android.permission.TEST_$id" else null,
        kind = kind,
        titleRes = 0,
        descriptionRes = 0,
        iconRes = 0,
        isRequired = isRequired,
    )

    private fun androidManifestText(): String {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        val manifest = candidates.firstOrNull { it.isFile }
            ?: error(
                "AndroidManifest.xml not found from ${File(".").absolutePath}; " +
                        "tried ${candidates.joinToString { it.path }}",
            )
        return manifest.readText()
    }

    private class FakePermissionState(
        granted: Set<String> = emptySet(),
    ) : PermissionState {

        private val granted: MutableSet<String> = granted.toMutableSet()

        val requested = mutableListOf<PermissionSpec>()

        override fun isSatisfied(spec: PermissionSpec): Boolean = spec.id in granted

        override fun request(spec: PermissionSpec) {
            requested += spec
            granted += spec.id
        }

        fun revokeAll() {
            granted.clear()
        }
    }
}
