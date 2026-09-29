@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shilpo.laboon.auth.AuthClient
import org.shilpo.laboon.auth.OnboardingProgress
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.auth.SharedPreferencesKeyValueStore
import org.shilpo.laboon.auth.UnauthorizedException
import org.shilpo.laboon.navigation.Route
import org.shilpo.laboon.navigation.RouteDirection
import org.shilpo.laboon.navigation.RouteEvent
import org.shilpo.laboon.navigation.RouteState
import org.shilpo.laboon.navigation.encode
import org.shilpo.laboon.navigation.reduce
import org.shilpo.laboon.navigation.routeStateFromTokens
import org.shilpo.laboon.navigation.transitionDirection
import org.shilpo.laboon.permissions.AndroidPermissionState
import org.shilpo.laboon.permissions.PermissionCatalogue
import org.shilpo.laboon.permissions.canLeaveOnboarding
import org.shilpo.laboon.ui.design.PredictiveBackSpec
import org.shilpo.laboon.ui.design.PredictiveBackSurface
import org.shilpo.laboon.ui.design.rememberPredictiveBackState
import org.shilpo.laboon.ui.design.splash.Overlay
import org.shilpo.laboon.ui.design.splash.Tuning
import org.shilpo.laboon.ui.design.splash.VectorLoader
import org.shilpo.laboon.ui.design.theme.AppTypography
import org.shilpo.laboon.ui.screens.connect.ConnectScreen
import org.shilpo.laboon.ui.screens.home.HomeScreen
import org.shilpo.laboon.ui.screens.lastfm.LastFmScreen
import org.shilpo.laboon.ui.screens.listenbrainz.ListenBrainzScreen
import org.shilpo.laboon.ui.screens.permissions.PermissionsScreen
import org.shilpo.laboon.ui.screens.welcome.WelcomeScreen

private const val ROUTE_STATE_KEY = "org.shilpo.laboon.routeState"

class MainActivity : ComponentActivity() {

    private var isReady = false
    private var splashVectorPath by mutableStateOf<Path?>(null)
    private lateinit var sessionStore: SessionStore
    private lateinit var authClient: AuthClient
    private lateinit var onboardingProgress: OnboardingProgress

    private var routeState by mutableStateOf(RouteState())
    private var isAuthenticating by mutableStateOf(false)
    private var authErrorMessage by mutableStateOf<String?>(null)

    private var isLastFmConnecting by mutableStateOf(false)
    private var lastFmErrorMessage by mutableStateOf<String?>(null)

    private var isListenBrainzConnecting by mutableStateOf(false)
    private var listenBrainzErrorMessage by mutableStateOf<String?>(null)

    private var permissionAnswersRevision by mutableIntStateOf(0)

    private fun dispatch(event: RouteEvent) {
        routeState = reduce(routeState, event)
    }

    private fun verifySessionIfPresent() {
        val session = sessionStore.getSession() ?: return
        lifecycleScope.launch {
            val result = authClient.validateSession(session.serverUrl, session.token)
            result.onFailure { error ->
                if (error is UnauthorizedException) {
                    dispatch(RouteEvent.SessionMissing)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        verifySessionIfPresent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val keyValueStore = SharedPreferencesKeyValueStore(applicationContext)
        sessionStore = SessionStore(keyValueStore)
        authClient = AuthClient(sessionStore)
        onboardingProgress = OnboardingProgress(keyValueStore)
        verifySessionIfPresent()

        val restoredTokens = savedInstanceState?.getStringArrayList(ROUTE_STATE_KEY)
        routeState = restoredTokens?.let { routeStateFromTokens(it) }
            ?: reduce(
                RouteState(),
                RouteEvent.AppStarted(
                    hasSession = sessionStore.hasSession(),
                    permissionsCompleted = onboardingProgress.hasCompletedPermissions,
                    permissionsSatisfied = canLeaveOnboarding(
                        PermissionCatalogue,
                        AndroidPermissionState(applicationContext),
                    ),
                    lastFmCredentials = sessionStore.getLastFmCredentials(),
                    listenBrainzCredentials = sessionStore.getListenBrainzCredentials(),
                ),
            )

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
            val path = VectorLoader.loadPath(this@MainActivity, R.drawable.about_splash)
            withContext(Dispatchers.Main) {
                splashVectorPath = path
                isReady = true
            }
        }

        handleAuthIntent(intent)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { permissionAnswersRevision++ }
            val permissionState = remember(context) {
                AndroidPermissionState(context) { permission -> permissionLauncher.launch(permission) }
            }
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

                    val mainBackState = rememberPredictiveBackState(
                        enabled = routeState.canGoBack,
                        onBack = { dispatch(RouteEvent.BackPressed) },
                    )

                    val motionScheme = MaterialTheme.motionScheme

                    val contentAlpha by animateFloatAsState(
                        targetValue = if (contentVisible) 1f else 0f,
                        animationSpec = motionScheme.defaultEffectsSpec(),
                        label = "splashContentAlpha",
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        PredictiveBackSurface(
                            state = mainBackState,
                            spec = PredictiveBackSpec.MainScreen,
                        ) { surfaceModifier ->
                            AnimatedContent(
                                targetState = routeState.current,
                                modifier = surfaceModifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = contentAlpha
                                        translationY =
                                            (1f - contentAlpha) * Tuning.Default.reveal.RISE_DP.dp.toPx()
                                    },
                                transitionSpec = {
                                    val isBackNav =
                                        transitionDirection(
                                            initialState,
                                            targetState
                                        ) == RouteDirection.Backward
                                    if (isBackNav) {
                                        (slideInHorizontally(
                                            animationSpec = motionScheme.defaultSpatialSpec(),
                                            initialOffsetX = { -it },
                                        ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                                                (slideOutHorizontally(
                                                    animationSpec = motionScheme.defaultSpatialSpec(),
                                                    targetOffsetX = { it },
                                                ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec()))
                                    } else {
                                        (slideInHorizontally(
                                            animationSpec = motionScheme.defaultSpatialSpec(),
                                            initialOffsetX = { it },
                                        ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                                                (slideOutHorizontally(
                                                    animationSpec = motionScheme.defaultSpatialSpec(),
                                                    targetOffsetX = { -it },
                                                ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec()))
                                    }
                                },
                                label = "screenTransition",
                            ) { route ->
                                when (route) {
                                    Route.Welcome -> WelcomeScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        onLetsGoClick = { dispatch(RouteEvent.OnboardingStarted) },
                                    )

                                    Route.Permissions -> PermissionsScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        state = permissionState,
                                        permissionAnswersRevision = permissionAnswersRevision,
                                        onContinue = {
                                            if (!canLeaveOnboarding(
                                                    PermissionCatalogue,
                                                    permissionState
                                                )
                                            ) {
                                                return@PermissionsScreen
                                            }
                                            onboardingProgress.markPermissionsCompleted()
                                            dispatch(
                                                RouteEvent.PermissionsCompleted(
                                                    hasSession = sessionStore.hasSession(),
                                                    lastFmCredentials = sessionStore.getLastFmCredentials(),
                                                    listenBrainzCredentials = sessionStore.getListenBrainzCredentials(),
                                                ),
                                            )
                                        },
                                    )

                                    Route.Connect -> ConnectScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        isAuthenticating = isAuthenticating,
                                        errorMessage = authErrorMessage,
                                    )

                                    is Route.LastFm -> LastFmScreen(
                                        credentials = route.credentials,
                                        hasSession = sessionStore.hasSession(),
                                        isConnecting = isLastFmConnecting,
                                        errorMessage = lastFmErrorMessage,
                                        onConnect = { username, password ->
                                            val session = sessionStore.getSession()
                                            if (session == null) {
                                                dispatch(RouteEvent.SessionMissing)
                                                return@LastFmScreen
                                            }
                                            isLastFmConnecting = true
                                            lastFmErrorMessage = null
                                            lifecycleScope.launch {
                                                val result = authClient.loginLastFm(
                                                    serverUrl = session.serverUrl,
                                                    token = session.token,
                                                    username = username,
                                                    password = password,
                                                )
                                                result.fold(
                                                    onSuccess = {
                                                        autofillManager?.commit()
                                                        isLastFmConnecting = false
                                                        dispatch(
                                                            RouteEvent.LastFmConnected(
                                                                listenBrainzCredentials = sessionStore.getListenBrainzCredentials(),
                                                            ),
                                                        )
                                                    },
                                                    onFailure = { error ->
                                                        autofillManager?.cancel()
                                                        isLastFmConnecting = false
                                                        if (error is UnauthorizedException) {
                                                            dispatch(RouteEvent.SessionMissing)
                                                        } else {
                                                            lastFmErrorMessage = error.message
                                                                ?: getString(R.string.connect_auth_failed)
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    is Route.ListenBrainz -> ListenBrainzScreen(
                                        credentials = route.credentials,
                                        hasSession = sessionStore.hasSession(),
                                        isConnecting = isListenBrainzConnecting,
                                        errorMessage = listenBrainzErrorMessage,
                                        onConnect = { token ->
                                            val session = sessionStore.getSession()
                                            if (session == null) {
                                                dispatch(RouteEvent.SessionMissing)
                                                return@ListenBrainzScreen
                                            }
                                            isListenBrainzConnecting = true
                                            listenBrainzErrorMessage = null
                                            lifecycleScope.launch {
                                                val result = authClient.loginListenBrainz(
                                                    serverUrl = session.serverUrl,
                                                    token = session.token,
                                                    listenbrainzToken = token,
                                                )
                                                result.fold(
                                                    onSuccess = {
                                                        isListenBrainzConnecting = false
                                                        dispatch(RouteEvent.ListenBrainzConnected)
                                                    },
                                                    onFailure = { error ->
                                                        isListenBrainzConnecting = false
                                                        if (error is UnauthorizedException) {
                                                            dispatch(RouteEvent.SessionMissing)
                                                        } else {
                                                            listenBrainzErrorMessage = error.message
                                                                ?: getString(R.string.connect_auth_failed)
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    Route.Home -> HomeScreen(
                                        state = routeState,
                                        session = sessionStore.getSession(),
                                        credentials = sessionStore.getLastFmCredentials(),
                                        onEvent = ::dispatch,
                                        onDisconnect = {
                                            sessionStore.signOut()
                                            onboardingProgress.reset()
                                            dispatch(RouteEvent.SessionEnded)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }

                        if (!splashDone) {
                            Overlay(
                                isDark = darkTheme,
                                customVectorPath = splashVectorPath,
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(ROUTE_STATE_KEY, ArrayList(routeState.encode()))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val payload = authClient.parseConnectionUri(uri) ?: return

        isAuthenticating = true
        authErrorMessage = null
        dispatch(RouteEvent.DeepLinkArrived)

        lifecycleScope.launch {
            val exchangeResult = authClient.exchangeCode(payload.serverUrl, payload.code)
            exchangeResult.fold(
                onSuccess = { session ->
                    val lastFmResult =
                        authClient.fetchLastFmStatus(session.serverUrl, session.token)
                    val creds = lastFmResult.getOrNull()
                    val lbResult =
                        authClient.fetchListenBrainzStatus(session.serverUrl, session.token)
                    val lbCreds = lbResult.getOrNull()
                    isAuthenticating = false
                    dispatch(
                        RouteEvent.SessionEstablished(
                            lastFmCredentials = creds,
                            listenBrainzCredentials = lbCreds,
                        ),
                    )
                },
                onFailure = { error ->
                    isAuthenticating = false
                    authErrorMessage = error.message ?: getString(R.string.connect_auth_failed)
                }
            )
        }
    }
}
