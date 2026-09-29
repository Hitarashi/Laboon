package org.shilpo.laboon.navigation

import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.auth.ListenBrainzCredentials

data class RouteState(
    val current: Route = Route.Welcome,
    val backStack: List<Route> = emptyList(),
    val currentTab: MainTab = MainTab.Home,
    val settingsVisible: Boolean = false,
) {
    val canGoBack: Boolean
        get() = backStack.isNotEmpty() && current.isBackDestination

    val canGoBackWithinHome: Boolean
        get() = current is Route.Home && (settingsVisible || currentTab != MainTab.Home)
}

sealed interface RouteEvent {
    data class AppStarted(
        val hasSession: Boolean,
        val permissionsCompleted: Boolean,
        val permissionsSatisfied: Boolean,
        val lastFmCredentials: LastFmCredentials?,
        val listenBrainzCredentials: ListenBrainzCredentials?,
    ) : RouteEvent

    data object OnboardingStarted : RouteEvent

    data class PermissionsCompleted(
        val hasSession: Boolean,
        val lastFmCredentials: LastFmCredentials?,
        val listenBrainzCredentials: ListenBrainzCredentials?,
    ) : RouteEvent

    data object DeepLinkArrived : RouteEvent

    data class SessionEstablished(
        val lastFmCredentials: LastFmCredentials?,
        val listenBrainzCredentials: ListenBrainzCredentials?,
    ) : RouteEvent

    data object BackPressed : RouteEvent

    data class TabSelected(val tab: MainTab) : RouteEvent

    data object SettingsOpened : RouteEvent

    data object SettingsClosed : RouteEvent

    data class LastFmConnected(
        val listenBrainzCredentials: ListenBrainzCredentials?,
    ) : RouteEvent

    data object ListenBrainzConnected : RouteEvent

    data object SessionMissing : RouteEvent

    data object SessionEnded : RouteEvent
}

fun reduce(state: RouteState, event: RouteEvent): RouteState = when (event) {
    is RouteEvent.AppStarted -> RouteState(current = startRoute(event))
    RouteEvent.OnboardingStarted -> state.push(Route.Permissions)
    is RouteEvent.PermissionsCompleted ->
        if (event.hasSession) {
            state.copy(
                backStack = emptyList(),
                current = authenticatedRoute(
                    event.lastFmCredentials,
                    event.listenBrainzCredentials
                ),
            )
        } else {
            state.push(Route.Connect)
        }

    RouteEvent.DeepLinkArrived ->
        if (state.current == Route.Welcome) state.copy(current = Route.Connect) else state

    is RouteEvent.SessionEstablished ->
        state.copy(
            current = authenticatedRoute(
                event.lastFmCredentials,
                event.listenBrainzCredentials
            )
        )

    RouteEvent.BackPressed -> when {
        state.canGoBack -> state.copy(
            current = state.backStack.last(),
            backStack = state.backStack.dropLast(1),
        )

        state.current is Route.Home && state.settingsVisible -> state.copy(settingsVisible = false)
        state.current is Route.Home && state.currentTab != MainTab.Home ->
            state.copy(currentTab = MainTab.Home)

        else -> state
    }

    is RouteEvent.TabSelected -> state.copy(currentTab = event.tab)
    RouteEvent.SettingsOpened -> state.copy(settingsVisible = true)
    RouteEvent.SettingsClosed -> state.copy(settingsVisible = false)
    is RouteEvent.LastFmConnected -> {
        val nextRoute = authenticatedRoute(
            lastFmCredentials = LastFmCredentials(
                connected = true,
                username = null,
                sessionKey = null,
                apiKey = null,
                apiSecret = null
            ),
            listenBrainzCredentials = event.listenBrainzCredentials,
        )
        if (nextRoute is Route.Home) {
            state.copy(backStack = emptyList(), current = Route.Home)
        } else {
            state.push(nextRoute)
        }
    }

    RouteEvent.ListenBrainzConnected -> state.copy(backStack = emptyList(), current = Route.Home)
    RouteEvent.SessionMissing -> state.copy(
        current = Route.Welcome,
        backStack = emptyList(),
        settingsVisible = false,
    )

    RouteEvent.SessionEnded -> RouteState()
}

private fun startRoute(event: RouteEvent.AppStarted): Route {
    val permissionsSettled = event.permissionsCompleted && event.permissionsSatisfied
    return when {
        !permissionsSettled -> if (event.hasSession) Route.Permissions else Route.Welcome
        !event.hasSession -> Route.Welcome
        else -> authenticatedRoute(event.lastFmCredentials, event.listenBrainzCredentials)
    }
}

private fun authenticatedRoute(
    lastFmCredentials: LastFmCredentials?,
    listenBrainzCredentials: ListenBrainzCredentials?,
): Route = when {
    lastFmCredentials?.connected != true -> Route.LastFm(lastFmCredentials)
    listenBrainzCredentials?.connected != true -> Route.ListenBrainz(listenBrainzCredentials)
    else -> Route.Home
}

private fun RouteState.push(route: Route): RouteState =
    if (route == current) this else copy(current = route, backStack = backStack + current)
