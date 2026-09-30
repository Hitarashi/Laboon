@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.ui.design.AppCardShape
import org.shilpo.laboon.ui.design.ScreenScaffold
import org.shilpo.laboon.ui.design.SegmentedSection
import org.shilpo.laboon.ui.design.UserAvatar
import org.shilpo.laboon.ui.design.userDisplayName

private data class SettingItem(
    val title: String,
    val subtitle: String? = null,
)

private val TopBarOuterVerticalPadding = 8.dp
private val TopBarInnerVerticalPadding = 6.dp
private val TopBarNavButtonHeight = 48.dp

private val TopBarHeight =
    TopBarNavButtonHeight + (TopBarOuterVerticalPadding + TopBarInnerVerticalPadding) * 2

private fun settingSupportingContent(
    items: List<SettingItem>,
): (@Composable (item: SettingItem) -> Unit)? =
    if (items.any { it.subtitle != null }) {
        { item ->
            item.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    } else {
        null
    }

@Composable
private fun SettingItemsSection(
    title: String,
    items: List<SettingItem>,
) {
    SegmentedSection(
        title = title,
        items = items,
        titleModifier = Modifier.padding(start = 4.dp),
        supportingContent = settingSupportingContent(items),
    ) { item ->
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
    }
}

@Composable
fun SettingsScreen(
    session: AuthSession?,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackItems = remember {
        listOf(
            SettingItem("Streaming Quality", "High (320 kbps)"),
            SettingItem("Audio Normalization", "Balance volume across all tracks"),
            SettingItem("Cache & Offline", "512 MB cached"),
        )
    }

    val servicesItems = remember(session?.serverUrl) {
        listOf(
            SettingItem("Telegram Server", session?.serverUrl ?: "Connected"),
            SettingItem("Last.fm Scrobbling", "Enabled"),
        )
    }

    val aboutItems = remember {
        listOf(
            SettingItem("Version", "1.0.0 (Expressive)"),
            SettingItem("Open Source", "github.com/hitarashi/Laboon"),
        )
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ScreenScaffold(
        modifier = modifier,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = TopBarOuterVerticalPadding),
                shape = MaterialTheme.shapes.extraLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = TopBarInnerVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(90f),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLargeEmphasized.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + TopBarHeight,
                bottom = bottomInset + 24.dp,
                start = 20.dp,
                end = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        UserAvatar(
                            session = session,
                            size = 64.dp,
                            fallbackIconSize = 36.dp,
                            initialTextStyle = MaterialTheme.typography.headlineSmallEmphasized,
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userDisplayName(session?.user)
                                    ?: stringResource(R.string.home_user_fallback),
                                style = MaterialTheme.typography.titleLargeEmphasized.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                            session?.user?.username?.let { username ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "@$username",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item { SettingItemsSection("Playback & Audio", playbackItems) }

            item { SettingItemsSection("Services", servicesItems) }

            item { SettingItemsSection("About", aboutItems) }

            item {
                OutlinedButton(
                    onClick = onDisconnect,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentPadding = ButtonDefaults.LargeContentPadding,
                ) {
                    Text(
                        text = stringResource(R.string.home_disconnect_button),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
        }
    }
}
