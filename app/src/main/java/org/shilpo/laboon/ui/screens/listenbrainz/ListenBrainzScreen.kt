@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.listenbrainz

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.ListenBrainzCredentials
import org.shilpo.laboon.ui.design.LandingColumn
import org.shilpo.laboon.ui.design.LandingReveal
import org.shilpo.laboon.ui.design.PrimaryActionZone
import org.shilpo.laboon.ui.design.ScreenError
import org.shilpo.laboon.ui.design.ScreenScaffold
import org.shilpo.laboon.ui.design.mergeScreenErrors

@Composable
fun ListenBrainzScreen(
    credentials: ListenBrainzCredentials?,
    hasSession: Boolean,
    modifier: Modifier = Modifier,
    isConnecting: Boolean = false,
    errorMessage: String? = null,
    onConnect: (token: String) -> Unit = {},
) {
    val emptyErrorText = stringResource(R.string.listenbrainz_error_empty)
    var token by rememberSaveable(credentials?.token) {
        mutableStateOf(credentials?.normalized()?.token.orEmpty())
    }
    var localError by remember { mutableStateOf<ScreenError?>(null) }
    val remoteError = remember(errorMessage) { errorMessage?.let(ScreenError::Remote) }

    ListenBrainzContent(
        token = token,
        hasSession = hasSession,
        isConnecting = isConnecting,
        error = mergeScreenErrors(remote = remoteError, local = localError),
        onTokenChange = {
            token = it
            localError = null
        },
        onConnect = {
            if (!hasSession) return@ListenBrainzContent
            val trimmedToken = token.trim()
            if (trimmedToken.isEmpty()) {
                localError = ScreenError.Validation(emptyErrorText)
            } else {
                onConnect(trimmedToken)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ListenBrainzContent(
    token: String,
    hasSession: Boolean,
    isConnecting: Boolean,
    error: ScreenError?,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current

    ScreenScaffold(modifier = modifier) {
        LandingColumn(imeAware = true, scrollable = true) {
            LandingReveal {
                Column {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_listenbrainz),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.listenbrainz_title),
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.listenbrainz_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.listenbrainz_token_label)) },
                placeholder = { Text(stringResource(R.string.listenbrainz_token_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onConnect()
                    },
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { uriHandler.openUri("https://listenbrainz.org/profile/") },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.listenbrainz_token_helper),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            if (hasSession) {
                PrimaryActionZone(
                    label = stringResource(R.string.listenbrainz_connect_button),
                    busyLabel = stringResource(R.string.listenbrainz_connecting),
                    isBusy = isConnecting,
                    onClick = onConnect,
                    error = error,
                )
            }
        }
    }
}
