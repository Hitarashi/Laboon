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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack

data class LyricLine(
    val startMs: Long,
    val endMs: Long = 0L,
    val text: String,
)

fun parseLrcLines(rawLrc: String): List<LyricLine> {
    if (rawLrc.isBlank()) return emptyList()
    val regex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)""")
    val parsed = mutableListOf<LyricLine>()
    rawLrc.lineSequence().forEach { line ->
        regex.find(line.trim())?.let { match ->
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val msStr = match.groupValues[3].padEnd(3, '0').take(3)
            val ms = msStr.toLongOrNull() ?: 0L
            val startMs = min * 60000L + sec * 1000L + ms
            val text = match.groupValues[4].trim()
            if (text.isNotEmpty()) {
                parsed.add(LyricLine(startMs = startMs, text = text))
            }
        }
    }
    if (parsed.isEmpty()) {
        return rawLrc.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, text ->
                LyricLine(startMs = index * 4000L, endMs = (index + 1) * 4000L, text = text)
            }
            .toList()
    }
    return parsed.mapIndexed { i, item ->
        val nextStart = parsed.getOrNull(i + 1)?.startMs ?: (item.startMs + 6000L)
        item.copy(endMs = nextStart)
    }
}

@Composable
fun LyricsScreen(
    track: HomeTrack,
    currentPositionMs: Long,
    durationMs: Long,
    lyricsLines: List<LyricLine>,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    lyricsFractionProvider: () -> Float = { 1f },
    lazyListState: LazyListState = rememberLazyListState(),
) {
    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        LyricsContentCard(
            track = track,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            lyricsLines = lyricsLines,
            lyricsFractionProvider = lyricsFractionProvider,
            lazyListState = lazyListState,
            onLineClick = { line ->
                if (durationMs > 0L) {
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
    lyricsLines: List<LyricLine>,
    lyricsFractionProvider: () -> Float,
    lazyListState: LazyListState,
    onLineClick: (LyricLine) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeIndex by remember(currentPositionMs, lyricsLines) {
        derivedStateOf {
            if (lyricsLines.isEmpty()) -1
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
                        text = "No Lyrics Available",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enjoy the melody for ${track.title}",
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
                        targetValue = if (isActive) 1.0f else 0.40f,
                        animationSpec = tween(durationMillis = 250),
                        label = "LineAlpha",
                    )

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
                            text = line.text,
                            fontSize = if (isActive) 22.sp else 19.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isActive) primaryTextColor else secondaryTextColor,
                            lineHeight = if (isActive) 30.sp else 26.sp,
                        )
                    }
                }
            }
        }

    }
}
