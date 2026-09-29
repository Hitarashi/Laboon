@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.connect

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.ui.design.LandingColumn
import org.shilpo.laboon.ui.design.LandingReveal
import org.shilpo.laboon.ui.design.PrimaryActionZone
import org.shilpo.laboon.ui.design.ScreenError
import org.shilpo.laboon.ui.design.ScreenScaffold

private fun openTelegramApp(context: Context) {
    val telegramIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("tg://")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val fallbackIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://telegram.org"),
    )
    try {
        context.startActivity(telegramIntent)
    } catch (_: Exception) {
        context.startActivity(fallbackIntent)
    }
}

@Composable
fun ConnectScreen(
    modifier: Modifier = Modifier,
    isAuthenticating: Boolean = false,
    errorMessage: String? = null,
) {
    val context = LocalContext.current
    val error = remember(errorMessage) { errorMessage?.let(ScreenError::Remote) }

    ScreenScaffold(modifier = modifier) {
        LandingColumn {
            LandingReveal {
                Column {
                    Icon(
                        painter = painterResource(R.drawable.ic_splash_logo),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.connect_title),
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.connect_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryActionZone(
                label = stringResource(R.string.connect_telegram_button),
                busyLabel = stringResource(R.string.connect_authenticating),
                isBusy = isAuthenticating,
                onClick = { openTelegramApp(context) },
                error = error,
                footer = {
                    Text(
                        text = stringResource(R.string.connect_telegram_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
    }
}
