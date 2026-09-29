@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package org.shilpo.laboon.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainer,
    )
    return this.drawWithCache {
        val w = size.width
        val h = size.height
        val bandWidth = maxOf(w, 300f)
        val startX = -bandWidth + progress * (w + bandWidth * 2f)
        val endX = startX + bandWidth
        val brush = Brush.linearGradient(
            colors = colors,
            start = Offset(startX, 0f),
            end = Offset(endX, h),
        )
        onDrawWithContent {
            drawRect(brush = brush)
            drawContent()
        }
    }
}

@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmer(),
    )
}

@Composable
fun SkeletonTextLine(
    width: Dp,
    height: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(4.dp),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(shape)
            .shimmer(),
    )
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
            SkeletonTextLine(width = titleWidth, height = 22.dp, shape = RoundedCornerShape(6.dp))
            SkeletonTextLine(
                width = subtitleWidth,
                height = 14.dp,
                shape = RoundedCornerShape(4.dp)
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkeletonCard(
                            modifier = Modifier.size(112.dp),
                            shape = CircleShape,
                        )
                        SkeletonTextLine(
                            width = 72.dp,
                            height = 12.dp,
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                } else {
                    SkeletonCard(
                        modifier = Modifier.size(width = 186.dp, height = 206.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }
            }
        }
    }
}

@Composable
fun SkeletonSegmentedList(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonTextLine(width = 110.dp, height = 20.dp, shape = RoundedCornerShape(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            SkeletonCard(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape,
            )
            SkeletonCard(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = ButtonGroupDefaults.connectedTrailingButtonShapes().shape,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            val itemCount = 4
            repeat(itemCount) { index ->
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = itemCount,
                    ),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = {
                        SkeletonCard(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        )
                    },
                    content = {
                        SkeletonTextLine(
                            width = 140.dp,
                            height = 16.dp,
                            shape = RoundedCornerShape(4.dp),
                        )
                    },
                    supportingContent = {
                        SkeletonTextLine(
                            width = 90.dp,
                            height = 12.dp,
                            shape = RoundedCornerShape(4.dp),
                        )
                    },
                    trailingContent = {
                        SkeletonCard(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun HomeLoadingSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        SkeletonTrackCarousel(
            titleWidth = 140.dp,
            subtitleWidth = 200.dp,
            isArtist = false,
        )
        SkeletonTrackCarousel(
            titleWidth = 150.dp,
            subtitleWidth = 220.dp,
            isArtist = false,
        )
        SkeletonTrackCarousel(
            titleWidth = 120.dp,
            subtitleWidth = 180.dp,
            isArtist = true,
        )
        SkeletonSegmentedList()
        SkeletonTrackCarousel(
            titleWidth = 130.dp,
            subtitleWidth = 190.dp,
            isArtist = false,
        )
    }
}
