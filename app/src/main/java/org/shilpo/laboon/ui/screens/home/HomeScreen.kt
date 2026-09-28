@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.data.auth.AuthSession
import org.shilpo.laboon.data.auth.LastFmCredentials
import org.shilpo.laboon.ui.component.FloatingNavigationToolbar
import org.shilpo.laboon.ui.navigation.MainNavTab
import org.shilpo.laboon.ui.screens.library.LibraryScreen
import org.shilpo.laboon.ui.screens.search.SearchScreen
import org.shilpo.laboon.ui.screens.settings.SettingsScreen

@Composable
fun HomeScreen(
    session: AuthSession?,
    credentials: LastFmCredentials? = null,
    modifier: Modifier = Modifier,
    onDisconnect: () -> Unit = {},
) {
    var currentTab by rememberSaveable { mutableStateOf(MainNavTab.Home) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val homeBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    val isBackEnabled = showSettings || currentTab != MainNavTab.Home

    NavigationBackHandler(
        state = homeBackState,
        isBackEnabled = isBackEnabled,
        onBackCompleted = {
            if (showSettings) {
                showSettings = false
            } else if (currentTab != MainNavTab.Home) {
                currentTab = MainNavTab.Home
            }
        },
    )

    val transitionState = homeBackState.transitionState
    val isBackInProgress = transitionState is NavigationEventTransitionState.InProgress
    val backEvent = (transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent
    val backProgress = backEvent?.progress ?: 0f
    val swipeEdge = backEvent?.swipeEdge ?: NavigationEvent.EDGE_LEFT

    val settingsBackProgress = if (showSettings && isBackInProgress) backProgress else 0f
    val tabBackProgress =
        if (!showSettings && currentTab != MainNavTab.Home && isBackInProgress) backProgress else 0f

    val animatedSettingsScale by animateFloatAsState(
        targetValue = 1f - (settingsBackProgress * 0.10f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "settingsScale",
    )
    val animatedSettingsCorners by animateFloatAsState(
        targetValue = settingsBackProgress * 32f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "settingsCorners",
    )
    val density = LocalDensity.current
    val settingsMaxShiftPx = with(density) { 56.dp.toPx() }
    val targetSettingsOffsetX = if (settingsBackProgress > 0f) {
        if (swipeEdge == NavigationEvent.EDGE_RIGHT) -settingsBackProgress * settingsMaxShiftPx else settingsBackProgress * settingsMaxShiftPx
    } else 0f
    val animatedSettingsOffsetX by animateFloatAsState(
        targetValue = targetSettingsOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "settingsOffsetX",
    )

    val scrimAlpha by animateFloatAsState(
        targetValue = if (showSettings) (1f - settingsBackProgress) * 0.4f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "settingsScrim",
    )

    val animatedTabScale by animateFloatAsState(
        targetValue = 1f - (tabBackProgress * 0.08f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabScale",
    )
    val animatedTabCorners by animateFloatAsState(
        targetValue = tabBackProgress * 24f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabCorners",
    )
    val tabMaxShiftPx = with(density) { 40.dp.toPx() }
    val targetTabOffsetX = if (tabBackProgress > 0f) {
        if (swipeEdge == NavigationEvent.EDGE_RIGHT) -tabBackProgress * tabMaxShiftPx else tabBackProgress * tabMaxShiftPx
    } else 0f
    val animatedTabOffsetX by animateFloatAsState(
        targetValue = targetTabOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabOffsetX",
    )

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentTab,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedTabScale
                    scaleY = animatedTabScale
                    translationX = animatedTabOffsetX
                    shape = RoundedCornerShape(animatedTabCorners.dp)
                    clip = animatedTabCorners > 0.5f
                },
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally { width -> direction * (width / 4) } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> -direction * (width / 4) } + fadeOut())
            },
            label = "mainNavTabTransition",
        ) { tab ->
            when (tab) {
                MainNavTab.Home -> HomeContent(
                    session = session,
                    onOpenSettings = { showSettings = true },
                    modifier = Modifier.fillMaxSize(),
                )

                MainNavTab.Search -> SearchScreen(
                    modifier = Modifier.fillMaxSize(),
                )

                MainNavTab.Library -> LibraryScreen(
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        FloatingNavigationToolbar(
            selectedTab = currentTab,
            onTabSelected = { currentTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
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
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            SettingsScreen(
                session = session,
                onBack = { showSettings = false },
                onDisconnect = {
                    showSettings = false
                    onDisconnect()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = animatedSettingsScale
                        scaleY = animatedSettingsScale
                        translationX = animatedSettingsOffsetX
                        shape = RoundedCornerShape(animatedSettingsCorners.dp)
                        clip = animatedSettingsCorners > 0.5f
                    },
            )
        }
    }
}

@Composable
private fun HomeContent(
    session: AuthSession?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
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
                            Text(
                                text = stringResource(R.string.home_title),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val displayName = session?.user?.name
                            ?: session?.user?.username?.let { "@$it" }
                            ?: stringResource(R.string.home_user_fallback)
                        Text(
                            text = stringResource(R.string.home_welcome, displayName),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    UserProfileAvatar(
                        session = session,
                        onClick = onOpenSettings,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_library_placeholder_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.home_library_placeholder_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun UserProfileAvatar(
    session: AuthSession?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val context = LocalPlatformContext.current
    var isImageLoaded by remember { mutableStateOf(false) }

    val imageRequest = remember(session?.serverUrl, session?.token) {
        val serverUrl = session?.serverUrl?.trimEnd('/')
        val token = session?.token
        if (!serverUrl.isNullOrEmpty() && !token.isNullOrEmpty()) {
            val headers = NetworkHeaders.Builder()
                .set("Authorization", "Bearer $token")
                .build()
            ImageRequest.Builder(context)
                .data("$serverUrl/api/v1/auth/me/avatar")
                .httpHeaders(headers)
                .crossfade(true)
                .build()
        } else {
            null
        }
    }

    val initial = remember(session?.user) {
        session?.user?.let { user ->
            (user.name ?: user.username ?: user.firstName)
                ?.firstOrNull { it.isLetter() }
                ?.uppercaseChar()
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!isImageLoaded) {
            if (initial != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_user_headshot),
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.55f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { state ->
                    isImageLoaded = state is AsyncImagePainter.State.Success
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
