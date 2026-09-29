package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.AuthUser
import org.shilpo.laboon.ui.design.ScreenError
import org.shilpo.laboon.ui.design.avatarBearerHeader
import org.shilpo.laboon.ui.design.avatarEndpoint
import org.shilpo.laboon.ui.design.avatarImageSpec
import org.shilpo.laboon.ui.design.avatarInitial
import org.shilpo.laboon.ui.design.mergeScreenErrors
import org.shilpo.laboon.ui.design.userDisplayName

class ScreenContentTest {

    private fun user(
        name: String? = null,
        username: String? = null,
        firstName: String? = null,
    ) = AuthUser(
        telegramId = 42L,
        name = name,
        username = username,
        firstName = firstName,
        lastName = null,
    )

    private fun session(
        serverUrl: String? = "https://stream.example.com",
        token: String? = "tok-123",
    ) = AuthSession(
        serverUrl = serverUrl.orEmpty(),
        token = token.orEmpty(),
        refreshToken = "refresh",
        expiresAtUnix = 0L,
        user = user(name = "Alice Smith"),
    )

    @Test
    fun avatarInitial_nullUser_returnsNull() {
        assertNull(avatarInitial(null))
    }

    @Test
    fun avatarInitial_allCandidatesNull_returnsNull() {
        assertNull(avatarInitial(user()))
    }

    @Test
    fun avatarInitial_singleCharName_returnsThatCharUppercased() {
        assertEquals('A', avatarInitial(user(name = "a")))
    }

    @Test
    fun avatarInitial_multiWordName_returnsFirstLetterOfFirstWord() {
        assertEquals('A', avatarInitial(user(name = "alice smith jones")))
    }

    @Test
    fun avatarInitial_blankName_fallsThroughToUsername() {
        assertEquals('B', avatarInitial(user(name = "", username = "bob")))
    }

    @Test
    fun avatarInitial_whitespaceOnlyName_fallsThroughToUsername() {
        assertEquals('B', avatarInitial(user(name = "   ", username = "bob")))
    }

    @Test
    fun avatarInitial_whitespaceOnlyNameAndUsername_fallsThroughToFirstName() {
        assertEquals('C', avatarInitial(user(name = " ", username = "\t\n", firstName = "carol")))
    }

    @Test
    fun avatarInitial_nameWithoutLetters_fallsThroughToUsername() {
        assertEquals('B', avatarInitial(user(name = "42 43", username = "bob")))
    }

    @Test
    fun avatarInitial_noCandidateHasALetter_returnsNull() {
        assertNull(avatarInitial(user(name = "123", username = "!!!", firstName = "  ")))
    }

    @Test
    fun avatarEndpoint_normalServerUrl_appendsTheAvatarPath() {
        assertEquals(
            "https://stream.example.com/api/v1/auth/me/avatar",
            avatarEndpoint("https://stream.example.com"),
        )
    }

    @Test
    fun avatarEndpoint_trailingSlash_isNotDoubled() {
        assertEquals(
            "https://stream.example.com/api/v1/auth/me/avatar",
            avatarEndpoint("https://stream.example.com/"),
        )
    }

    @Test
    fun avatarEndpoint_blankServerUrl_returnsNull() {
        assertNull(avatarEndpoint("   "))
        assertNull(avatarEndpoint("/"))
        assertNull(avatarEndpoint(null))
    }

    @Test
    fun avatarBearerHeader_token_isBearerPrefixed() {
        assertEquals("Bearer tok-123", avatarBearerHeader("tok-123"))
    }

    @Test
    fun avatarBearerHeader_blankToken_returnsNull() {
        assertNull(avatarBearerHeader("   "))
        assertNull(avatarBearerHeader(null))
    }

    @Test
    fun avatarImageSpec_validSession_carriesEndpointAndBearerHeader() {
        val spec = avatarImageSpec(session())

        assertEquals("https://stream.example.com/api/v1/auth/me/avatar", spec?.url)
        assertEquals("Bearer tok-123", spec?.authorization)
    }

    @Test
    fun avatarImageSpec_blankToken_producesNoRequest() {
        assertNull(avatarImageSpec(session(token = "  ")))
    }

    @Test
    fun avatarImageSpec_blankServerUrl_producesNoRequest() {
        assertNull(avatarImageSpec(session(serverUrl = " ")))
    }

    @Test
    fun avatarImageSpec_nullSession_producesNoRequest() {
        assertNull(avatarImageSpec(null))
    }

    @Test
    fun userDisplayName_prefersNameOverUsername_withoutAtSign() {
        assertEquals("Alice Smith", userDisplayName(user(name = "Alice Smith", username = "alice")))
    }

    @Test
    fun userDisplayName_blankName_fallsBackToBareUsernameWithoutAtSign() {
        assertEquals("alice", userDisplayName(user(name = "  ", username = "alice")))
    }

    @Test
    fun userDisplayName_whitespaceOnlyName_fallsBackToBareUsernameWithoutAtSign() {
        assertEquals("alice", userDisplayName(user(name = "", username = "alice")))
    }

    @Test
    fun userDisplayName_noNameOrUsername_returnsNull() {
        assertNull(userDisplayName(user(name = " ", username = null)))
        assertNull(userDisplayName(null))
    }

    @Test
    fun userDisplayName_cleansEmptyQuotesSuffix() {
        assertEquals("Hitarashi", userDisplayName(user(name = "Hitarashi (\"\");")))
        assertEquals("Hitarashi", userDisplayName(user(name = "Hitarashi (\"\")")))
        assertEquals("Hitarashi", userDisplayName(user(name = "Hitarashi ()")))
    }

    @Test
    fun mergeScreenErrors_remoteError_winsOverLocalValidation() {
        val remote = ScreenError.Remote("server said no")
        val local = ScreenError.Validation("type a password")

        assertEquals(remote, mergeScreenErrors(remote = remote, local = local))
    }

    @Test
    fun mergeScreenErrors_noRemote_keepsLocalValidationWithItsLayer() {
        val local = ScreenError.Validation("type a password")

        assertEquals(local, mergeScreenErrors(remote = null, local = local))
    }

    @Test
    fun mergeScreenErrors_noErrors_returnsNull() {
        assertNull(mergeScreenErrors(remote = null, local = null))
    }
}
