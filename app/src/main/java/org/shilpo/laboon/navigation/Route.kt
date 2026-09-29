package org.shilpo.laboon.navigation

import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.auth.ListenBrainzCredentials

sealed interface Route {
    val id: String
    val isBackDestination: Boolean
    val backTarget: Route?

    data object Welcome : Route {
        override val id: String = "welcome"
        override val isBackDestination: Boolean = true
        override val backTarget: Route? = null
    }

    data object Permissions : Route {
        override val id: String = "permissions"
        override val isBackDestination: Boolean = true
        override val backTarget: Route = Welcome
    }

    data object Connect : Route {
        override val id: String = "connect"
        override val isBackDestination: Boolean = true
        override val backTarget: Route = Permissions
    }

    data class LastFm(val credentials: LastFmCredentials?) : Route {
        override val id: String = ID
        override val isBackDestination: Boolean = true
        override val backTarget: Route? = null

        companion object {
            const val ID: String = "lastfm"
        }
    }

    data class ListenBrainz(val credentials: ListenBrainzCredentials?) : Route {
        override val id: String = ID
        override val isBackDestination: Boolean = true
        override val backTarget: Route = LastFm(null)

        companion object {
            const val ID: String = "listenbrainz"
        }
    }

    data object Home : Route {
        override val id: String = "home"
        override val isBackDestination: Boolean = false
        override val backTarget: Route? = null
    }
}

enum class RouteDirection { Forward, Backward }

fun transitionDirection(from: Route, to: Route): RouteDirection =
    if (from.backTarget != null && from.backTarget == to) RouteDirection.Backward else RouteDirection.Forward

fun tabTransitionDirection(from: MainTab, to: MainTab): RouteDirection =
    if (to.order > from.order) RouteDirection.Forward else RouteDirection.Backward

internal fun routeFromId(id: String): Route? = when (id) {
    Route.Welcome.id -> Route.Welcome
    Route.Permissions.id -> Route.Permissions
    Route.Connect.id -> Route.Connect
    Route.Home.id -> Route.Home
    Route.LastFm.ID -> Route.LastFm(null)
    Route.ListenBrainz.ID -> Route.ListenBrainz(null)
    else -> null
}
