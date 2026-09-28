@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
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

@Composable
fun HomeScreen(
    session: AuthSession?,
    credentials: LastFmCredentials? = null,
    modifier: Modifier = Modifier,
    onDisconnect: () -> Unit = {},
) {
    var currentTab by rememberSaveable { mutableStateOf(MainNavTab.Home) }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentTab,
            modifier = Modifier.fillMaxSize(),
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
                    onDisconnect = onDisconnect,
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
    }
}

@Composable
private fun HomeContent(
    session: AuthSession?,
    onDisconnect: () -> Unit,
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
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
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
                        onDisconnect = onDisconnect,
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
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val context = LocalPlatformContext.current
    var menuExpanded by remember { mutableStateOf(false) }
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

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .clickable { menuExpanded = true },
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

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            val name = session?.user?.name ?: session?.user?.username
            if (name != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.home_disconnect_button),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuExpanded = false
                    val loader = SingletonImageLoader.get(context)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                    onDisconnect()
                },
            )
        }
    }
}
