@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
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
import org.shilpo.laboon.ui.screens.lastfm.LastFmScreen
import org.shilpo.laboon.ui.screens.welcome.WelcomeScreen
import org.shilpo.laboon.ui.theme.AppTypography

private sealed interface Screen {
    data object Welcome : Screen
    data object Connect : Screen
    data class LastFm(val credentials: LastFmCredentials?) : Screen
}

class MainActivity : ComponentActivity() {

    private var isReady = false
    private lateinit var authStorage: AuthStorage
    private lateinit var authRepository: AuthRepository

    private var currentScreen by mutableStateOf<Screen>(Screen.Welcome)
    private var isAuthenticating by mutableStateOf(false)
    private var authErrorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authStorage = AuthStorage(applicationContext)
        authRepository = AuthRepository(authStorage)

        if (authStorage.hasSession()) {
            currentScreen = Screen.LastFm(authStorage.getLastFmCredentials())
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
                    var contentVisible by remember { mutableStateOf(false) }
                    var splashDone by remember { mutableStateOf(false) }

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
                                    alpha = contentAlpha
                                    translationY =
                                        (1f - contentAlpha) * SplashConfig.Reveal.RISE_DP.dp.toPx()
                                },
                            transitionSpec = {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { -it } + fadeOut())
                            },
                            label = "screenTransition",
                        ) { screen ->
                            when (screen) {
                                Screen.Welcome -> WelcomeScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    onLetsGoClick = { currentScreen = Screen.Connect },
                                )

                                Screen.Connect -> ConnectScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    isAuthenticating = isAuthenticating,
                                    errorMessage = authErrorMessage,
                                )

                                is Screen.LastFm -> LastFmScreen(
                                    credentials = screen.credentials,
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
                    currentScreen = Screen.LastFm(creds)
                },
                onFailure = { error ->
                    isAuthenticating = false
                    authErrorMessage = error.message ?: getString(R.string.connect_auth_failed)
                }
            )
        }
    }
}
