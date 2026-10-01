package org.shilpo.laboon.ui.screens.player.lyrics

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyrics.LyricsLine

@Composable
fun LyricsScreen(
    track: HomeTrack,
    currentPositionMs: Long,
    durationMs: Long,
    lyricsLines: List<LyricsLine>,
    lyricsLoading: Boolean = false,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    lyricsFractionProvider: () -> Float = { 1f },
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val hasTiming = lyricsLines.hasTiming()
    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        LyricsContentCard(
            track = track,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            lyricsLines = lyricsLines,
            lyricsLoading = lyricsLoading,
            lyricsFractionProvider = lyricsFractionProvider,
            lazyListState = lazyListState,
            onLineClick = { line ->
                if (hasTiming && durationMs > 0L) {
                    val frac = (line.startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            },
            onSeek = onSeek,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun LyricsContentCard(
    track: HomeTrack,
    currentPositionMs: Long,
    durationMs: Long,
    lyricsLines: List<LyricsLine>,
    lyricsLoading: Boolean,
    lyricsFractionProvider: () -> Float,
    lazyListState: LazyListState,
    onLineClick: (LyricsLine) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasTiming = remember(lyricsLines) { lyricsLines.hasTiming() }
    val activeIndex by remember(currentPositionMs, lyricsLines) {
        derivedStateOf {
            if (lyricsLines.isEmpty() || !hasTiming) -1
            else {
                val idx = lyricsLines.indexOfLast { it.startMs <= currentPositionMs }
                if (idx >= 0) idx else 0
            }
        }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex in lyricsLines.indices && lyricsFractionProvider() > 0.1f) {
            lazyListState.animateScrollToItem(
                index = (activeIndex - 2).coerceAtLeast(0),
                scrollOffset = 0,
            )
        }
    }

    val fadeHeight = 40.dp
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val karaokeHighlightColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxSize()) {
        if (lyricsLines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_lyrics),
                        contentDescription = null,
                        tint = primaryTextColor.copy(alpha = 0.40f),
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (lyricsLoading) "Searching for lyrics…" else "No Lyrics Available",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (lyricsLoading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        text = if (lyricsLoading) "Lyrics will appear here when found" else "Enjoy the melody for ${track.title}",
                        fontSize = 14.sp,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = 16.dp,
                    start = 24.dp,
                    end = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = if (lyricsFractionProvider() >= 0.99f) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        if (lyricsFractionProvider() <= 0f) return@drawWithContent
                        val fadePx = fadeHeight.toPx()
                        if (size.height > 0f && fadePx > 0f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black),
                                    startY = 0f,
                                    endY = fadePx,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startY = size.height - fadePx,
                                    endY = size.height,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    },
            ) {
                itemsIndexed(
                    items = lyricsLines,
                    key = { idx, line -> "${line.startMs}_$idx" },
                ) { index, line ->
                    val isActive = index == activeIndex
                    val lineScale by animateFloatAsState(
                        targetValue = if (isActive) 1.05f else 1.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "LineScale",
                    )
                    val lineAlpha by animateFloatAsState(
                        targetValue = if (isActive || !hasTiming) 1.0f else 0.52f,
                        animationSpec = tween(durationMillis = 250),
                        label = "LineAlpha",
                    )
                    val karaokeText = remember(
                        line,
                        currentPositionMs,
                        isActive,
                        primaryTextColor,
                        secondaryTextColor,
                        karaokeHighlightColor,
                    ) {
                        line.toKaraokeText(
                            currentPositionMs = currentPositionMs,
                            isActive = isActive,
                            completedColor = primaryTextColor,
                            highlightColor = karaokeHighlightColor,
                            upcomingColor = secondaryTextColor,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = lineScale
                                scaleY = lineScale
                                alpha = lineAlpha
                            }
                            .clickable { onLineClick(line) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = karaokeText,
                            textAlign = TextAlign.Center,
                            fontSize = if (isActive) 22.sp else 19.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isActive) primaryTextColor else secondaryTextColor,
                            lineHeight = if (isActive) 30.sp else 26.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

    }
}

private fun LyricsLine.toKaraokeText(
    currentPositionMs: Long,
    isActive: Boolean,
    completedColor: Color,
    highlightColor: Color,
    upcomingColor: Color,
): AnnotatedString {
    if (!isActive || words.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)

        var searchFrom = 0
        words.forEachIndexed { index, word ->
            val wordStart = text.indexOf(word.text, startIndex = searchFrom)
            if (wordStart < 0) return@forEachIndexed

            val wordEnd = wordStart + word.text.length
            val endMs = word.endMs
                ?.takeIf { it > word.startMs }
                ?: words.getOrNull(index + 1)?.startMs?.takeIf { it > word.startMs }
                ?: this@toKaraokeText.endMs.takeIf { it > word.startMs }
                ?: (word.startMs + 600L)
            val wordStyle = when {
                currentPositionMs >= endMs -> SpanStyle(
                    color = completedColor,
                    fontWeight = FontWeight.Bold,
                )

                currentPositionMs >= word.startMs -> {
                    val durationMs = (endMs - word.startMs).coerceAtLeast(1L)
                    val progress =
                        ((currentPositionMs - word.startMs).toFloat() / durationMs).coerceIn(0f, 1f)
                    SpanStyle(
                        color = highlightColor,
                        background = highlightColor.copy(alpha = 0.10f + (0.14f * progress)),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                else -> SpanStyle(
                    color = upcomingColor.copy(alpha = 0.45f),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            addStyle(wordStyle, wordStart, wordEnd)
            searchFrom = wordEnd
        }
    }
}

private fun List<LyricsLine>.hasTiming(): Boolean =
    any { line -> line.startMs > 0L || line.words.any { word -> word.startMs > 0L } }
