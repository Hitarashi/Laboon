@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.lastfm

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.LastFmCredentials
import org.shilpo.laboon.ui.design.LandingColumn
import org.shilpo.laboon.ui.design.LandingReveal
import org.shilpo.laboon.ui.design.PrimaryActionZone
import org.shilpo.laboon.ui.design.ScreenError
import org.shilpo.laboon.ui.design.ScreenScaffold
import org.shilpo.laboon.ui.design.mergeScreenErrors
import kotlin.math.hypot
import kotlin.math.sin

private val LastFmRed = Color(0xFFD51007)

@Composable
fun LastFmScreen(
    credentials: LastFmCredentials?,
    hasSession: Boolean,
    modifier: Modifier = Modifier,
    isConnecting: Boolean = false,
    errorMessage: String? = null,
    onConnect: (username: String, password: String) -> Unit = { _, _ -> },
) {
    val emptyErrorText = stringResource(R.string.lastfm_error_empty)
    var username by rememberSaveable(credentials?.username) {
        mutableStateOf(credentials?.normalized()?.username.orEmpty())
    }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var localError by remember { mutableStateOf<ScreenError?>(null) }
    val remoteError = remember(errorMessage) { errorMessage?.let(ScreenError::Remote) }

    LastFmContent(
        username = username,
        password = password,
        passwordVisible = passwordVisible,
        credentials = credentials,
        hasSession = hasSession,
        isConnecting = isConnecting,
        error = mergeScreenErrors(remote = remoteError, local = localError),
        onUsernameChange = {
            username = it
            localError = null
        },
        onPasswordChange = {
            password = it
            localError = null
        },
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        onConnect = {
            if (!hasSession) return@LastFmContent
            val trimmedUsername = username.trim()
            val trimmedPassword = password.trim()
            if (trimmedUsername.isEmpty() || trimmedPassword.isEmpty()) {
                localError = ScreenError.Validation(emptyErrorText)
            } else {
                onConnect(trimmedUsername, trimmedPassword)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun LastFmContent(
    username: String,
    password: String,
    passwordVisible: Boolean,
    credentials: LastFmCredentials?,
    hasSession: Boolean,
    isConnecting: Boolean,
    error: ScreenError?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    ScreenScaffold(modifier = modifier) {
        LandingColumn(imeAware = true, scrollable = true) {
            LandingReveal {
                Column {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(LastFmRed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lastfm),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.lastfm_title),
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.lastfm_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                enabled = !isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
                label = { Text(stringResource(R.string.lastfm_username_label)) },
                placeholder = { Text(stringResource(R.string.lastfm_username_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                enabled = !isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password },
                label = { Text(stringResource(R.string.lastfm_password_label)) },
                placeholder = { Text(stringResource(R.string.lastfm_password_placeholder)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        PasswordVisibilityIcon(
                            visible = passwordVisible,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onConnect()
                    },
                ),
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            if (hasSession) {
                PrimaryActionZone(
                    label = stringResource(R.string.lastfm_connect_button),
                    busyLabel = stringResource(R.string.lastfm_connecting),
                    isBusy = isConnecting,
                    onClick = onConnect,
                    error = error,
                )
            }
        }
    }
}

@Composable
private fun PasswordVisibilityIcon(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val motionScheme = MaterialTheme.motionScheme
    val progress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "PasswordVisibilitySlicerProgress",
    )
    val eyePainter = painterResource(R.drawable.ic_eye)
    val eyeClosePainter = painterResource(R.drawable.ic_eye_close)

    val clampedProgress = progress.coerceIn(0f, 1f)
    val motionPhase = sin(clampedProgress * Math.PI.toFloat())
    val rotationAngle = motionPhase * -10f
    val scaleFactor = 1f - motionPhase * 0.08f

    Canvas(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                rotationZ = rotationAngle
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
    ) {
        if (progress <= 0.001f) {
            with(eyePainter) {
                draw(size, colorFilter = ColorFilter.tint(tint))
            }
        } else if (progress >= 0.999f) {
            with(eyeClosePainter) {
                draw(size, colorFilter = ColorFilter.tint(tint))
            }
        } else {
            val p1X = 0.88f * size.width
            val p1Y = 0.12f * size.height
            val p2X = 0.12f * size.width
            val p2Y = 0.88f * size.height

            val vx = p2X - p1X
            val vy = p2Y - p1Y
            val len = hypot(vx, vy)
            val ux = vx / len
            val uy = vy / len
            val nx = -uy
            val ny = ux

            val cutCenterX = p1X + vx * clampedProgress
            val cutCenterY = p1Y + vy * clampedProgress
            val extent = size.maxDimension * 3f

            val c1X = cutCenterX + nx * extent
            val c1Y = cutCenterY + ny * extent
            val c2X = cutCenterX - nx * extent
            val c2Y = cutCenterY - ny * extent

            val b1X = c1X - ux * extent
            val b1Y = c1Y - uy * extent
            val b2X = c2X - ux * extent
            val b2Y = c2Y - uy * extent

            val a1X = c1X + ux * extent
            val a1Y = c1Y + uy * extent
            val a2X = c2X + ux * extent
            val a2Y = c2Y + uy * extent

            val closePath = Path().apply {
                moveTo(b1X, b1Y)
                lineTo(c1X, c1Y)
                lineTo(c2X, c2Y)
                lineTo(b2X, b2Y)
                close()
            }

            val openPath = Path().apply {
                moveTo(a1X, a1Y)
                lineTo(c1X, c1Y)
                lineTo(c2X, c2Y)
                lineTo(a2X, a2Y)
                close()
            }

            clipPath(openPath) {
                with(eyePainter) {
                    draw(size, colorFilter = ColorFilter.tint(tint))
                }
            }
            clipPath(closePath) {
                with(eyeClosePainter) {
                    draw(size, colorFilter = ColorFilter.tint(tint))
                }
            }
        }
    }
}

