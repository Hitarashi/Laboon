@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.ui.design.UserAvatar
import org.shilpo.laboon.ui.design.painterResource

@Composable
internal fun HomeTopBar(
    session: AuthSession?,
    onOpenSettings: () -> Unit,
    onOpenRipVisualizer: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    collapseProgress: Float = 0f,
) {
    val containerColor by animateColorAsState(
        targetValue = if (collapseProgress > 0.5f) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "homeTopBarColor",
    )
    val settingsLabel = stringResource(R.string.settings_title)
    Surface(color = containerColor, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.weight(1f, fill = false),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.app_icon_small),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_search),
                        contentDescription = stringResource(R.string.nav_search),
                        modifier = Modifier.size(24.dp),
                    )
                }
                FilledTonalIconButton(onClick = onOpenRipVisualizer, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_download),
                        contentDescription = stringResource(R.string.home_rip_control),
                        modifier = Modifier.size(24.dp),
                    )
                }
                UserAvatar(
                    session = session,
                    onClick = onOpenSettings,
                    size = 48.dp,
                    modifier = Modifier.semantics { contentDescription = settingsLabel },
                )
            }
        }
    }
}
