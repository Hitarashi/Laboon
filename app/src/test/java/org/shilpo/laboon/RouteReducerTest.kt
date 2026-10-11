package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Test
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.navigation.MainTab
import org.shilpo.laboon.navigation.Route
import org.shilpo.laboon.navigation.RouteEvent
import org.shilpo.laboon.navigation.RouteState
import org.shilpo.laboon.navigation.encode
import org.shilpo.laboon.navigation.reduce
import org.shilpo.laboon.navigation.routeStateFromTokens

class RouteReducerTest {
    private val connectedLastFm = LastFmCredentials(true, "ada", "session", "key", "secret")
    private val disconnectedLastFm = LastFmCredentials(false, null, null, null, null)

    @Test
    fun appStartRequiresLastFmAndOpensHomeWhenConnected() {
        val needsLastFm = reduce(
            RouteState(),
            RouteEvent.AppStarted(true, true, true, disconnectedLastFm),
        )
        assertEquals(Route.LastFm(disconnectedLastFm), needsLastFm.current)

        val ready = reduce(
            RouteState(),
            RouteEvent.AppStarted(true, true, true, connectedLastFm),
        )
        assertEquals(Route.Home, ready.current)
    }

    @Test
    fun incompletePermissionsKeepOnboardingAheadOfAuthentication() {
        val state = reduce(
            RouteState(),
            RouteEvent.AppStarted(true, false, false, connectedLastFm),
        )
        assertEquals(Route.Permissions, state.current)
    }

    @Test
    fun lastFmConnectionEntersHome() {
        val state = reduce(RouteState(current = Route.LastFm(disconnectedLastFm)), RouteEvent.LastFmConnected)
        assertEquals(Route.Home, state.current)
    }

    @Test
    fun backFromSettingsAndTabsReturnsToHomeState() {
        val state = RouteState(current = Route.Home, currentTab = MainTab.Library, settingsVisible = true)
        val settingsClosed = reduce(state, RouteEvent.BackPressed)
        assertEquals(false, settingsClosed.settingsVisible)
        val homeTab = reduce(settingsClosed, RouteEvent.BackPressed)
        assertEquals(MainTab.Home, homeTab.currentTab)
    }

    @Test
    fun routeStateEncodingRoundTripsLastFmCredentials() {
        val state = RouteState(
            current = Route.LastFm(connectedLastFm),
            backStack = listOf(Route.Connect),
            currentTab = MainTab.Library,
            settingsVisible = true,
        )
        assertEquals(state, routeStateFromTokens(state.encode()))
    }
}
