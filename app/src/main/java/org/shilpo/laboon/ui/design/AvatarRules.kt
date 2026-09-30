package org.shilpo.laboon.ui.design

import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.AuthUser

const val AVATAR_PATH = "/api/v1/auth/me/avatar"

data class AvatarImageSpec(
    val url: String,
    val authorization: String,
)

fun avatarEndpoint(serverUrl: String?): String? =
    serverUrl?.trim()?.trimEnd('/')
        ?.takeIf { it.isNotEmpty() }
        ?.plus(AVATAR_PATH)

fun avatarBearerHeader(token: String?): String? =
    token?.trim()?.takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

fun avatarImageSpec(session: AuthSession?): AvatarImageSpec? {
    val url = avatarEndpoint(session?.serverUrl) ?: return null
    val authorization = avatarBearerHeader(session?.token) ?: return null
    return AvatarImageSpec(url, authorization)
}

fun avatarInitial(user: AuthUser?): Char? =
    listOfNotNull(user?.name, user?.username, user?.firstName)
        .firstNotNullOfOrNull { candidate ->
            candidate.firstOrNull(Char::isLetter)?.uppercaseChar()
        }

fun userDisplayName(user: AuthUser?): String? =
    listOfNotNull(user?.name, user?.username).firstOrNull { it.isNotBlank() }
