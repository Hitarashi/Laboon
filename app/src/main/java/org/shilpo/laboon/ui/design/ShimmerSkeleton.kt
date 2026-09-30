@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package org.shilpo.laboon.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R


@Composable
fun frostedGlassBaseBrush(isDark: Boolean = isSystemInDarkTheme()): Brush {
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
    return remember(isDark, surfaceContainerHigh, surfaceContainerLow) {
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f),
                    surfaceContainerHigh.copy(alpha = 0.45f),
                ),
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.70f),
                    surfaceContainerLow.copy(alpha = 0.40f),
                ),
            )
        }
    }
}


@Composable
fun frostedGlassBorderBrush(isDark: Boolean = isSystemInDarkTheme()): Brush {
    return remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent,
                ),
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.15f),
                    Color.Transparent,
                ),
            )
        }
    }
}


@Composable
fun frostedGlassGlintColors(isDark: Boolean = isSystemInDarkTheme()): List<Color> {
    return remember(isDark) {
        if (isDark) {
            listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
            )
        } else {
            listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.80f),
                Color.White.copy(alpha = 0.30f),
                Color.Transparent,
            )
        }
    }
}


@Composable
fun Modifier.frostedGlassShimmer(
    drawBase: Boolean = true,
): Modifier {
    val isDark = isSystemInDarkTheme()
    val transition = rememberInfiniteTransition(label = "frostedGlassShimmerTransition")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "frostedGlassShimmerProgress",
    )

    val baseBrush = frostedGlassBaseBrush(isDark)
    val glintColors = frostedGlassGlintColors(isDark)

    return this.drawWithCache {
        val w = size.width
        val h = size.height
        val bandWidth = maxOf(w * 0.85f, 260f)
        val totalDistance = w + bandWidth * 2f
        val startX = -bandWidth + progress * totalDistance
        val endX = startX + bandWidth

        val glintBrush = Brush.linearGradient(
            colors = glintColors,
            start = Offset(startX, 0f),
            end = Offset(endX, h),
        )

        onDrawWithContent {
            if (drawBase) {
                drawRect(brush = baseBrush)
            }
            drawRect(brush = glintBrush)
            drawContent()
        }
    }
}


@Composable
fun Modifier.shimmer(): Modifier = frostedGlassShimmer()


@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 0.75.dp,
    content: @Composable (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val borderBrush = frostedGlassBorderBrush(isDark)

    Box(
        modifier = modifier
            .clip(shape)
            .frostedGlassShimmer()
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(width = borderWidth, brush = borderBrush, shape = shape)
                } else Modifier
            ),
    ) {
        content?.invoke()
    }
}


@Composable
fun SkeletonTextLine(
    width: Dp,
    height: Dp = 16.dp,
    shape: Shape = CircleShape,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val borderBrush = frostedGlassBorderBrush(isDark)

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(shape)
            .frostedGlassShimmer()
            .border(width = 0.5.dp, brush = borderBrush, shape = shape),
    )
}


@Composable
fun SkeletonTrackCard(
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val shape = MaterialTheme.shapes.extraLarge
    val borderBrush = frostedGlassBorderBrush(isDark)

    Box(
        modifier = modifier
            .size(width = 186.dp, height = 206.dp)
            .clip(shape)
            .frostedGlassShimmer()
            .border(width = 0.75.dp, brush = borderBrush, shape = shape),
    ) {
        Icon(
            painter = painterResource(R.drawable.app_icon_small),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.20f else 0.28f),
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.Center)
                .offset(y = (-18).dp),
        )


        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.72f to Color.Black.copy(alpha = if (isDark) 0.50f else 0.25f),
                        1.0f to Color.Black.copy(alpha = if (isDark) 0.85f else 0.45f),
                    )
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SkeletonTextLine(
                    width = 96.dp,
                    height = 14.dp,
                    shape = CircleShape,
                )
                SkeletonTextLine(
                    width = 64.dp,
                    height = 11.dp,
                    shape = CircleShape,
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .frostedGlassShimmer()
                    .border(width = 0.75.dp, brush = borderBrush, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.35f else 0.45f),
                    modifier = Modifier
                        .size(17.dp)
                        .offset(x = 1.dp),
                )
            }
        }
    }
}


@Composable
fun SkeletonArtistItem(
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val borderBrush = frostedGlassBorderBrush(isDark)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.width(112.dp),
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .frostedGlassShimmer()
                .border(width = 0.75.dp, brush = borderBrush, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_user_headshot),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.25f else 0.35f),
                modifier = Modifier.size(48.dp),
            )
        }
        SkeletonTextLine(
            width = 72.dp,
            height = 12.dp,
            shape = CircleShape,
        )
    }
}


@Composable
fun SkeletonTrackCarousel(
    titleWidth: Dp = 140.dp,
    subtitleWidth: Dp = 200.dp,
    isArtist: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonTextLine(width = titleWidth, height = 22.dp, shape = CircleShape)
            SkeletonTextLine(
                width = subtitleWidth,
                height = 14.dp,
                shape = CircleShape,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), enabled = false)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isArtist) 14.dp else 10.dp),
        ) {
            val count = if (isArtist) 5 else 4
            repeat(count) {
                if (isArtist) {
                    SkeletonArtistItem()
                } else {
                    SkeletonTrackCard()
                }
            }
        }
    }
}


@Composable
fun SkeletonSegmentedList(
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val borderBrush = frostedGlassBorderBrush(isDark)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonTextLine(width = 110.dp, height = 20.dp, shape = CircleShape)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val leadingShape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape
            val trailingShape = ButtonGroupDefaults.connectedTrailingButtonShapes().shape

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(leadingShape)
                    .frostedGlassShimmer()
                    .border(width = 0.75.dp, brush = borderBrush, shape = leadingShape),
                contentAlignment = Alignment.Center,
            ) {
                SkeletonTextLine(width = 68.dp, height = 12.dp, shape = CircleShape)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(trailingShape)
                    .frostedGlassShimmer()
                    .border(width = 0.75.dp, brush = borderBrush, shape = trailingShape),
                contentAlignment = Alignment.Center,
            ) {
                SkeletonTextLine(width = 68.dp, height = 12.dp, shape = CircleShape)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            val itemCount = 4
            repeat(itemCount) { index ->
                val itemShapes = ListItemDefaults.segmentedShapes(
                    index = index,
                    count = itemCount,
                )
                SegmentedListItem(
                    shapes = itemShapes,
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = if (isDark) {
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
                        },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 0.5.dp, brush = borderBrush, shape = itemShapes.shape),
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .frostedGlassShimmer()
                                .border(
                                    width = 0.75.dp,
                                    brush = borderBrush,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.app_icon_small),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (isDark) 0.25f else 0.35f
                                ),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    content = {
                        SkeletonTextLine(
                            width = 140.dp,
                            height = 15.dp,
                            shape = CircleShape,
                        )
                    },
                    supportingContent = {
                        SkeletonTextLine(
                            width = 90.dp,
                            height = 12.dp,
                            shape = CircleShape,
                        )
                    },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .frostedGlassShimmer()
                                .border(
                                    width = 0.75.dp,
                                    brush = borderBrush,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (isDark) 0.30f else 0.40f
                                ),
                                modifier = Modifier
                                    .size(16.dp)
                                    .offset(x = 1.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}
