@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.navigation.MainTab
import org.shilpo.laboon.theme.LocalVisualTheme
import kotlin.math.roundToInt

internal val NavigationBarMaxWidth = 420.dp
internal val NavigationBarHeight = 80.dp
internal val PixelNavigationBarHeight = 90.dp
internal val PixelPlayerOuterCornerRadius = 32.dp
internal val PixelPlayerFacingCornerRadius = 10.dp
private val NavigationBarInnerPadding = 6.dp
internal val NavigationBarBottomPadding = 10.dp
private val NavigationBarClearanceSlack = 8.dp

internal val MiniPlayerHeight = 72.dp
internal val PixelMiniPlayerHeight = 64.dp
internal val MiniPlayerSpacing = 8.dp
internal val MiniPlayerBottomPadding =
    PixelNavigationBarHeight + MiniPlayerSpacing

internal val FloatingNavBarBaseClearance =
    NavigationBarHeight + NavigationBarBottomPadding + NavigationBarClearanceSlack

internal val FloatingCombinedClearance =
    FloatingNavBarBaseClearance + MiniPlayerHeight + MiniPlayerSpacing

internal val FloatingNavBarClearance = FloatingCombinedClearance

internal data class FloatingNavigationItem<T>(
    val value: T,
    val title: String,
    val iconOutlined: Int,
    val iconFilled: Int,
)

@Composable
fun FloatingNavBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    hasMiniPlayerAbove: Boolean = false,
    tabs: List<MainTab> = MainTab.entries,
    backdropState: LiquidGlassBackdropState? = null,
) {
    FloatingNavigationBar(
        items = tabs.map { tab ->
            FloatingNavigationItem(
                value = tab,
                title = stringResource(id = tab.titleRes),
                iconOutlined = tab.iconOutlined,
                iconFilled = tab.iconFilled,
            )
        },
        selectedItem = selectedTab,
        onItemSelected = onTabSelected,
        modifier = modifier,
        hasMiniPlayerAbove = hasMiniPlayerAbove,
        backdropState = backdropState,
    )
}

@Composable
internal fun <T> FloatingNavigationBar(
    items: List<FloatingNavigationItem<T>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    hasMiniPlayerAbove: Boolean = false,
    connectedBelow: Boolean = false,
    connectedBelowFraction: Float = if (connectedBelow) 1f else 0f,
    iconAlpha: Float = 1f,
    labelAlpha: Float = 1f,
    backdropState: LiquidGlassBackdropState? = null,
) {
    if (LocalVisualTheme.current == null) {
        Column(
            modifier = modifier
                .offset(y = NavigationBarBottomPadding)
                .widthIn(max = NavigationBarMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .height(PixelNavigationBarHeight + MiniPlayerSpacing),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerSpacing),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PixelNavigationBarHeight),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(
                        topStart = PixelPlayerFacingCornerRadius,
                        topEnd = PixelPlayerFacingCornerRadius,
                        bottomStart = PixelPlayerOuterCornerRadius,
                        bottomEnd = PixelPlayerOuterCornerRadius,
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                ) {}
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { item ->
                        val selected = item.value == selectedItem
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.1f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "navigationIconScale",
                        )
                        val iconColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 150),
                            label = "pixelNavIconColor",
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 150),
                            label = "pixelNavTextColor",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember(item.value) { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                    onClickLabel = item.title,
                                    onClick = { onItemSelected(item.value) },
                                )
                                .semantics {
                                    contentDescription = item.title
                                    this.selected = selected
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier.size(width = 64.dp, height = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = selected,
                                    enter = androidx.compose.animation.fadeIn(tween(durationMillis = 100)) +
                                        scaleIn(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow,
                                            ),
                                        ),
                                    exit = fadeOut(tween(durationMillis = 100)) +
                                        scaleOut(animationSpec = tween(durationMillis = 100)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 4.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(16.dp),
                                            ),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(width = 48.dp, height = 24.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (selected) item.iconFilled else item.iconOutlined,
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .alpha(iconAlpha.coerceIn(0f, 1f)),
                                        tint = iconColor,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                ),
                                maxLines = 1,
                                modifier = Modifier.alpha(labelAlpha.coerceIn(0f, 1f)),
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val motionScheme = MaterialTheme.motionScheme
    val isDark = isSystemInDarkTheme()

    val topRadius by animateDpAsState(
        targetValue = if (hasMiniPlayerAbove) 12.dp else 35.dp,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "navTopCornerRadius",
    )
    val bottomRadius = lerp(
        start = 35.dp,
        stop = 12.dp,
        fraction = connectedBelowFraction.coerceIn(0f, 1f),
    )
    val navShape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )

    val pillGradient = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.12f),
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.50f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.75f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f),
            )
        },
    )

    val borderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.10f),
                Color.Transparent,
            )
        },
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .widthIn(max = NavigationBarMaxWidth)
                .fillMaxWidth()
                .height(NavigationBarHeight),
            backdropState = backdropState,
            shape = navShape,
            cornerRadius = 35.dp,
            topRadius = topRadius,
            bottomRadius = bottomRadius,
            shadowElevation = 8.dp,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(NavigationBarInnerPadding),
            ) {
                val tabCount = items.size
                val tabWidth = if (tabCount > 0) maxWidth / tabCount else 0.dp
                val targetIndex = items.indexOfFirst { it.value == selectedItem }.coerceAtLeast(0)
                val indicatorIndex by animateFloatAsState(
                    targetValue = targetIndex.toFloat(),
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "navIndicatorSlide",
                )

                if (tabCount > 0) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (indicatorIndex * tabWidth.toPx()).roundToInt(),
                                    y = 0,
                                )
                            }
                            .width(tabWidth)
                            .fillMaxHeight()
                            .shadow(
                                elevation = 2.dp,
                                shape = CircleShape,
                                clip = false,
                            )
                            .background(
                                brush = pillGradient,
                                shape = CircleShape,
                            )
                            .border(
                                width = 0.5.dp,
                                brush = borderBrush,
                                shape = CircleShape,
                            ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { item ->
                        val selected = item.value == selectedItem
                        val contentColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            animationSpec = motionScheme.defaultEffectsSpec(),
                            label = "navTabContentColor",
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onItemSelected(item.value) },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Crossfade(
                                targetState = selected,
                                animationSpec = motionScheme.fastEffectsSpec(),
                                label = "navTabIconCrossfade",
                            ) { isSelected ->
                                Icon(
                                    painter = painterResource(
                                        id = if (isSelected) item.iconFilled else item.iconOutlined,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .alpha(iconAlpha.coerceIn(0f, 1f)),
                                    tint = contentColor,
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = contentColor,
                                maxLines = 1,
                                modifier = Modifier.alpha(labelAlpha.coerceIn(0f, 1f)),
                            )
                        }
                    }
                }
            }
        }
    }
}
