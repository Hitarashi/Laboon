@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.shilpo.laboon.auth.AuthClient
import org.shilpo.laboon.auth.OnboardingProgress
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.auth.SharedPreferencesKeyValueStore
import org.shilpo.laboon.auth.UnauthorizedException
import org.shilpo.laboon.home.HomeFeedRepository
import org.shilpo.laboon.navigation.Route
import org.shilpo.laboon.navigation.RouteDirection
import org.shilpo.laboon.navigation.RouteEvent
import org.shilpo.laboon.navigation.RouteState
import org.shilpo.laboon.navigation.encode
import org.shilpo.laboon.navigation.reduce
import org.shilpo.laboon.navigation.routeStateFromTokens
import org.shilpo.laboon.navigation.transitionDirection
import org.shilpo.laboon.permissions.AndroidPermissionState
import org.shilpo.laboon.permissions.canLeaveOnboarding
import org.shilpo.laboon.permissions.permissionCatalogueForAndroidApi
import org.shilpo.laboon.playback.PlaybackPersistence
import org.shilpo.laboon.rip.RipConnectionHolderInstance
import org.shilpo.laboon.theme.LaboonExpressiveTheme
import org.shilpo.laboon.theme.LocalVisualTheme
import org.shilpo.laboon.theme.LocalVisualThemeController
import org.shilpo.laboon.theme.ThemeRouteContent
import org.shilpo.laboon.theme.VisualThemeController
import org.shilpo.laboon.theme.contract.VisualThemeAction
import org.shilpo.laboon.theme.renderer.VisualThemePresentation
import org.shilpo.laboon.ui.design.PredictiveBackSpec
import org.shilpo.laboon.ui.design.PredictiveBackSurface
import org.shilpo.laboon.ui.design.rememberPredictiveBackState
import org.shilpo.laboon.ui.screens.connect.ConnectScreen
import org.shilpo.laboon.ui.screens.connect.openTelegramApp
import org.shilpo.laboon.ui.screens.home.HomeScreen
import org.shilpo.laboon.ui.screens.lastfm.LastFmScreen
import org.shilpo.laboon.ui.screens.listenbrainz.ListenBrainzScreen
import org.shilpo.laboon.ui.screens.permissions.PermissionsScreen
import org.shilpo.laboon.ui.screens.welcome.WelcomeScreen

private const val ROUTE_STATE_KEY = "org.shilpo.laboon.routeState"

class MainActivity : ComponentActivity() {

    private var isReady = false
    private lateinit var sessionStore: SessionStore
    private lateinit var authClient: AuthClient
    private lateinit var onboardingProgress: OnboardingProgress
    private lateinit var homeFeedRepository: HomeFeedRepository
    private lateinit var playbackPersistence: PlaybackPersistence

    private var routeState by mutableStateOf(RouteState())
    private var isExchangingCode by mutableStateOf(false)
    private var authErrorMessage by mutableStateOf<String?>(null)

    private var isLastFmAuthenticating by mutableStateOf(false)
    private var lastFmErrorMessage by mutableStateOf<String?>(null)

    private var isListenBrainzAuthenticating by mutableStateOf(false)
    private var listenBrainzErrorMessage by mutableStateOf<String?>(null)

    private var permissionAnswersRevision by mutableIntStateOf(0)
    private var openThemeSelectionOnNextHome by mutableStateOf(false)
    private var themeCatalogRefreshKey by mutableIntStateOf(0)

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
        themeCatalogRefreshKey++
        permissionAnswersRevision++
        verifySessionIfPresent()
        syncRipConnection()
    }

    /**
     * Reconciles the app-scoped rip socket / auto-rip coordinator against the current session.
     * Called on resume and after any session change (sign-in, sign-out, token refresh) so the
     * connection follows the session rather than any screen's composition.
     */
    private fun syncRipConnection() {
        if (!::sessionStore.isInitialized) return
        RipConnectionHolderInstance.getInstance(applicationContext, sessionStore).syncWithSession()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val keyValueStore = SharedPreferencesKeyValueStore(applicationContext)
        sessionStore = SessionStore(keyValueStore)
        authClient = AuthClient(sessionStore)
        onboardingProgress = OnboardingProgress(keyValueStore)
        homeFeedRepository = HomeFeedRepository(sessionStore)
        playbackPersistence = PlaybackPersistence(keyValueStore)
        verifySessionIfPresent()
        syncRipConnection()

        val restoredTokens = savedInstanceState?.getStringArrayList(ROUTE_STATE_KEY)
        routeState = restoredTokens?.let { routeStateFromTokens(it) }
            ?: reduce(
                RouteState(),
                RouteEvent.AppStarted(
                    hasSession = sessionStore.hasSession(),
                    permissionsCompleted = onboardingProgress.hasCompletedPermissions,
                    permissionsSatisfied = canLeaveOnboarding(
                        permissionCatalogueForAndroidApi(Build.VERSION.SDK_INT),
                        AndroidPermissionState(applicationContext),
                    ),
                    lastFmCredentials = sessionStore.getLastFmCredentials(),
                    listenBrainzCredentials = sessionStore.getListenBrainzCredentials(),
                ),
            )

        handleThemeSelectionIntent(intent)

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

        isReady = true

        handleAuthIntent(intent)

        setContent {
            val context = LocalContext.current
            val themeController = remember(context) { VisualThemeController(context) }
            val themeCatalog by themeController.state.collectAsState()
            LaunchedEffect(themeController, themeCatalogRefreshKey) { themeController.refresh() }
            val selectedTheme = themeCatalog.themes.firstOrNull {
                it.manifest.id == themeCatalog.selectedThemeId
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { permissionAnswersRevision++ }
            val permissionState = remember(context) {
                AndroidPermissionState(context) { permission -> permissionLauncher.launch(permission) }
            }
            var isPlayerDismissed by rememberSaveable { mutableStateOf(playbackPersistence.isPlayerDismissed()) }
            var activeArtworkUrl by rememberSaveable { mutableStateOf<String?>(null) }
            var showVisualRecovery by rememberSaveable { mutableStateOf(false) }

            CompositionLocalProvider(
                LocalVisualThemeController provides themeController,
                LocalVisualTheme provides selectedTheme,
                org.shilpo.laboon.theme.LocalVisualThemeRevision provides themeCatalog.revision,
            ) {
                LaboonExpressiveTheme(theme = selectedTheme, artworkUrl = activeArtworkUrl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val autofillManager = LocalAutofillManager.current
                        var customLastFmUsername by rememberSaveable { mutableStateOf("") }
                        var customLastFmPassword by rememberSaveable { mutableStateOf("") }
                        var customListenBrainzToken by rememberSaveable { mutableStateOf("") }
                        val permissionCatalogue = remember {
                            permissionCatalogueForAndroidApi(Build.VERSION.SDK_INT)
                        }
                        val continuePermissions: () -> Unit = continuePermissions@{
                            if (!canLeaveOnboarding(
                                    permissionCatalogue,
                                    permissionState
                                )
                            ) return@continuePermissions
                            onboardingProgress.markPermissionsCompleted()
                            dispatch(
                                RouteEvent.PermissionsCompleted(
                                    hasSession = sessionStore.hasSession(),
                                    lastFmCredentials = sessionStore.getLastFmCredentials(),
                                    listenBrainzCredentials = sessionStore.getListenBrainzCredentials(),
                                ),
                            )
                        }
                        val submitLastFm: (String, String) -> Unit =
                            submitLastFm@{ username, password ->
                                val cleanUsername = username.trim()
                                if (cleanUsername.isEmpty() || password.isEmpty()) {
                                    lastFmErrorMessage = getString(R.string.lastfm_error_empty)
                                    return@submitLastFm
                                }
                                val session = sessionStore.getSession()
                                if (session == null) {
                                    dispatch(RouteEvent.SessionMissing)
                                    return@submitLastFm
                                }
                                isLastFmAuthenticating = true
                                lastFmErrorMessage = null
                                lifecycleScope.launch {
                                    val result = authClient.loginLastFm(
                                        serverUrl = session.serverUrl,
                                        token = session.token,
                                        username = cleanUsername,
                                        password = password,
                                    )
                                    result.fold(
                                        onSuccess = {
                                            autofillManager?.commit()
                                            isLastFmAuthenticating = false
                                            dispatch(
                                                RouteEvent.LastFmConnected(
                                                    listenBrainzCredentials = sessionStore.getListenBrainzCredentials(),
                                                ),
                                            )
                                        },
                                        onFailure = { error ->
                                            autofillManager?.cancel()
                                            isLastFmAuthenticating = false
                                            if (error is UnauthorizedException) {
                                                dispatch(RouteEvent.SessionMissing)
                                            } else {
                                                lastFmErrorMessage = error.message
                                                    ?: getString(R.string.connect_auth_failed)
                                            }
                                        },
                                    )
                                }
                            }
                        val submitListenBrainz: (String) -> Unit = submitListenBrainz@{ token ->
                            val cleanToken = token.trim()
                            if (cleanToken.isEmpty()) {
                                listenBrainzErrorMessage =
                                    getString(R.string.listenbrainz_error_empty)
                                return@submitListenBrainz
                            }
                            val session = sessionStore.getSession()
                            if (session == null) {
                                dispatch(RouteEvent.SessionMissing)
                                return@submitListenBrainz
                            }
                            isListenBrainzAuthenticating = true
                            listenBrainzErrorMessage = null
                            lifecycleScope.launch {
                                val result = authClient.loginListenBrainz(
                                    serverUrl = session.serverUrl,
                                    token = session.token,
                                    listenbrainzToken = cleanToken,
                                )
                                result.fold(
                                    onSuccess = {
                                        isListenBrainzAuthenticating = false
                                        dispatch(RouteEvent.ListenBrainzConnected)
                                    },
                                    onFailure = { error ->
                                        isListenBrainzAuthenticating = false
                                        if (error is UnauthorizedException) {
                                            dispatch(RouteEvent.SessionMissing)
                                        } else {
                                            listenBrainzErrorMessage = error.message
                                                ?: getString(R.string.connect_auth_failed)
                                        }
                                    },
                                )
                            }
                        }
                        var contentVisible by remember { mutableStateOf(false) }
                        var splashDone by remember { mutableStateOf(false) }
                        LaunchedEffect(selectedTheme?.manifest?.id, splashDone) {
                            if (splashDone) return@LaunchedEffect
                            val customSplash =
                                "splash" in selectedTheme?.definition?.screens.orEmpty()
                            delay(if (customSplash) 2_500L else 900L)
                            contentVisible = true
                            splashDone = true
                        }

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
                                                (1f - contentAlpha) * 24.dp.toPx()
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
                                        Route.Welcome -> ThemeRouteContent(
                                            theme = selectedTheme,
                                            screenName = "welcome",
                                            presentation = VisualThemePresentation(
                                                values = mapOf(
                                                    "screen.title" to getString(R.string.home_title),
                                                    "screen.subtitle" to getString(R.string.welcome_tagline),
                                                ),
                                            ),
                                            availableActions = setOf(VisualThemeAction.CONTINUE),
                                            onAction = { action, _ ->
                                                if (action == VisualThemeAction.CONTINUE) dispatch(
                                                    RouteEvent.OnboardingStarted
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            WelcomeScreen(
                                                modifier = Modifier.fillMaxSize(),
                                                onLetsGoClick = { dispatch(RouteEvent.OnboardingStarted) },
                                            )
                                        }

                                        Route.Permissions -> {
                                            val permissionItems = permissionCatalogue.map { spec ->
                                                val granted = permissionState.isSatisfied(spec)
                                                mapOf(
                                                    "permission.id" to spec.id,
                                                    "permission.title" to getString(spec.titleRes),
                                                    "permission.description" to getString(spec.descriptionRes),
                                                    "permission.granted" to granted.toString(),
                                                    "permission.canRequest" to (!granted).toString(),
                                                )
                                            }
                                            ThemeRouteContent(
                                                theme = selectedTheme,
                                                screenName = "permissions",
                                                presentation = VisualThemePresentation(
                                                    values = mapOf(
                                                        "screen.title" to getString(R.string.permissions_title),
                                                        "screen.loading" to "false",
                                                        "permissions.continueEnabled" to canLeaveOnboarding(
                                                            permissionCatalogue,
                                                            permissionState
                                                        ).toString(),
                                                    ),
                                                    collections = mapOf("permissions" to permissionItems),
                                                ),
                                                availableActions = setOf(
                                                    VisualThemeAction.CONTINUE,
                                                    VisualThemeAction.REQUEST_PERMISSION,
                                                ),
                                                onAction = { action, parameters ->
                                                    when (action) {
                                                        VisualThemeAction.CONTINUE -> continuePermissions()
                                                        VisualThemeAction.REQUEST_PERMISSION -> permissionCatalogue
                                                            .firstOrNull { it.id == parameters["permission.id"] }
                                                            ?.let(permissionState::request)

                                                        else -> Unit
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                PermissionsScreen(
                                                    modifier = Modifier.fillMaxSize(),
                                                    state = permissionState,
                                                    permissionAnswersRevision = permissionAnswersRevision,
                                                    onContinue = continuePermissions,
                                                )
                                            }
                                        }

                                        Route.Connect -> ThemeRouteContent(
                                            theme = selectedTheme,
                                            screenName = "connect",
                                            presentation = VisualThemePresentation(
                                                values = mapOf(
                                                    "screen.title" to getString(R.string.connect_title),
                                                    "screen.subtitle" to getString(R.string.connect_subtitle),
                                                    "auth.isConnecting" to isExchangingCode.toString(),
                                                    "auth.error" to authErrorMessage.orEmpty(),
                                                ),
                                            ),
                                            availableActions = setOf(VisualThemeAction.OPEN_TELEGRAM),
                                            onAction = { action, _ ->
                                                if (action == VisualThemeAction.OPEN_TELEGRAM) openTelegramApp(
                                                    this@MainActivity
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            ConnectScreen(
                                                modifier = Modifier.fillMaxSize(),
                                                isAuthenticating = isExchangingCode,
                                                errorMessage = authErrorMessage,
                                            )
                                        }

                                        is Route.LastFm -> ThemeRouteContent(
                                            theme = selectedTheme,
                                            screenName = "lastFm",
                                            presentation = VisualThemePresentation(
                                                values = mapOf(
                                                    "screen.title" to getString(R.string.lastfm_title),
                                                    "screen.subtitle" to getString(R.string.lastfm_subtitle),
                                                    "auth.isConnecting" to isLastFmAuthenticating.toString(),
                                                    "auth.canSubmit" to (!isLastFmAuthenticating).toString(),
                                                    "auth.error" to lastFmErrorMessage.orEmpty(),
                                                ),
                                                inputValues = mapOf(
                                                    "auth.lastFm.username" to customLastFmUsername,
                                                    "auth.lastFm.password" to customLastFmPassword,
                                                ),
                                            ),
                                            availableActions = setOf(
                                                VisualThemeAction.BACK,
                                                VisualThemeAction.CONNECT_LAST_FM
                                            ),
                                            onAction = { action, parameters ->
                                                when (action) {
                                                    VisualThemeAction.BACK -> dispatch(RouteEvent.BackPressed)
                                                    VisualThemeAction.CONNECT_LAST_FM -> submitLastFm(
                                                        parameters["auth.lastFm.username"].orEmpty(),
                                                        parameters["auth.lastFm.password"].orEmpty(),
                                                    )

                                                    else -> Unit
                                                }
                                            },
                                            onFieldChange = { field, value ->
                                                when (field) {
                                                    "auth.lastFm.username" -> customLastFmUsername =
                                                        value

                                                    "auth.lastFm.password" -> customLastFmPassword =
                                                        value
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            LastFmScreen(
                                                credentials = route.credentials,
                                                hasSession = sessionStore.hasSession(),
                                                isConnecting = isLastFmAuthenticating,
                                                errorMessage = lastFmErrorMessage,
                                                onConnect = submitLastFm,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }

                                        is Route.ListenBrainz -> ThemeRouteContent(
                                            theme = selectedTheme,
                                            screenName = "listenBrainz",
                                            presentation = VisualThemePresentation(
                                                values = mapOf(
                                                    "screen.title" to getString(R.string.listenbrainz_title),
                                                    "screen.subtitle" to getString(R.string.listenbrainz_subtitle),
                                                    "auth.isConnecting" to isListenBrainzAuthenticating.toString(),
                                                    "auth.canSubmit" to (!isListenBrainzAuthenticating).toString(),
                                                    "auth.error" to listenBrainzErrorMessage.orEmpty(),
                                                ),
                                                inputValues = mapOf("auth.listenBrainz.token" to customListenBrainzToken),
                                            ),
                                            availableActions = setOf(
                                                VisualThemeAction.BACK,
                                                VisualThemeAction.CONNECT_LISTENBRAINZ
                                            ),
                                            onAction = { action, parameters ->
                                                when (action) {
                                                    VisualThemeAction.BACK -> dispatch(RouteEvent.BackPressed)
                                                    VisualThemeAction.CONNECT_LISTENBRAINZ -> submitListenBrainz(
                                                        parameters["auth.listenBrainz.token"].orEmpty(),
                                                    )

                                                    else -> Unit
                                                }
                                            },
                                            onFieldChange = { field, value ->
                                                if (field == "auth.listenBrainz.token") customListenBrainzToken =
                                                    value
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            ListenBrainzScreen(
                                                credentials = route.credentials,
                                                hasSession = sessionStore.hasSession(),
                                                isConnecting = isListenBrainzAuthenticating,
                                                errorMessage = listenBrainzErrorMessage,
                                                onConnect = submitListenBrainz,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }

                                        Route.Home -> HomeScreen(
                                            state = routeState,
                                            session = sessionStore.getSession(),
                                            repository = homeFeedRepository,
                                            onActiveTrackChange = { track, dismissed ->
                                                activeArtworkUrl = track?.artworkUrl
                                                isPlayerDismissed = dismissed
                                            },
                                            onEvent = ::dispatch,
                                            openThemeSelection = openThemeSelectionOnNextHome,
                                            onThemeSelectionOpened = {
                                                openThemeSelectionOnNextHome = false
                                            },
                                            onDisconnect = {
                                                sessionStore.signOut()
                                                onboardingProgress.reset()
                                                isPlayerDismissed = true
                                                syncRipConnection()
                                                dispatch(RouteEvent.SessionEnded)
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }

                            if (!splashDone) {
                                ThemeRouteContent(
                                    theme = selectedTheme,
                                    screenName = "splash",
                                    presentation = VisualThemePresentation(
                                        values = mapOf(
                                            "screen.title" to getString(R.string.app_name),
                                            "screen.subtitle" to getString(R.string.welcome_tagline),
                                        ),
                                    ),
                                    availableActions = setOf(VisualThemeAction.CONTINUE),
                                    onAction = { action, _ ->
                                        if (action == VisualThemeAction.CONTINUE) {
                                            contentVisible = true
                                            splashDone = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f),
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .widthIn(max = 360.dp)
                                                    .padding(24.dp),
                                                shape = MaterialTheme.shapes.extraLarge,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                tonalElevation = 6.dp,
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(28.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                                ) {
                                                    Text(
                                                        text = getString(R.string.app_name),
                                                        style = MaterialTheme.typography.headlineLarge,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    Text(
                                                        text = getString(R.string.welcome_tagline),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Button(
                                                        onClick = {
                                                            contentVisible = true
                                                            splashDone = true
                                                        },
                                                    ) {
                                                        Text("Continue")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (selectedTheme != null) {
                                LaboonExpressiveTheme(theme = null) {
                                    androidx.compose.material3.SmallFloatingActionButton(
                                        onClick = { showVisualRecovery = true },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .statusBarsPadding()
                                            .padding(8.dp),
                                    ) { Text("M3", style = MaterialTheme.typography.labelLarge) }
                                }
                            }
                            if (showVisualRecovery) org.shilpo.laboon.ui.screens.settings.VisualThemeRecoveryDialog(
                                onDismiss = { showVisualRecovery = false },
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
        handleThemeSelectionIntent(intent)
    }

    private fun handleThemeSelectionIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_THEME_SELECTION || routeState.current != Route.Home) return
        openThemeSelectionOnNextHome = true
        dispatch(RouteEvent.SettingsOpened)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val payload = authClient.parseConnectionUri(uri) ?: return

        isExchangingCode = true
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
                    isExchangingCode = false

                    syncRipConnection()
                    dispatch(
                        RouteEvent.SessionEstablished(
                            lastFmCredentials = creds,
                            listenBrainzCredentials = lbCreds,
                        ),
                    )
                },
                onFailure = { error ->
                    isExchangingCode = false
                    authErrorMessage = error.message ?: getString(R.string.connect_auth_failed)
                }
            )
        }
    }
}

private const val ACTION_OPEN_THEME_SELECTION = "org.shilpo.laboon.action.OPEN_THEME_SELECTION"
