@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.navigation.MainTab
import org.shilpo.laboon.navigation.RouteDirection
import org.shilpo.laboon.navigation.RouteEvent
import org.shilpo.laboon.navigation.RouteState
import org.shilpo.laboon.navigation.tabTransitionDirection
import org.shilpo.laboon.ui.design.FloatingNavBar
import org.shilpo.laboon.ui.design.FloatingNavBarClearance
import org.shilpo.laboon.ui.design.NavigationBarBottomPadding
import org.shilpo.laboon.ui.design.PlaceholderCard
import org.shilpo.laboon.ui.design.PredictiveBackSpec
import org.shilpo.laboon.ui.design.PredictiveBackSurface
import org.shilpo.laboon.ui.design.ScreenHeadline
import org.shilpo.laboon.ui.design.ScreenList
import org.shilpo.laboon.ui.design.ScreenScaffold
import org.shilpo.laboon.ui.design.UserAvatar
import org.shilpo.laboon.ui.design.rememberPredictiveBackState
import org.shilpo.laboon.ui.design.userDisplayName
import org.shilpo.laboon.ui.screens.library.LibraryScreen
import org.shilpo.laboon.ui.screens.search.SearchScreen
import org.shilpo.laboon.ui.screens.settings.SettingsScreen

@Composable
fun HomeScreen(
    state: RouteState,
    onEvent: (RouteEvent) -> Unit,
    session: AuthSession?,
    credentials: LastFmCredentials? = null,
    modifier: Modifier = Modifier,
    onDisconnect: () -> Unit = {},
) {
    val currentTab = state.currentTab
    val showSettings = state.settingsVisible

    val homeBackState = rememberPredictiveBackState(
        enabled = state.canGoBackWithinHome,
        onBack = { onEvent(RouteEvent.BackPressed) },
    )

    val settingsIsBackTarget = showSettings
    val tabIsBackTarget = !showSettings && currentTab != MainTab.Home
    val settingsProgress = homeBackState.progressFor(settingsIsBackTarget)

    val motionScheme = MaterialTheme.motionScheme

    val scrimAlpha by animateFloatAsState(
        targetValue = if (showSettings) (1f - settingsProgress) * 0.4f else 0f,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "settingsScrim",
    )

    Box(modifier = modifier.fillMaxSize()) {
        PredictiveBackSurface(
            state = homeBackState,
            spec = PredictiveBackSpec.HomeTab,
            active = tabIsBackTarget,
        ) { tabSurface ->
            AnimatedContent(
                targetState = currentTab,
                modifier = tabSurface.fillMaxSize(),
                transitionSpec = {
                    val forward =
                        tabTransitionDirection(initialState, targetState) == RouteDirection.Forward
                    val direction = if (forward) 1 else -1
                    (slideInHorizontally(
                        animationSpec = motionScheme.defaultSpatialSpec(),
                        initialOffsetX = { width -> direction * (width / 4) },
                    ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                            (slideOutHorizontally(
                                animationSpec = motionScheme.defaultSpatialSpec(),
                                targetOffsetX = { width -> -direction * (width / 4) },
                            ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec()))
                },
                label = "mainNavTabTransition",
            ) { tab ->
                when (tab) {
                    MainTab.Home -> HomeContent(
                        session = session,
                        onOpenSettings = { onEvent(RouteEvent.SettingsOpened) },
                        onNavigate = { onEvent(RouteEvent.TabSelected(it)) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    MainTab.Search -> SearchScreen(
                        modifier = Modifier.fillMaxSize(),
                    )

                    MainTab.Library -> LibraryScreen(
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        FloatingNavBar(
            selectedTab = currentTab,
            onTabSelected = { onEvent(RouteEvent.TabSelected(it)) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = NavigationBarBottomPadding),
        )

        if (showSettings || scrimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialOffsetX = { it },
            ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = slideOutHorizontally(
                animationSpec = motionScheme.defaultSpatialSpec(),
                targetOffsetX = { it },
            ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec()),
        ) {
            PredictiveBackSurface(
                state = homeBackState,
                spec = PredictiveBackSpec.HomeSettings,
                active = settingsIsBackTarget,
            ) { settingsSurface ->
                SettingsScreen(
                    session = session,
                    onBack = { onEvent(RouteEvent.SettingsClosed) },
                    onDisconnect = {
                        onEvent(RouteEvent.SettingsClosed)
                        onDisconnect()
                    },
                    modifier = settingsSurface.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    session: AuthSession?,
    onOpenSettings: () -> Unit,
    onNavigate: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier = modifier) {
        ScreenList(
            bottomClearance = FloatingNavBarClearance,
            itemSpacing = 24.dp,
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.app_icon_small),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp),
                            )
                            ScreenHeadline(text = stringResource(R.string.home_title))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.home_welcome,
                                userDisplayName(session?.user)
                                    ?: stringResource(R.string.home_user_fallback),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    UserAvatar(
                        session = session,
                        onClick = onOpenSettings,
                    )
                }
            }

            item {
                PlaceholderCard(
                    title = stringResource(R.string.home_library_placeholder_title),
                    subtitle = stringResource(R.string.home_library_placeholder_subtitle),
                    onClick = { onNavigate(MainTab.Library) },
                )
            }
        }
    }
}
