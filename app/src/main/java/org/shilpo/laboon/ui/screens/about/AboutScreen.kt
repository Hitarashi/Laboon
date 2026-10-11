@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
)

package org.shilpo.laboon.ui.screens.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.shilpo.laboon.update.AppUpdatePreferences
import org.shilpo.laboon.update.GithubUpdateRepository
import org.shilpo.laboon.update.UpdateCheckResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.shilpo.laboon.R
import org.shilpo.laboon.ui.design.AppCardShape
import org.shilpo.laboon.ui.design.ScreenScaffold
import org.shilpo.laboon.ui.design.painterResource

private val TopBarOuterVerticalPadding = 8.dp
private val TopBarInnerVerticalPadding = 6.dp
private val TopBarNavButtonHeight = 48.dp
private val TopBarHeight =
    TopBarNavButtonHeight + (TopBarOuterVerticalPadding + TopBarInnerVerticalPadding) * 2

private const val GITHUB_REPO_URL = "https://github.com/hitarashi/Laboon"
private const val GITHUB_ISSUES_URL = "https://github.com/hitarashi/Laboon/issues"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    repository: AboutRepository = remember { GithubAboutRepository() },
) {
    var aboutData by remember { mutableStateOf<AboutData?>(null) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val updatePreferences = remember { AppUpdatePreferences(context) }
    val updateRepository = remember { GithubUpdateRepository(preferences = updatePreferences) }
    var updateCheckResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        aboutData = repository.getAboutData()
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ScreenScaffold(
        modifier = modifier,
        topBar = {
            AboutTopAppBar(
                title = "About Laboon",
                onBack = onBack,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + TopBarHeight + 8.dp,
                bottom = bottomInset + 32.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Hero Identity Card
            item {
                AboutHeroIdentityCard(
                    data = aboutData,
                    onCopyBuildInfo = { versionText ->
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("Laboon Version", versionText)
                        )
                        Toast.makeText(
                            context,
                            "$versionText copied to clipboard",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                )
            }

            // App Updates Section
            item {
                AboutUpdateSection(
                    result = updateCheckResult,
                    isChecking = isCheckingUpdates,
                    onCheckUpdates = {
                        if (isCheckingUpdates) return@AboutUpdateSection
                        isCheckingUpdates = true
                        coroutineScope.launch {
                            try {
                                val result = updateRepository.checkForUpdate(force = true)
                                updateCheckResult = result
                            } catch (e: Exception) {
                                updateCheckResult = UpdateCheckResult.Failed(e.message ?: "Failed to check for updates")
                            } finally {
                                isCheckingUpdates = false
                            }
                        }
                    },
                    onDownloadApk = { url ->
                        try {
                            uriHandler.openUri(url)
                        } catch (_: Exception) {}
                    },
                    onViewReleaseNotes = { url ->
                        try {
                            uriHandler.openUri(url)
                        } catch (_: Exception) {}
                    },
                )
            }

            // Current Release Changelog Section
            aboutData?.let { data ->
                if (data.changelogItems.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "WHAT'S NEW IN V${data.versionName.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            ) {
                                data.changelogItems.forEachIndexed { index, changelogItem ->
                                    ChangelogSegmentedRow(
                                        text = changelogItem,
                                        index = index,
                                        totalCount = data.changelogItems.size,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Creator & Lead Developer Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "CREATOR & LEAD DEVELOPER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                    )

                    aboutData?.let { data ->
                        ContributorCard(
                            contributor = data.leadDeveloper,
                            isLead = true,
                            onClick = {
                                try {
                                    uriHandler.openUri(data.leadDeveloper.htmlUrl)
                                } catch (_: Exception) {
                                }
                            },
                        )
                    }
                }
            }

            // Additional Contributors Section (if any beyond lead)
            aboutData?.let { data ->
                val otherContributors = data.contributors.filter {
                    !it.login.equals(data.leadDeveloper.login, ignoreCase = true)
                }

                if (otherContributors.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "CONTRIBUTORS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            ) {
                                otherContributors.forEachIndexed { index, contributor ->
                                    ContributorSegmentedRow(
                                        contributor = contributor,
                                        index = index,
                                        totalCount = otherContributors.size,
                                        onClick = {
                                            try {
                                                uriHandler.openUri(contributor.htmlUrl)
                                            } catch (_: Exception) {
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Links Section (No redundancy, clean and direct)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "PROJECT LINKS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        LinkSegmentedRow(
                            title = "GitHub Repository",
                            subtitle = "hitarashi/Laboon",
                            index = 0,
                            totalCount = 2,
                            onClick = {
                                try {
                                    uriHandler.openUri(GITHUB_REPO_URL)
                                } catch (_: Exception) {
                                }
                            },
                        )
                        LinkSegmentedRow(
                            title = "Report an Issue",
                            subtitle = "Submit bugs or feature requests",
                            index = 1,
                            totalCount = 2,
                            onClick = {
                                try {
                                    uriHandler.openUri(GITHUB_ISSUES_URL)
                                } catch (_: Exception) {
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hero identity card showing clean app logo and version badge.
 */
@Composable
private fun AboutHeroIdentityCard(
    data: AboutData?,
    onCopyBuildInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Info Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Laboon",
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "High-Fidelity Telegram & Hi-Res Audio Stream",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Clean Version Badge (No debug, no code numbers, no Compose clutter)
            val versionName = data?.versionName ?: "0.2.1"
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.clickable {
                    onCopyBuildInfo("Laboon v$versionName")
                },
            ) {
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Segmented row displaying a single changelog item for the current release.
 */
@Composable
private fun ChangelogSegmentedRow(
    text: String,
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = totalCount),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        },
        content = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
            )
        },
    )
}

/**
 * Elevated card showing Lead Developer with GitHub avatar and commit stats.
 */
@Composable
private fun ContributorCard(
    contributor: Contributor,
    isLead: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "leadDevScale",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = contributor.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = contributor.name ?: contributor.login,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = if (isLead) "Lead" else "Contributor",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "@${contributor.login} • ${contributor.role}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (contributor.contributions > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${contributor.contributions} contributions on GitHub",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(18.dp)
                    .rotate(270f),
            )
        }
    }
}

/**
 * Segmented row for additional project contributors.
 */
@Composable
private fun ContributorSegmentedRow(
    contributor: Contributor,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = totalCount),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        leadingContent = {
            AsyncImage(
                model = contributor.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        },
        content = {
            Text(
                text = contributor.name ?: contributor.login,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = "@${contributor.login}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (contributor.contributions > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = "${contributor.contributions}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        },
    )
}

/**
 * Clickable row for project links.
 */
@Composable
private fun LinkSegmentedRow(
    title: String,
    subtitle: String,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = totalCount),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(18.dp)
                    .rotate(270f),
            )
        },
    )
}

/**
 * Top app bar with rounded elevated surface.
 */
@Composable
private fun AboutTopAppBar(
    title: String,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
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
            IconButton(onClick = onBack) {
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
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

/**
 * Expressive update status card displaying current version, check action,
 * and direct download buttons when a release is available.
 */
@Composable
private fun AboutUpdateSection(
    result: UpdateCheckResult?,
    isChecking: Boolean,
    onCheckUpdates: () -> Unit,
    onDownloadApk: (String) -> Unit,
    onViewReleaseNotes: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "APP UPDATES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                when (result) {
                                    is UpdateCheckResult.Available -> MaterialTheme.colorScheme.primaryContainer
                                    is UpdateCheckResult.UpToDate -> MaterialTheme.colorScheme.tertiaryContainer
                                    is UpdateCheckResult.Failed -> MaterialTheme.colorScheme.errorContainer
                                    null -> MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            val iconRes = when (result) {
                                is UpdateCheckResult.Available -> R.drawable.ic_cloud_download
                                is UpdateCheckResult.UpToDate -> R.drawable.ic_check
                                is UpdateCheckResult.Failed -> R.drawable.ic_info_circle
                                null -> R.drawable.ic_clockwise_clock
                            }
                            val iconTint = when (result) {
                                is UpdateCheckResult.Available -> MaterialTheme.colorScheme.primary
                                is UpdateCheckResult.UpToDate -> MaterialTheme.colorScheme.tertiary
                                is UpdateCheckResult.Failed -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val statusTitle = when {
                            isChecking -> stringResource(R.string.update_status_checking)
                            result is UpdateCheckResult.Available -> stringResource(
                                R.string.update_status_available,
                                result.release.versionName
                            )
                            result is UpdateCheckResult.UpToDate -> stringResource(
                                R.string.update_status_up_to_date,
                                result.currentVersion
                            )
                            result is UpdateCheckResult.Failed -> stringResource(R.string.update_status_error)
                            else -> "Release Channel: Stable"
                        }
                        val statusSubtitle = when {
                            isChecking -> "Connecting to GitHub Releases API…"
                            result is UpdateCheckResult.Available -> "A new version of Laboon is ready to install."
                            result is UpdateCheckResult.UpToDate -> "You are running the latest version."
                            result is UpdateCheckResult.Failed -> result.error
                            else -> "Check GitHub releases for updates and new features."
                        }

                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }

                // Actions
                if (result is UpdateCheckResult.Available) {
                    val release = result.release
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onDownloadApk(release.downloadUrl ?: release.htmlUrl) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_cloud_download),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.update_action_download))
                        }

                        OutlinedButton(
                            onClick = { onViewReleaseNotes(release.htmlUrl) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_info_circle),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.update_action_view))
                        }
                    }
                } else {
                    FilledTonalButton(
                        onClick = onCheckUpdates,
                        enabled = !isChecking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.update_status_checking))
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_clockwise_clock),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.update_check_button))
                        }
                    }
                }
            }
        }
    }
}

