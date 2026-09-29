package org.shilpo.laboon.navigation

import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.auth.ListenBrainzCredentials

fun RouteState.encode(): List<String> = buildList {
    add(current.id)
    add(backStack.size.toString())
    backStack.forEach { add(it.id) }
    add(currentTab.name)
    add(if (settingsVisible) "1" else "0")
    if (current is Route.LastFm) {
        val credentials = current.credentials
        add(if (credentials == null) "0" else "1")
        add(if (credentials?.connected == true) "1" else "0")
        add(credentials?.username.orEncoded())
        add(credentials?.sessionKey.orEncoded())
        add(credentials?.apiKey.orEncoded())
        add(credentials?.apiSecret.orEncoded())
    } else if (current is Route.ListenBrainz) {
        val credentials = current.credentials
        add(if (credentials == null) "0" else "1")
        add(if (credentials?.connected == true) "1" else "0")
        add(credentials?.username.orEncoded())
        add(credentials?.token.orEncoded())
    }
}

fun routeStateFromTokens(tokens: List<String>): RouteState? {
    if (tokens.size < 4) return null
    var index = 0
    val current = routeFromId(tokens[index++]) ?: return null
    val backStackSize = tokens[index++].toIntOrNull() ?: return null
    if (index + backStackSize + 2 > tokens.size) return null
    val backStack =
        tokens.subList(index, index + backStackSize).map { routeFromId(it) ?: return null }
    index += backStackSize
    val tabToken = tokens[index++]
    val currentTab = MainTab.entries.firstOrNull { it.name == tabToken } ?: return null
    val settingsVisible = tokens[index++] == "1"
    var restored = current
    if (current is Route.LastFm) {
        if (index >= tokens.size) return null
        if (tokens[index++] == "0") {
            restored = Route.LastFm(null)
        } else {
            if (index + 5 > tokens.size) return null
            restored = Route.LastFm(
                LastFmCredentials(
                    connected = tokens[index++] == "1",
                    username = tokens[index++].decoded(),
                    sessionKey = tokens[index++].decoded(),
                    apiKey = tokens[index++].decoded(),
                    apiSecret = tokens[index++].decoded(),
                ),
            )
        }
    } else if (current is Route.ListenBrainz) {
        if (index >= tokens.size) return null
        if (tokens[index++] == "0") {
            restored = Route.ListenBrainz(null)
        } else {
            if (index + 3 > tokens.size) return null
            restored = Route.ListenBrainz(
                ListenBrainzCredentials(
                    connected = tokens[index++] == "1",
                    username = tokens[index++].decoded(),
                    token = tokens[index++].decoded(),
                ),
            )
        }
    }
    return RouteState(
        current = restored,
        backStack = backStack,
        currentTab = currentTab,
        settingsVisible = settingsVisible,
    )
}

private fun String?.orEncoded(): String = if (this == null) "0" else "1$this"

private fun String.decoded(): String? = if (startsWith("1")) substring(1) else null
