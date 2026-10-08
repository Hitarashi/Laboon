@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.BuildConfig
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.ui.design.AppCardShape
import org.shilpo.laboon.ui.design.ScreenScaffold
import org.shilpo.laboon.ui.design.UserAvatar
import org.shilpo.laboon.ui.design.userDisplayName

import org.shilpo.laboon.ui.screens.about.AboutScreen

/**
 * Display theme options for Laboon.
 */
enum class ThemeMode(val label: String, val shortLabel: String) {
    SYSTEM("Follow System", "System"),
    LIGHT("Light Mode", "Light"),
    DARK("Dark Mode", "Dark"),
}

/**
 * Settings category destinations in Laboon.
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val badge: String? = null,
    val plannedFeatures: List<String>,
) {
    AUDIO_PIPELINE(
        title = "Audio & Pipeline",
        subtitle = "Dolby Atmos, Bit-perfect DAC, ALAC/FLAC & ReplayGain",
        iconRes = R.drawable.ic_pipeline_resampler,
        badge = "Hi-Res",
        plannedFeatures = listOf(
            "Bit-perfect USB DAC hardware direct pass-through",
            "Dolby Atmos binaural decoding & spatial profile",
            "ReplayGain volume normalization and pre-amp control",
            "ALAC and FLAC 24-bit/192kHz resampling settings",
            "Custom buffer sizing and audio underrun defense",
        ),
    ),
    LYRICS_ENGINE(
        title = "Lyrics Engine",
        subtitle = "Synced lyrics, Romaji/Pinyin romanization & translation",
        iconRes = R.drawable.ic_player_lyrics,
        badge = "Synced",
        plannedFeatures = listOf(
            "Syllable & line-by-line synchronized lyric animation",
            "Automatic Japanese (Romaji), Chinese (Pinyin), Korean (Hangul) romanization",
            "On-device dynamic lyrics translation powered by LyricsTranslator",
            "Lyrics providers priority (Lyricsporn, LRCLIB, local metadata)",
            "Kinetic typography glow and liquid background blur",
        ),
    ),
    APPEARANCE(
        title = "Appearance & Customization",
        subtitle = "Dynamic artwork colors, Liquid Glass blur & OLED black",
        iconRes = R.drawable.ic_appearance_palette,
        plannedFeatures = listOf(
            "Material 3 Expressive dynamic color palette engine",
            "Dynamic artwork theming (extract accents from playing track)",
            "Liquid Glass blur intensity, frosted diffusion & fallback toggles",
            "OLED pure black background mode for AMOLED screens",
            "Expressive spring animation stiffness and motion scheme tuning",
            "Custom app icons and typography scaling",
        ),
    ),
    SERVICES(
        title = "Services & Scrobbling",
        subtitle = "Peerless server, Last.fm, ListenBrainz & auto-rip",
        iconRes = R.drawable.ic_services_scrobble,
        badge = "Active",
        plannedFeatures = listOf(
            "Peerless server MTProto connection, timeout & chunk streaming logic",
            "Last.fm real-time scrobble submission & playback threshold",
            "ListenBrainz scrobbling integration",
            "Background track auto-ripping coordinator & cloud caching",
            "Custom DNS-over-HTTPS & proxy tunneling configuration",
        ),
    ),
    STORAGE(
        title = "Storage & Cache",
        subtitle = "Audio cache size, downloaded tracks & storage directory",
        iconRes = R.drawable.ic_perm_storage,
        plannedFeatures = listOf(
            "Local audio cache limit slider & automatic cleanup policy",
            "One-tap audio cache clearing",
            "Downloaded offline tracks storage folder selector",
            "Cellular download constraints & automatic preload restrictions",
        ),
    ),
    ABOUT(
        title = "About Laboon",
        subtitle = "Version, developer links, open source licenses & credits",
        iconRes = R.drawable.ic_info_circle,
        badge = "v${BuildConfig.VERSION_NAME}",
        plannedFeatures = listOf(
            "Laboon app build specifications & changelog history",
            "GitHub repository & issue reporting links",
            "Open source software licenses attribution",
            "Core contributors & project credits",
        ),
    ),
}

private val TopBarOuterVerticalPadding = 8.dp
private val TopBarInnerVerticalPadding = 6.dp
private val TopBarNavButtonHeight = 48.dp
private val TopBarHeight =
    TopBarNavButtonHeight + (TopBarOuterVerticalPadding + TopBarInnerVerticalPadding) * 2

@Composable
fun SettingsScreen(
    session: AuthSession?,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    // Intercept hardware/system back gestures to return to main settings hub
    BackHandler(enabled = activeCategory != null) {
        activeCategory = null
    }

    AnimatedContent(
        targetState = activeCategory,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(
                    initialOffsetX = { it / 3 },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) + fadeIn()).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { -it / 3 },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    ) + fadeOut()
                )
            } else {
                (slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) + fadeIn()).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { it / 3 },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    ) + fadeOut()
                )
            }
        },
        label = "SettingsNavigationTransition",
        modifier = modifier.fillMaxSize(),
    ) { category ->
        when (category) {
            null -> {
                SettingsHubScreen(
                    session = session,
                    onBack = onBack,
                    onSelectCategory = { activeCategory = it },
                    onDisconnect = onDisconnect,
                )
            }

            SettingsCategory.ABOUT -> {
                AboutScreen(
                    onBack = { activeCategory = null },
                )
            }

            else -> {
                SettingsComingSoonScreen(
                    category = category,
                    onBack = { activeCategory = null },
                )
            }
        }
    }
}

/**
 * Root Settings Hub displaying user profile, interactive theme selector, and M3 Expressive segmented category groups.
 */
@Composable
private fun SettingsHubScreen(
    session: AuthSession?,
    onBack: () -> Unit,
    onSelectCategory: (SettingsCategory) -> Unit,
    onDisconnect: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var selectedThemeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }

    ScreenScaffold(
        topBar = {
            SettingsTopAppBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + TopBarHeight,
                bottom = bottomInset + 32.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Profile & Server Status Card
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

                            Spacer(modifier = Modifier.height(6.dp))

                            // Server connection badge
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 3.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                    Text(
                                        text = session?.serverUrl ?: "Connected to Peerless",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Theme & Display Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "THEME & DISPLAY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                    )

                    ThemeModeSelectorCard(
                        selectedMode = selectedThemeMode,
                        onModeSelected = { selectedThemeMode = it },
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        SettingsCategorySegmentedRow(
                            category = SettingsCategory.APPEARANCE,
                            index = 0,
                            totalCount = 1,
                            onClick = { onSelectCategory(SettingsCategory.APPEARANCE) },
                        )
                    }
                }
            }

            // Group: Playback & Audio
            item {
                SettingsSectionGroup(
                    title = "PLAYBACK & AUDIO",
                    items = listOf(
                        SettingsCategory.AUDIO_PIPELINE,
                        SettingsCategory.LYRICS_ENGINE,
                    ),
                    onSelectItem = onSelectCategory,
                )
            }

            // Group: Storage & Cache
            item {
                SettingsSectionGroup(
                    title = "STORAGE & CACHE",
                    items = listOf(
                        SettingsCategory.STORAGE,
                    ),
                    onSelectItem = onSelectCategory,
                )
            }

            // Group: Ecosystem & Services
            item {
                SettingsSectionGroup(
                    title = "ECOSYSTEM & SERVICES",
                    items = listOf(
                        SettingsCategory.SERVICES,
                    ),
                    onSelectItem = onSelectCategory,
                )
            }

            // Group: About
            item {
                SettingsSectionGroup(
                    title = "ABOUT",
                    items = listOf(
                        SettingsCategory.ABOUT,
                    ),
                    onSelectItem = onSelectCategory,
                )
            }

            // Disconnect Button
            item {
                OutlinedButton(
                    onClick = onDisconnect,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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

/**
 * Interactive M3 Expressive Theme Mode selector card (System, Light, Dark).
 */
@Composable
private fun ThemeModeSelectorCard(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Light, Dark, or Match System",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = selectedMode.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // 3-Option Segmented Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        label = "themeSegmentScale",
                    )

                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "themeSegmentBg",
                    )

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "themeSegmentContent",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(containerColor)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onModeSelected(mode) },
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ThemeModeIcon(
                                mode = mode,
                                tint = contentColor,
                            )
                            Text(
                                text = mode.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Procedural Vector Graphics for Theme Modes: Sun (Light), Moon (Dark), and Auto/Split (System).
 */
@Composable
private fun ThemeModeIcon(
    mode: ThemeMode,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val sizePx = size.minDimension
        val strokeWidth = 1.6.dp.toPx()
        when (mode) {
            ThemeMode.LIGHT -> {
                // Central sun circle
                drawCircle(
                    color = tint,
                    radius = sizePx * 0.25f,
                    style = Stroke(width = strokeWidth),
                )
                // 8 sun rays
                val rayLength = sizePx * 0.11f
                val rayInnerRadius = sizePx * 0.36f
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val startX = (center.x + rayInnerRadius * kotlin.math.cos(angle)).toFloat()
                    val startY = (center.y + rayInnerRadius * kotlin.math.sin(angle)).toFloat()
                    val endX =
                        (center.x + (rayInnerRadius + rayLength) * kotlin.math.cos(angle)).toFloat()
                    val endY =
                        (center.y + (rayInnerRadius + rayLength) * kotlin.math.sin(angle)).toFloat()
                    drawLine(
                        color = tint,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }

            ThemeMode.DARK -> {
                // Crescent Moon path
                val r = sizePx * 0.40f
                val path = Path().apply {
                    arcTo(
                        rect = Rect(center.x - r, center.y - r, center.x + r, center.y + r),
                        startAngleDegrees = -70f,
                        sweepAngleDegrees = 200f,
                        forceMoveTo = true,
                    )
                    arcTo(
                        rect = Rect(
                            center.x - r * 0.45f,
                            center.y - r * 0.85f,
                            center.x + r * 1.05f,
                            center.y + r * 0.85f
                        ),
                        startAngleDegrees = 110f,
                        sweepAngleDegrees = -160f,
                        forceMoveTo = false,
                    )
                    close()
                }
                drawPath(path = path, color = tint)
            }

            ThemeMode.SYSTEM -> {
                // Half-filled split circle for auto/system
                val r = sizePx * 0.40f
                drawCircle(
                    color = tint,
                    radius = r,
                    style = Stroke(width = strokeWidth),
                )
                drawArc(
                    color = tint,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                )
            }
        }
    }
}

/**
 * Section container with uppercase label and M3 Expressive Segmented Items.
 */
@Composable
private fun SettingsSectionGroup(
    title: String,
    items: List<SettingsCategory>,
    onSelectItem: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
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
            items.forEachIndexed { index, category ->
                SettingsCategorySegmentedRow(
                    category = category,
                    index = index,
                    totalCount = items.size,
                    onClick = { onSelectItem(category) },
                )
            }
        }
    }
}

/**
 * Expressive segmented row with tactile spring press scale and squircle icon badges.
 */
@Composable
private fun SettingsCategorySegmentedRow(
    category: SettingsCategory,
    index: Int,
    totalCount: Int,
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
        label = "categoryRowScale",
    )

    val accentColor = when (category) {
        SettingsCategory.AUDIO_PIPELINE -> MaterialTheme.colorScheme.primary
        SettingsCategory.LYRICS_ENGINE -> MaterialTheme.colorScheme.secondary
        SettingsCategory.APPEARANCE -> MaterialTheme.colorScheme.tertiary
        SettingsCategory.SERVICES -> Color(0xFFE53935)
        SettingsCategory.STORAGE -> MaterialTheme.colorScheme.secondary
        SettingsCategory.ABOUT -> MaterialTheme.colorScheme.primary
    }

    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = totalCount),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        verticalAlignment = Alignment.CenterVertically,
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(category.iconRes),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        content = {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                category.badge?.let { badgeText ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
        },
    )
}

/**
 * Expressive "Coming Soon" placeholder sub-screen for any category.
 */
@Composable
private fun SettingsComingSoonScreen(
    category: SettingsCategory,
    onBack: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val accentColor = when (category) {
        SettingsCategory.AUDIO_PIPELINE -> MaterialTheme.colorScheme.primary
        SettingsCategory.LYRICS_ENGINE -> MaterialTheme.colorScheme.secondary
        SettingsCategory.APPEARANCE -> MaterialTheme.colorScheme.tertiary
        SettingsCategory.SERVICES -> Color(0xFFE53935)
        SettingsCategory.STORAGE -> MaterialTheme.colorScheme.secondary
        SettingsCategory.ABOUT -> MaterialTheme.colorScheme.primary
    }

    ScreenScaffold(
        topBar = {
            SettingsTopAppBar(
                title = category.title,
                onBack = onBack,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + TopBarHeight + 12.dp,
                bottom = bottomInset + 32.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero Icon & Status Pill
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(category.iconRes),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(42.dp),
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "COMING SOON",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }

                    Text(
                        text = category.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            // Planned Capabilities Preview Group
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "PLANNED CAPABILITIES",
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
                        category.plannedFeatures.forEachIndexed { index, feature ->
                            SegmentedListItem(
                                shapes = ListItemDefaults.segmentedShapes(
                                    index = index,
                                    count = category.plannedFeatures.size,
                                ),
                                colors = ListItemDefaults.segmentedColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 12.dp
                                ),
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(accentColor.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_check),
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                },
                                content = {
                                    Text(
                                        text = feature,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // Back Action Button
            item {
                FilledTonalButton(
                    onClick = onBack,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentPadding = ButtonDefaults.LargeContentPadding,
                ) {
                    Text(
                        text = "Back to Settings",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
        }
    }
}

/**
 * Top app bar with rounded elevated surface.
 */
@Composable
private fun SettingsTopAppBar(
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
