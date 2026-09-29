package org.shilpo.laboon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.navigation.MainTab
import org.shilpo.laboon.navigation.Route
import org.shilpo.laboon.navigation.RouteDirection
import org.shilpo.laboon.navigation.RouteEvent
import org.shilpo.laboon.navigation.RouteState
import org.shilpo.laboon.navigation.encode
import org.shilpo.laboon.navigation.reduce
import org.shilpo.laboon.navigation.routeStateFromTokens
import org.shilpo.laboon.navigation.tabTransitionDirection
import org.shilpo.laboon.navigation.transitionDirection

private val connectedCredentials = LastFmCredentials(
    connected = true,
    username = "listener",
    sessionKey = "sk",
    apiKey = "ak",
    apiSecret = "as",
)

private val unconnectedCredentials = LastFmCredentials(
    connected = false,
    username = "listener",
    sessionKey = null,
    apiKey = "ak",
    apiSecret = "as",
)

class RouteReducerTest {

    @Test
    fun appStarted_exhaustiveBootstrapTruthTable() {
        val results = mutableListOf<String>()
        var combinations = 0

        for (hasSession in BOOLEANS) {
            for (permissionsCompleted in BOOLEANS) {
                for (permissionsSatisfied in BOOLEANS) {
                    for (credentials in CREDENTIAL_VARIANTS) {
                        combinations++
                        val event = RouteEvent.AppStarted(
                            hasSession = hasSession,
                            permissionsCompleted = permissionsCompleted,
                            permissionsSatisfied = permissionsSatisfied,
                            lastFmCredentials = credentials,
                        )
                        val state = reduce(RouteState(), event)
                        val settled = permissionsCompleted && permissionsSatisfied
                        val expected = when {
                            !settled -> if (hasSession) Route.Permissions else Route.Welcome
                            !hasSession -> Route.Welcome
                            credentials?.connected == true -> Route.Home
                            else -> Route.LastFm(credentials)
                        }
                        val label = "session=$hasSession completed=$permissionsCompleted " +
                                "satisfied=$permissionsSatisfied creds=${credentials?.connected}"
                        results += "$label -> $state.current"
                        assertEquals(label, expected, state.current)
                        assertTrue("$label must not carry a backstack", state.backStack.isEmpty())
                    }
                }
            }
        }

        assertEquals(24, combinations)
        assertEquals(24, results.size)
    }

    @Test
    fun appStarted_backstackAlwaysEmpty() {
        val state = reduce(
            RouteState(backStack = listOf(Route.Connect), current = Route.Connect),
            RouteEvent.AppStarted(
                hasSession = false,
                permissionsCompleted = false,
                permissionsSatisfied = false,
                lastFmCredentials = null,
            ),
        )
        assertEquals(Route.Welcome, state.current)
        assertEquals(emptyList<Route>(), state.backStack)
    }

    @Test
    fun onboardingStarted_pushesWelcomeOntoBackstack() {
        val state = reduce(RouteState(), RouteEvent.OnboardingStarted)

        assertEquals(Route.Permissions, state.current)
        assertEquals(listOf(Route.Welcome), state.backStack)
    }

    @Test
    fun permissionsCompleted_withoutSession_pushesConnect() {
        val state = reduce(
            RouteState(current = Route.Permissions, backStack = listOf(Route.Welcome)),
            RouteEvent.PermissionsCompleted(hasSession = false, lastFmCredentials = null),
        )

        assertEquals(Route.Connect, state.current)
        assertEquals(listOf(Route.Welcome, Route.Permissions), state.backStack)
    }

    @Test
    fun permissionsCompleted_withSession_clearsBackstackAndLandsOnHome() {
        val state = reduce(
            RouteState(current = Route.Permissions, backStack = listOf(Route.Welcome)),
            RouteEvent.PermissionsCompleted(
                hasSession = true,
                lastFmCredentials = connectedCredentials,
            ),
        )

        assertEquals(Route.Home, state.current)
        assertEquals(emptyList<Route>(), state.backStack)
    }

    @Test
    fun permissionsCompleted_withSession_butDisconnectedLastFm_landsOnLastFm() {
        val state = reduce(
            RouteState(current = Route.Permissions, backStack = listOf(Route.Welcome)),
            RouteEvent.PermissionsCompleted(
                hasSession = true,
                lastFmCredentials = unconnectedCredentials,
            ),
        )

        assertEquals(Route.LastFm(unconnectedCredentials), state.current)
        assertEquals(emptyList<Route>(), state.backStack)
    }

    @Test
    fun backFromPushedRoute_popsBackstack() {
        val pushed = reduce(RouteState(), RouteEvent.OnboardingStarted)
        val popped = reduce(pushed, RouteEvent.BackPressed)

        assertEquals(Route.Welcome, popped.current)
        assertEquals(emptyList<Route>(), popped.backStack)
    }

    @Test
    fun backAtRoot_doesNothing() {
        val root = RouteState()
        val result = reduce(root, RouteEvent.BackPressed)

        assertEquals(Route.Welcome, result.current)
        assertEquals(emptyList<Route>(), result.backStack)
        assertSame(root, result)
    }

    @Test
    fun backOnHome_tabAndSettings_withinHomeUnwindInOrder() {
        val onLibrary = reduce(
            RouteState(current = Route.Home),
            RouteEvent.TabSelected(MainTab.Library),
        )
        val backToHomeTab = reduce(onLibrary, RouteEvent.BackPressed)
        assertEquals(MainTab.Home, backToHomeTab.currentTab)
        assertEquals(Route.Home, backToHomeTab.current)

        val withSettings = reduce(
            backToHomeTab,
            RouteEvent.SettingsOpened,
        )
        assertTrue(withSettings.canGoBackWithinHome)

        val settingsClosed = reduce(withSettings, RouteEvent.BackPressed)
        assertFalse(settingsClosed.settingsVisible)
        assertEquals(Route.Home, settingsClosed.current)
    }

    @Test
    fun canGoBack_isFalseAtHomeAndTrueOnPushedRoutes() {
        val home = reduce(
            RouteState(),
            RouteEvent.SessionEstablished(connectedCredentials),
        )
        assertEquals(Route.Home, home.current)
        assertFalse(home.canGoBack)

        val pushed = reduce(RouteState(), RouteEvent.OnboardingStarted)
        assertTrue(pushed.canGoBack)

        val withBackstackAtHome = home.copy(backStack = listOf(Route.Connect))
        assertFalse(withBackstackAtHome.canGoBack)
    }

    @Test
    fun canGoBackWithinHome_reflectsTabAndSettingsOnly() {
        val home = RouteState(current = Route.Home)
        assertFalse(home.canGoBackWithinHome)

        assertTrue(home.copy(currentTab = MainTab.Search).canGoBackWithinHome)
        assertTrue(home.copy(settingsVisible = true).canGoBackWithinHome)
        assertFalse(RouteState(current = Route.Connect).copy(currentTab = MainTab.Library).canGoBackWithinHome)
    }

    @Test
    fun deepLinkArrived_fromWelcome_landsOnConnect() {
        val state = reduce(RouteState(), RouteEvent.DeepLinkArrived)

        assertEquals(Route.Connect, state.current)
        assertEquals(emptyList<Route>(), state.backStack)
    }

    @Test
    fun deepLinkArrived_fromNonWelcome_leavesRouteUntouched() {
        val onConnect = reduce(
            RouteState(current = Route.Connect, backStack = listOf(Route.Permissions)),
            RouteEvent.DeepLinkArrived,
        )
        assertEquals(Route.Connect, onConnect.current)

        val onHome = reduce(RouteState(), RouteEvent.SessionEstablished(connectedCredentials))
        val stillHome = reduce(onHome, RouteEvent.DeepLinkArrived)
        assertEquals(Route.Home, stillHome.current)
    }

    @Test
    fun deepLinkArrived_thenSessionEstablished_landsOnHomeRegardlessOfBackstack() {
        val withBackstack = RouteState(
            current = Route.Permissions,
            backStack = listOf(Route.Welcome, Route.Connect),
        )
        val arrived = reduce(withBackstack, RouteEvent.DeepLinkArrived)
        val established = reduce(arrived, RouteEvent.SessionEstablished(connectedCredentials))

        assertEquals(Route.Home, established.current)
        assertEquals(listOf(Route.Welcome, Route.Connect), established.backStack)
        assertFalse(established.canGoBack)
    }

    @Test
    fun deepLinkArrived_thenSessionEstablished_landsOnHomeFromABareStack() {
        val arrived = reduce(RouteState(), RouteEvent.DeepLinkArrived)
        val established = reduce(arrived, RouteEvent.SessionEstablished(connectedCredentials))

        assertEquals(Route.Home, established.current)
        assertEquals(emptyList<Route>(), established.backStack)
    }

    @Test
    fun deepLinkArrived_thenSessionEstablished_withoutLastFm_landsOnLastFm() {
        val arrived = reduce(RouteState(), RouteEvent.DeepLinkArrived)
        val established = reduce(arrived, RouteEvent.SessionEstablished(unconnectedCredentials))

        assertEquals(Route.LastFm(unconnectedCredentials), established.current)
    }

    @Test
    fun disconnect_returnsToStartRouteAndClearsEverything() {
        val state = RouteState(
            current = Route.Home,
            backStack = listOf(Route.Connect),
            currentTab = MainTab.Library,
            settingsVisible = true,
        )

        val ended = reduce(state, RouteEvent.SessionEnded)

        assertEquals(Route.Welcome, ended.current)
        assertEquals(emptyList<Route>(), ended.backStack)
        assertEquals(MainTab.Home, ended.currentTab)
        assertFalse(ended.settingsVisible)
        assertFalse(ended.canGoBack)
        assertFalse(ended.canGoBackWithinHome)
    }

    @Test
    fun lastFmConnected_landsOnHome() {
        val state = reduce(
            RouteState(current = Route.LastFm(unconnectedCredentials)),
            RouteEvent.LastFmConnected,
        )
        assertEquals(Route.Home, state.current)
    }

    @Test
    fun sessionMissing_landsOnWelcome() {
        val state = reduce(
            RouteState(current = Route.LastFm(null), backStack = listOf(Route.Connect)),
            RouteEvent.SessionMissing,
        )
        assertEquals(Route.Welcome, state.current)
    }

    @Test
    fun push_neverStoresTheCurrentRouteTwiceInARow() {
        var state = reduce(RouteState(), RouteEvent.OnboardingStarted)
        repeat(5) {
            state = reduce(
                state,
                RouteEvent.PermissionsCompleted(hasSession = false, lastFmCredentials = null),
            )
            assertTrue(state.backStack.zipWithNext().none { (a, b) -> a == b })
            assertFalse(state.backStack.lastOrNull() == state.current)
        }
        assertEquals(Route.Connect, state.current)
        assertEquals(listOf(Route.Welcome, Route.Permissions), state.backStack)
    }

    @Test
    fun push_ofTheCurrentRoute_isANoOp() {
        val onConnect = reduce(
            RouteState(current = Route.Connect, backStack = listOf(Route.Permissions)),
            RouteEvent.PermissionsCompleted(hasSession = false, lastFmCredentials = null),
        )

        assertEquals(Route.Connect, onConnect.current)
        assertEquals(listOf(Route.Permissions), onConnect.backStack)
    }

    @Test
    fun reduce_neverMutatesThePreviousState() {
        val start = RouteState(
            current = Route.Permissions,
            backStack = listOf(Route.Welcome),
            currentTab = MainTab.Search,
            settingsVisible = true,
        )
        val snapshot = start.copy(backStack = start.backStack.toList())

        val next = reduce(
            start,
            RouteEvent.PermissionsCompleted(hasSession = true, lastFmCredentials = null)
        )

        assertEquals(Route.Permissions, start.current)
        assertEquals(listOf(Route.Welcome), start.backStack)
        assertEquals(snapshot, start)
        assertNotSame(start.backStack, next.backStack)
    }

    @Test
    fun reduce_keepsBackStackConsistentWithCurrentAcrossTheWholeEventSurface() {
        val events = listOf(
            RouteEvent.OnboardingStarted,
            RouteEvent.PermissionsCompleted(hasSession = false, lastFmCredentials = null),
            RouteEvent.BackPressed,
            RouteEvent.BackPressed,
            RouteEvent.DeepLinkArrived,
            RouteEvent.SessionEstablished(connectedCredentials),
            RouteEvent.TabSelected(MainTab.Search),
            RouteEvent.SettingsOpened,
            RouteEvent.BackPressed,
            RouteEvent.TabSelected(MainTab.Library),
            RouteEvent.BackPressed,
            RouteEvent.SettingsClosed,
            RouteEvent.SessionEnded,
        )

        var state = RouteState()
        for (event in events) {
            state = reduce(state, event)
            assertTrue(
                "$event left a duplicated backstack entry",
                state.backStack.zipWithNext().none { (a, b) -> a == b })
            if (state.current.isBackDestination && state.backStack.isNotEmpty()) {
                assertTrue(
                    "$event left the current route on top of its own backstack",
                    state.backStack.last() != state.current
                )
            }
            if (state.backStack.isEmpty()) {
                assertFalse(
                    "$event claimed back eligibility on an empty backstack",
                    state.canGoBack
                )
            }
        }
    }

    @Test
    fun routeTransitionDirection_matchesTheOnboardingBackPairs() {
        assertEquals(RouteDirection.Backward, transitionDirection(Route.Permissions, Route.Welcome))
        assertEquals(RouteDirection.Backward, transitionDirection(Route.Connect, Route.Permissions))
        assertEquals(RouteDirection.Forward, transitionDirection(Route.Welcome, Route.Permissions))
        assertEquals(RouteDirection.Forward, transitionDirection(Route.Permissions, Route.Connect))
        assertEquals(RouteDirection.Forward, transitionDirection(Route.Welcome, Route.Connect))
        assertEquals(RouteDirection.Forward, transitionDirection(Route.LastFm(null), Route.Home))
        assertEquals(RouteDirection.Forward, transitionDirection(Route.Home, Route.Welcome))
    }

    @Test
    fun tabTransitionDirection_followsExplicitOrderNotDeclarationOrder() {
        assertEquals(RouteDirection.Forward, tabTransitionDirection(MainTab.Home, MainTab.Search))
        assertEquals(RouteDirection.Forward, tabTransitionDirection(MainTab.Home, MainTab.Library))
        assertEquals(
            RouteDirection.Backward,
            tabTransitionDirection(MainTab.Library, MainTab.Search)
        )
        assertEquals(RouteDirection.Backward, tabTransitionDirection(MainTab.Search, MainTab.Home))
    }

    @Test
    fun encodeAndDecode_roundTripsEveryRouteIncludingCredentials() {
        val states = listOf(
            RouteState(),
            RouteState(
                current = Route.Connect,
                backStack = listOf(Route.Welcome, Route.Permissions)
            ),
            RouteState(
                current = Route.LastFm(connectedCredentials),
                backStack = listOf(Route.Connect)
            ),
            RouteState(
                current = Route.LastFm(null),
                currentTab = MainTab.Library,
                settingsVisible = true
            ),
            RouteState(current = Route.Home, currentTab = MainTab.Search, settingsVisible = true),
        )

        for (state in states) {
            assertEquals(state, routeStateFromTokens(state.encode()))
        }
    }

    @Test
    fun routeStateFromTokens_rejectsGarbage() {
        assertEquals(null, routeStateFromTokens(emptyList()))
        assertEquals(null, routeStateFromTokens(listOf("nope", "0", "Home", "0")))
        assertEquals(null, routeStateFromTokens(listOf("home", "x", "Home", "0")))
        assertEquals(null, routeStateFromTokens(listOf("home", "5", "Home", "0")))
    }

    private companion object {
        val BOOLEANS = listOf(true, false)
        val CREDENTIAL_VARIANTS =
            listOf(null, connectedCredentials, unconnectedCredentials)
    }
}
