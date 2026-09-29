@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.navigation.MainTab
import kotlin.math.roundToInt

private val NavigationBarMaxWidth = 420.dp
internal val NavigationBarHeight = 70.dp
private val NavigationBarInnerPadding = 6.dp
internal val NavigationBarBottomPadding = 10.dp
private val NavigationBarClearanceSlack = 8.dp

internal val FloatingNavBarClearance =
    NavigationBarHeight + NavigationBarBottomPadding + NavigationBarClearanceSlack

@Composable
fun FloatingNavBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<MainTab> = MainTab.entries,
    backdropState: LiquidGlassBackdropState? = null,
) {
    val motionScheme = MaterialTheme.motionScheme
    val isDark = isSystemInDarkTheme()

    val pillGradient = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.08f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.70f),
                Color.White.copy(alpha = 0.35f),
            )
        },
    )

    val borderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.50f),
                Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.02f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.05f),
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
            shape = CircleShape,
            cornerRadius = 35.dp,
            shadowElevation = 8.dp,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(NavigationBarInnerPadding),
            ) {
                val tabCount = tabs.size
                val tabWidth = if (tabCount > 0) maxWidth / tabCount else 0.dp
                val targetIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
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
                                width = 1.dp,
                                brush = borderBrush,
                                shape = CircleShape,
                            ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        val selected = tab == selectedTab
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
                                    onClick = { onTabSelected(tab) },
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
                                        id = if (isSelected) tab.iconFilled else tab.iconOutlined,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = contentColor,
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = stringResource(id = tab.titleRes),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = contentColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
