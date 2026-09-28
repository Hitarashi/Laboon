@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shilpo.laboon.data.auth.AuthRepository
import org.shilpo.laboon.data.auth.AuthStorage
import org.shilpo.laboon.data.auth.LastFmCredentials
import org.shilpo.laboon.ui.component.splash.SplashConfig
import org.shilpo.laboon.ui.component.splash.SplashOverlay
import org.shilpo.laboon.ui.component.splash.SplashSlots
import org.shilpo.laboon.ui.component.splash.SplashVectorLoader
import org.shilpo.laboon.ui.screens.auth.ConnectScreen
import org.shilpo.laboon.ui.screens.home.HomeScreen
import org.shilpo.laboon.ui.screens.lastfm.LastFmScreen
import org.shilpo.laboon.ui.screens.permissions.PermissionsScreen
import org.shilpo.laboon.ui.screens.welcome.WelcomeScreen
import org.shilpo.laboon.ui.theme.AppTypography

private sealed interface Screen {
    data object Welcome : Screen
    data object Permissions : Screen
    data object Connect : Screen
    data class LastFm(val credentials: LastFmCredentials?) : Screen
    data object Home : Screen
}

class MainActivity : ComponentActivity() {

    private var isReady = false
    private lateinit var authStorage: AuthStorage
    private lateinit var authRepository: AuthRepository

    private var currentScreen by mutableStateOf<Screen>(Screen.Welcome)
    private var isAuthenticating by mutableStateOf(false)
    private var authErrorMessage by mutableStateOf<String?>(null)

    private var isLastFmConnecting by mutableStateOf(false)
    private var lastFmErrorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authStorage = AuthStorage(applicationContext)
        authRepository = AuthRepository(authStorage)

        val hasSession = authStorage.hasSession()
        val permissionsCompleted =
            authStorage.hasCompletedPermissions() && areEssentialPermissionsGranted(
                applicationContext
            )

        if (!permissionsCompleted) {
            currentScreen = if (hasSession) {
                Screen.Permissions
            } else {
                Screen.Welcome
            }
        } else if (hasSession) {
            val lastFm = authStorage.getLastFmCredentials()
            currentScreen = if (lastFm?.connected == true) {
                Screen.Home
            } else {
                Screen.LastFm(lastFm)
            }
        }

        splashScreen.setKeepOnScreenCondition { !isReady }
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val iconView = splashScreenViewProvider.iconView
            val splashView = splashScreenViewProvider.view

            val iconScaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.15f)
            val iconScaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.15f)
            val iconAlpha = ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f)
            val splashAlpha = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f)

            AnimatorSet().apply {
                duration = 300L
                interpolator = AccelerateDecelerateInterpolator()
                playTogether(iconScaleX, iconScaleY, iconAlpha, splashAlpha)
                doOnEnd {
                    splashScreenViewProvider.remove()
                }
                start()
            }
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val path = SplashVectorLoader.loadPath(this@MainActivity, R.drawable.about_splash)
            withContext(Dispatchers.Main) {
                SplashSlots.customVectorPath = path
                SplashSlots.vectorVersion++
                isReady = true
            }
        }

        handleAuthIntent(intent)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }

            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                motionScheme = MotionScheme.expressive(),
                typography = AppTypography,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val autofillManager = LocalAutofillManager.current
                    var contentVisible by remember { mutableStateOf(false) }
                    var splashDone by remember { mutableStateOf(false) }

                    val screenBackStack = remember { mutableStateListOf<Screen>() }
                    val mainBackState =
                        rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
                    val canGoBack = screenBackStack.isNotEmpty() && currentScreen != Screen.Home

                    NavigationBackHandler(
                        state = mainBackState,
                        isBackEnabled = canGoBack,
                        onBackCompleted = {
                            if (screenBackStack.isNotEmpty()) {
                                currentScreen = screenBackStack.removeLast()
                            }
                        },
                    )

                    val mainTransition = mainBackState.transitionState
                    val isMainBackInProgress =
                        mainTransition is NavigationEventTransitionState.InProgress
                    val mainEvent =
                        (mainTransition as? NavigationEventTransitionState.InProgress)?.latestEvent
                    val mainProgress =
                        if (canGoBack && isMainBackInProgress) mainEvent?.progress ?: 0f else 0f
                    val mainSwipeEdge = mainEvent?.swipeEdge ?: NavigationEvent.EDGE_LEFT

                    val mainScale by animateFloatAsState(
                        targetValue = 1f - (mainProgress * 0.08f),
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "mainScale",
                    )
                    val mainCornerRadius by animateFloatAsState(
                        targetValue = mainProgress * 28f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "mainCornerRadius",
                    )
                    val density = LocalDensity.current
                    val mainMaxShiftPx = with(density) { 44.dp.toPx() }
                    val mainTargetOffsetX = if (mainProgress > 0f) {
                        if (mainSwipeEdge == NavigationEvent.EDGE_RIGHT) -mainProgress * mainMaxShiftPx else mainProgress * mainMaxShiftPx
                    } else 0f
                    val mainOffsetX by animateFloatAsState(
                        targetValue = mainTargetOffsetX,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "mainOffsetX",
                    )

                    val contentAlpha by animateFloatAsState(
                        targetValue = if (contentVisible) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = SplashConfig.Reveal.DURATION_MS,
                            easing = EaseOut
                        ),
                        label = "splashContentAlpha",
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = currentScreen,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = mainScale
                                    scaleY = mainScale
                                    translationX = mainOffsetX
                                    shape = RoundedCornerShape(mainCornerRadius.dp)
                                    clip = mainCornerRadius > 0.5f
                                    alpha = contentAlpha
                                    translationY =
                                        (1f - contentAlpha) * SplashConfig.Reveal.RISE_DP.dp.toPx()
                                },
                            transitionSpec = {
                                val isBackNav =
                                    (targetState == Screen.Welcome && initialState == Screen.Permissions) ||
                                            (targetState == Screen.Permissions && initialState == Screen.Connect)
                                if (isBackNav) {
                                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                                            (slideOutHorizontally { it } + fadeOut())
                                } else {
                                    (slideInHorizontally { it } + fadeIn()) togetherWith
                                            (slideOutHorizontally { -it } + fadeOut())
                                }
                            },
                            label = "screenTransition",
                        ) { screen ->
                            when (screen) {
                                Screen.Welcome -> WelcomeScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    onLetsGoClick = {
                                        screenBackStack.add(Screen.Welcome)
                                        currentScreen = Screen.Permissions
                                    },
                                )

                                Screen.Permissions -> PermissionsScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    onContinue = {
                                        authStorage.setCompletedPermissions(true)
                                        if (authStorage.hasSession()) {
                                            val lastFm = authStorage.getLastFmCredentials()
                                            screenBackStack.clear()
                                            currentScreen = if (lastFm?.connected == true) {
                                                Screen.Home
                                            } else {
                                                Screen.LastFm(lastFm)
                                            }
                                        } else {
                                            screenBackStack.add(Screen.Permissions)
                                            currentScreen = Screen.Connect
                                        }
                                    },
                                )

                                Screen.Connect -> ConnectScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    isAuthenticating = isAuthenticating,
                                    errorMessage = authErrorMessage,
                                )

                                is Screen.LastFm -> LastFmScreen(
                                    credentials = screen.credentials,
                                    isConnecting = isLastFmConnecting,
                                    errorMessage = lastFmErrorMessage,
                                    onConnect = { username, password ->
                                        val session = authStorage.getSession()
                                        if (session == null) {
                                            currentScreen = Screen.Welcome
                                            return@LastFmScreen
                                        }
                                        isLastFmConnecting = true
                                        lastFmErrorMessage = null
                                        lifecycleScope.launch {
                                            val result = authRepository.loginLastFm(
                                                serverUrl = session.serverUrl,
                                                token = session.token,
                                                username = username,
                                                password = password,
                                            )
                                            result.fold(
                                                onSuccess = {
                                                    autofillManager?.commit()
                                                    isLastFmConnecting = false
                                                    currentScreen = Screen.Home
                                                },
                                                onFailure = { error ->
                                                    autofillManager?.cancel()
                                                    isLastFmConnecting = false
                                                    lastFmErrorMessage = error.message
                                                        ?: getString(R.string.connect_auth_failed)
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )

                                Screen.Home -> HomeScreen(
                                    session = authStorage.getSession(),
                                    credentials = authStorage.getLastFmCredentials(),
                                    onDisconnect = {
                                        authStorage.clearSession()
                                        screenBackStack.clear()
                                        currentScreen = Screen.Welcome
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        if (!splashDone) {
                            SplashOverlay(
                                isDark = darkTheme,
                                onBurstStart = {
                                    contentVisible = true
                                },
                                onDismiss = {
                                    splashDone = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val payload = authRepository.parseConnectionUri(uri) ?: return

        isAuthenticating = true
        authErrorMessage = null
        if (currentScreen is Screen.Welcome) {
            currentScreen = Screen.Connect
        }

        lifecycleScope.launch {
            val exchangeResult = authRepository.exchangeCode(payload.serverUrl, payload.code)
            exchangeResult.fold(
                onSuccess = { session ->
                    val lastFmResult =
                        authRepository.fetchLastFmStatus(session.serverUrl, session.token)
                    val creds = lastFmResult.getOrNull()
                    isAuthenticating = false
                    currentScreen = if (creds?.connected == true) {
                        Screen.Home
                    } else {
                        Screen.LastFm(creds)
                    }
                },
                onFailure = { error ->
                    isAuthenticating = false
                    authErrorMessage = error.message ?: getString(R.string.connect_auth_failed)
                }
            )
        }
    }

    private fun areEssentialPermissionsGranted(context: Context): Boolean {
        val notifGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        return notifGranted && audioGranted
    }
}
