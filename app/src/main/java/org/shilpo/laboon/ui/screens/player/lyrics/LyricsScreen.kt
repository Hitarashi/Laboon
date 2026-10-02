package org.shilpo.laboon.ui.screens.player.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyrics.LyricsLine
import org.shilpo.laboon.lyrics.LyricsTranslator
import org.shilpo.laboon.ui.design.theme.GoogleSansFlex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val LRC_LEAD_MS = 300L
private const val TTML_LEAD_MS = 0L
private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L
private const val MANUAL_SCROLL_DEBOUNCE_MS = 50L
private const val LYRIC_FOCUS_ANCHOR_RATIO = 0.50f
private const val LYRIC_LINE_SYNC_TOP_ANCHOR_RATIO = 0.50f
private const val LYRIC_FOCUS_MIN_SCROLL_PX = 6
private const val LYRIC_FOCUS_ANIMATED_DISTANCE = 12
private const val SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS = 80L
private const val SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS = 180L
private const val SMOOTH_PLAYBACK_DRIFT_CORRECTION = 0.55f
private const val LYRIC_FOCUS_SCROLL_DURATION_MS = 520
private const val LYRICS_FONT_FEATURE_SETTINGS = "'liga' 0, 'clig' 0"

private val SmoothDecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
private val NoSpaceAfterChars: Set<Char> = setOf('(', '[', '{', '«', '‹', '“', '‘')

private fun isChinese(text: String): Boolean = text.any { it.code in 0x4E00..0x9FFF }
private fun isJapanese(text: String): Boolean =
    text.any { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF }

private fun isKorean(text: String): Boolean =
    text.any { it.code in 0xAC00..0xD7AF || it.code in 0x1100..0x11FF }

private fun isRtlText(text: String): Boolean {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return true

            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return false
        }
    }
    return false
}

private fun shouldAppendWordSpace(current: String, next: String): Boolean {
    if (current.isEmpty() || next.isEmpty()) return false
    val last = current.last()
    val first = next.first()
    if (last.isWhitespace() || first.isWhitespace()) return false
    if (!first.isLetterOrDigit()) return false
    return last !in NoSpaceAfterChars
}

fun Modifier.smoothFadingEdge(vertical: Dp = 52.dp): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        val verticalPx = vertical.toPx()
        if (size.height > 0f && verticalPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.25f),
                        0.7f to Color.Black.copy(alpha = 0.75f),
                        1.0f to Color.Black,
                    ),
                    startY = 0f,
                    endY = verticalPx,
                ),
                blendMode = BlendMode.DstIn,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Black,
                        0.3f to Color.Black.copy(alpha = 0.75f),
                        0.65f to Color.Black.copy(alpha = 0.25f),
                        1.0f to Color.Transparent,
                    ),
                    startY = size.height - verticalPx,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private suspend fun LazyListState.scrollLyricIntoFocus(
    index: Int,
    animateToNearbyItem: Boolean,
    force: Boolean = false,
    alignByItemCenter: Boolean = true,
    isSeek: Boolean = false,
) {
    val itemCount = layoutInfo.totalItemsCount
    if (itemCount == 0) return

    val targetIndex = index.coerceIn(0, itemCount - 1)
    var itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
    if (itemInfo == null) {
        val distance = abs(targetIndex - firstVisibleItemIndex)
        if (animateToNearbyItem && distance <= LYRIC_FOCUS_ANIMATED_DISTANCE) {
            animateScrollToItem(targetIndex)
        } else {
            val nearbyIndex = if (targetIndex > firstVisibleItemIndex) {
                targetIndex - LYRIC_FOCUS_ANIMATED_DISTANCE
            } else {
                targetIndex + LYRIC_FOCUS_ANIMATED_DISTANCE
            }.coerceIn(0, itemCount - 1)
            scrollToItem(nearbyIndex)
        }
        withFrameNanos { }
        itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
    }

    itemInfo ?: return

    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val viewportHeight = viewportEnd - viewportStart
    if (viewportHeight <= 0) return

    // Center alignment for all synced lines
    val itemFocusPoint = itemInfo.offset + itemInfo.size / 2
    val targetFocusPoint = viewportStart + (viewportHeight * 0.50f).roundToInt()
    val scrollDelta = itemFocusPoint - targetFocusPoint
    if (abs(scrollDelta) > LYRIC_FOCUS_MIN_SCROLL_PX) {
        if (isSeek) {
            animateScrollBy(
                value = scrollDelta.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        } else {
            animateScrollBy(
                value = scrollDelta.toFloat(),
                animationSpec = tween(
                    durationMillis = LYRIC_FOCUS_SCROLL_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
}

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
    isPlaying: Boolean = true,
) {
    val coroutineScope = rememberCoroutineScope()
    val hasTiming = remember(lyricsLines) { lyricsLines.hasTiming() }
    val hasWordTimings = remember(lyricsLines) { lyricsLines.hasWordTimings() }
    val view = LocalView.current

    DisposableEffect(view) {
        val wasKeepingScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = wasKeepingScreenOn }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        LyricsContentCard(
            track = track,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            lyricsLines = lyricsLines,
            lyricsLoading = lyricsLoading,
            lyricsFractionProvider = lyricsFractionProvider,
            lazyListState = lazyListState,
            isPlaying = isPlaying,
            onLineClick = { line, index ->
                if (hasTiming && durationMs > 0L) {
                    val frac = (line.startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    onSeek(frac)
                    coroutineScope.launch {
                        lazyListState.scrollLyricIntoFocus(
                            index = index,
                            animateToNearbyItem = true,
                            force = true,
                            alignByItemCenter = hasWordTimings,
                            isSeek = true,
                        )
                    }
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
    onLineClick: (LyricsLine, Int) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
) {
    val coroutineScope = rememberCoroutineScope()
    val hasTiming = remember(lyricsLines) { lyricsLines.hasTiming() }
    val hasWordTimings = remember(lyricsLines) { lyricsLines.hasWordTimings() }
    val leadMs = if (hasWordTimings) TTML_LEAD_MS else LRC_LEAD_MS

    val latestPosition = rememberUpdatedState(currentPositionMs)
    val latestIsPlaying = rememberUpdatedState(isPlaying)
    val latestFraction = rememberUpdatedState(lyricsFractionProvider)

    val playbackPositionMs = remember {
        mutableLongStateOf(currentPositionMs.coerceAtLeast(0L))
    }
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(track, lyricsLines) {
        playbackPositionMs.longValue = currentPositionMs.coerceAtLeast(0L)
        isManualScrolling = false
        lastManualScrollTime = 0L
    }

    LaunchedEffect(track) {
        var anchorPlayerPositionMs = latestPosition.value.coerceAtLeast(0L)
        var anchorFrameNanos = 0L
        while (isActive) {
            val fraction = latestFraction.value()
            if (fraction <= 0.05f) {
                anchorPlayerPositionMs = latestPosition.value.coerceAtLeast(0L)
                anchorFrameNanos = 0L
                delay(200L)
                continue
            }

            val rawPosition = latestPosition.value.coerceAtLeast(0L)
            val playing = latestIsPlaying.value

            if (!playing) {
                anchorPlayerPositionMs = rawPosition
                anchorFrameNanos = 0L
                if (playbackPositionMs.longValue != rawPosition) {
                    playbackPositionMs.longValue = rawPosition
                }
                delay(100L)
            } else {
                val frameNanos = withFrameNanos { it }
                if (anchorFrameNanos == 0L) {
                    anchorFrameNanos = frameNanos
                    anchorPlayerPositionMs = rawPosition
                }

                val elapsedMs = (frameNanos - anchorFrameNanos) / 1_000_000f
                val projectedPosition = anchorPlayerPositionMs + elapsedMs.roundToLong()
                val driftMs = rawPosition - projectedPosition

                val nextPosition = when {
                    driftMs > SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS || driftMs < -SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS -> {
                        anchorPlayerPositionMs = rawPosition
                        anchorFrameNanos = frameNanos
                        rawPosition
                    }

                    driftMs != 0L -> {
                        projectedPosition + (driftMs * SMOOTH_PLAYBACK_DRIFT_CORRECTION).roundToLong()
                    }

                    else -> {
                        projectedPosition
                    }
                }.coerceAtLeast(0L)

                if (playbackPositionMs.longValue != nextPosition) {
                    playbackPositionMs.longValue = nextPosition
                }
            }
        }
    }

    var lyricsSyncOffsetMs by remember { mutableLongStateOf(0L) }
    var isNudgeExpanded by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showRomanization by remember { mutableStateOf(true) }
    var showTranslation by remember { mutableStateOf(false) }
    var translatedLines by remember(lyricsLines) { mutableStateOf<List<LyricsLine>?>(null) }
    var isTranslating by remember { mutableStateOf(false) }

    val displayLyricsLines = translatedLines ?: lyricsLines

    val singerLaneMap = remember(displayLyricsLines, track.artist) {
        val allSingers = displayLyricsLines
            .mapNotNull { cleanSingerName(it.singer).takeIf { s -> s.isNotBlank() } }
            .distinct()

        val soloSingers = allSingers.filter { !isDuetOrGroup(it) }
        val map = mutableMapOf<String, LyricSingerLane>()

        // 1. Duets / groups are always Center
        for (singer in allSingers) {
            if (isDuetOrGroup(singer)) {
                map[singer.lowercase()] = LyricSingerLane.Center
            }
        }
        map["duet"] = LyricSingerLane.Center
        map["chorus"] = LyricSingerLane.Center
        map["all"] = LyricSingerLane.Center
        map["both"] = LyricSingerLane.Center
        map["v3"] = LyricSingerLane.Center

        // 2. Solo singers:
        if (soloSingers.isNotEmpty()) {
            val counts = displayLyricsLines
                .groupBy { cleanSingerName(it.singer).lowercase() }
                .mapValues { it.value.size }

            val cleanTrackArtist = track.artist.lowercase()
            val primaryTrackArtist = track.artist
                .split(
                    Regex(
                        """(?i)\s*,\s*|\s*/\s*|\s*&\s*|\s+feat\.?\s+|\s+ft\.?\s+|\s+with\s+|\s+and\s+|\s*、\s*|\s*;\s*"""
                    )
                )
                .first()
                .trim()
                .lowercase()

            // Identify lead singer: matches primary artist or has highest line count
            val leadSinger = soloSingers.firstOrNull { singer ->
                val sLower = singer.lowercase()
                primaryTrackArtist.isNotEmpty() && (
                        sLower == primaryTrackArtist ||
                                primaryTrackArtist.contains(sLower) ||
                                sLower.contains(primaryTrackArtist)
                        )
            } ?: soloSingers.maxByOrNull { counts[it.lowercase()] ?: 0 } ?: soloSingers.first()

            val remainingSolo = soloSingers.filter { it.lowercase() != leadSinger.lowercase() }

            // Identify secondary / featured singer:
            // Prefer the remaining singer that matches track artist, or has "sean"/"paul", or has highest line count
            val secondarySinger = remainingSolo.firstOrNull { singer ->
                val sLower = singer.lowercase()
                cleanTrackArtist.contains(sLower) || sLower.contains("sean") || sLower.contains("paul")
            } ?: remainingSolo.maxByOrNull { counts[it.lowercase()] ?: 0 }

            // Map Lead Singer -> Left
            map[leadSinger.lowercase()] = LyricSingerLane.Left
            for (singer in soloSingers) {
                val sLower = singer.lowercase()
                if (sLower.contains(leadSinger.lowercase()) || leadSinger.lowercase()
                        .contains(sLower) || sLower.contains("sia")
                ) {
                    map[sLower] = LyricSingerLane.Left
                }
            }

            // Map Secondary Singer -> Right
            if (secondarySinger != null) {
                val secLower = secondarySinger.lowercase()
                map[secLower] = LyricSingerLane.Right
                for (singer in soloSingers) {
                    val sLower = singer.lowercase()
                    if (!sLower.contains(leadSinger.lowercase()) && !leadSinger.lowercase()
                            .contains(sLower) && !sLower.contains("sia")
                    ) {
                        if (sLower.contains(secLower) || secLower.contains(sLower) ||
                            (secLower.contains("sean") && sLower.contains("henriques")) ||
                            (sLower.contains("sean") && secLower.contains("henriques")) ||
                            sLower.contains("sean") || sLower.contains("paul") || sLower.contains("henriques")
                        ) {
                            map[sLower] = LyricSingerLane.Right
                        }
                    }
                }
            }

            // For any other solo singers not yet mapped, map to Right (never Center)
            for (singer in soloSingers) {
                val sLower = singer.lowercase()
                if (!map.containsKey(sLower)) {
                    map[sLower] = LyricSingerLane.Right
                }
            }
        }

        // Map known agents (e.g. v1, v2) based on the lines
        for (line in displayLyricsLines) {
            val s = cleanSingerName(line.singer).takeIf { it.isNotBlank() }?.lowercase()
            val a = line.agent?.trim()?.lowercase()
            if (s != null && a != null && map.containsKey(s) && !map.containsKey(a)) {
                map[a] = map.getValue(s)
            }
        }

        map
    }

    val currentTimeProvider: () -> Long = remember(leadMs) {
        {
            (
                    playbackPositionMs.longValue +
                            leadMs +
                            LYRIC_VISUAL_TUNING_OFFSET_MS +
                            lyricsSyncOffsetMs
                    ).coerceAtLeast(0L)
        }
    }

    val activeIndex by remember(displayLyricsLines, hasTiming) {
        derivedStateOf {
            if (displayLyricsLines.isEmpty() || !hasTiming) -1
            else {
                val focusTime = currentTimeProvider()
                val idx = displayLyricsLines.indexOfLast { it.startMs <= focusTime }
                if (idx >= 0) idx else 0
            }
        }
    }
    val nestedScrollConnection = remember {
        var lastUserScrollEventMs = 0L
        object : NestedScrollConnection {
            private fun markManualScroll() {
                val now = System.currentTimeMillis()
                if (now - lastUserScrollEventMs >= MANUAL_SCROLL_DEBOUNCE_MS) {
                    isManualScrolling = true
                    lastManualScrollTime = now
                    lastUserScrollEventMs = now
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    markManualScroll()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (isManualScrolling) {
                    lastManualScrollTime = System.currentTimeMillis()
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            while (lazyListState.isScrollInProgress) {
                delay(MANUAL_SCROLL_DEBOUNCE_MS)
            }
            isManualScrolling = false
        }
    }

    LaunchedEffect(activeIndex, isManualScrolling, isPlaying) {
        if (!isPlaying || isManualScrolling || activeIndex !in lyricsLines.indices || lyricsFractionProvider() <= 0.05f) {
            return@LaunchedEffect
        }
        while (lazyListState.isScrollInProgress) {
            delay(MANUAL_SCROLL_DEBOUNCE_MS)
        }
        if (isPlaying && !isManualScrolling) {
            lazyListState.scrollLyricIntoFocus(
                index = activeIndex,
                animateToNearbyItem = true,
                force = false,
                alignByItemCenter = hasWordTimings,
                isSeek = false,
            )
        }
    }

    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lyricsTextStyle = LocalTextStyle.current.copy(
        fontFamily = GoogleSansFlex,
        fontFeatureSettings = LYRICS_FONT_FEATURE_SETTINGS,
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sheetMaxHeight = maxHeight

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
                            strokeWidth = 2.dp,
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
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        top = sheetMaxHeight * 0.5f - 24.dp,
                        bottom = sheetMaxHeight * 0.5f - 24.dp,
                        start = 24.dp,
                        end = 24.dp,
                    ),
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier
                        .fillMaxSize()
                        .smoothFadingEdge(vertical = 52.dp)
                        .nestedScroll(nestedScrollConnection),
                ) {
                    itemsIndexed(
                        items = displayLyricsLines,
                        key = { idx, line -> "${line.startMs}_$idx" },
                    ) { index, line ->
                        val isActive = index == activeIndex
                        val distance = if (activeIndex >= 0) abs(index - activeIndex) else 0

                        val targetAlpha = when {
                            !hasTiming -> 1.0f
                            distance == 0 -> 1.0f
                            isManualScrolling -> when (distance) {
                                1 -> 0.75f
                                2 -> 0.55f
                                3 -> 0.40f
                                else -> 0.28f
                            }

                            else -> when (distance) {
                                1 -> 0.68f
                                2 -> 0.44f
                                else -> 0.22f
                            }
                        }

                        val animatedAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = SmoothDecelerateEasing,
                            ),
                            label = "lineAlpha",
                        )

                        val targetBlur = when {
                            !hasTiming || distance <= 1 || isManualScrolling -> 0f
                            distance == 2 -> 0.6f
                            else -> 1.8f
                        }

                        val animatedBlur by animateFloatAsState(
                            targetValue = targetBlur,
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing,
                            ),
                            label = "lineBlur",
                        )

                        val lineScale = remember { Animatable(1.0f) }
                        LaunchedEffect(isActive) {
                            if (isActive) {
                                lineScale.snapTo(0.96f)
                                lineScale.animateTo(
                                    targetValue = 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                )
                            } else {
                                lineScale.snapTo(1.0f)
                            }
                        }

                        val lineTransformOrigin = remember(line.agent, line.singer, singerLaneMap) {
                            when (resolveLane(line.singer, line.agent, singerLaneMap)) {
                                LyricSingerLane.Left -> TransformOrigin(0f, 0.5f)
                                LyricSingerLane.Right -> TransformOrigin(1f, 0.5f)
                                LyricSingerLane.Center -> TransformOrigin(0.5f, 0.5f)
                            }
                        }

                        val lineModifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = lineScale.value
                                scaleY = lineScale.value
                                alpha = animatedAlpha
                                transformOrigin = lineTransformOrigin
                            }
                            .then(
                                if (animatedBlur > 0.05f) {
                                    Modifier.blur(
                                        radiusX = animatedBlur.dp,
                                        radiusY = animatedBlur.dp,
                                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .then(
                                if (!line.isInstrumental) {
                                    Modifier
                                        .clickable {
                                            isManualScrolling = false
                                            onLineClick(line, index)
                                        }
                                        .padding(vertical = 8.dp)
                                } else {
                                    Modifier.padding(vertical = 0.dp)
                                }
                            )

                        CompositionLocalProvider(LocalTextStyle provides lyricsTextStyle) {
                            LyricsLineItem(
                                line = line,
                                isActive = isActive,
                                index = index,
                                nextLineStartMs = displayLyricsLines.getOrNull(index + 1)?.startMs,
                                currentTimeProvider = currentTimeProvider,
                                textColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                showRomanization = showRomanization,
                                showTranslation = showTranslation,
                                singerLaneMap = singerLaneMap,
                                modifier = lineModifier,
                                onLineClick = { clickedLine, clickedIndex ->
                                    isManualScrolling = false
                                    onLineClick(clickedLine, clickedIndex)
                                },
                            )
                        }
                    }
                }

                // Floating Controls: Sync, Timing Offset Nudge, and Share
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Expandable timing offset nudge row
                        AnimatedVisibility(
                            visible = isNudgeExpanded,
                            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
                            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                                shadowElevation = 8.dp,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp,
                                        vertical = 10.dp
                                    ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    val offsetDisplay = if (lyricsSyncOffsetMs == 0L) "0.0s"
                                    else "${if (lyricsSyncOffsetMs > 0) "+" else ""}${
                                        String.format(
                                            java.util.Locale.US,
                                            "%.1fs",
                                            lyricsSyncOffsetMs / 1000.0
                                        )
                                    }"
                                    Text(
                                        text = "Timing Offset: $offsetDisplay",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        NudgeChip(label = "-0.5s") { lyricsSyncOffsetMs -= 500L }
                                        NudgeChip(label = "-0.1s") { lyricsSyncOffsetMs -= 100L }
                                        NudgeChip(
                                            label = "0.0s",
                                            isReset = true,
                                            isActive = lyricsSyncOffsetMs == 0L,
                                        ) { lyricsSyncOffsetMs = 0L }
                                        NudgeChip(label = "+0.1s") { lyricsSyncOffsetMs += 100L }
                                        NudgeChip(label = "+0.5s") { lyricsSyncOffsetMs += 500L }
                                    }
                                }
                            }
                        }

                        // Bottom pills row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 1. Sync button (only visible when manual scrolling)
                            AnimatedVisibility(
                                visible = isManualScrolling && hasTiming && activeIndex in lyricsLines.indices,
                                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
                                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
                            ) {
                                Surface(
                                    onClick = {
                                        isManualScrolling = false
                                        coroutineScope.launch {
                                            lazyListState.scrollLyricIntoFocus(
                                                index = activeIndex,
                                                animateToNearbyItem = true,
                                                force = true,
                                                alignByItemCenter = hasWordTimings,
                                                isSeek = true,
                                            )
                                        }
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shadowElevation = 8.dp,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(
                                            horizontal = 14.dp,
                                            vertical = 9.dp
                                        ),
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_song_wave),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Sync",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            // 2. Timing offset nudge trigger pill
                            if (hasTiming) {
                                Surface(
                                    onClick = { isNudgeExpanded = !isNudgeExpanded },
                                    shape = CircleShape,
                                    color = if (lyricsSyncOffsetMs != 0L || isNudgeExpanded) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                                            .copy(alpha = 0.92f)
                                    },
                                    contentColor = if (lyricsSyncOffsetMs != 0L || isNudgeExpanded) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    shadowElevation = 6.dp,
                                    border = BorderStroke(
                                        1.dp,
                                        if (lyricsSyncOffsetMs != 0L) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    ),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 8.dp
                                        ),
                                    ) {
                                        val offsetText = if (lyricsSyncOffsetMs == 0L) "±0.0s"
                                        else "${if (lyricsSyncOffsetMs > 0) "+" else ""}${
                                            String.format(
                                                java.util.Locale.US,
                                                "%.1fs",
                                                lyricsSyncOffsetMs / 1000.0
                                            )
                                        }"
                                        Text(
                                            text = offsetText,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            // [Rom] chip
                            Surface(
                                onClick = { showRomanization = !showRomanization },
                                shape = CircleShape,
                                color = if (showRomanization) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                                        .copy(alpha = 0.92f)
                                },
                                contentColor = if (showRomanization) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                shadowElevation = 6.dp,
                                border = BorderStroke(
                                    1.dp,
                                    if (showRomanization) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    ),
                                ) {
                                    Text(
                                        text = "Rom",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            // [Trans] chip
                            Surface(
                                onClick = {
                                    val next = !showTranslation
                                    showTranslation = next
                                    if (next && translatedLines == null && lyricsLines.none { it.translations.isNotEmpty() } && !isTranslating) {
                                        isTranslating = true
                                        coroutineScope.launch {
                                            try {
                                                translatedLines =
                                                    LyricsTranslator.translateLines(lyricsLines)
                                            } finally {
                                                isTranslating = false
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                color = if (showTranslation) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                                        .copy(alpha = 0.92f)
                                },
                                contentColor = if (showTranslation) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                shadowElevation = 6.dp,
                                border = BorderStroke(
                                    1.dp,
                                    if (showTranslation) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    ),
                                ) {
                                    if (isTranslating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp,
                                            color = if (showTranslation) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "Trans",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            // 3. Share button pill
                            Surface(
                                onClick = { showShareDialog = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                                    .copy(alpha = 0.92f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                shadowElevation = 6.dp,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_player_lyrics),
                                        contentDescription = "Share",
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Share",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }

                if (showShareDialog) {
                    LyricsShareDialog(
                        track = track,
                        lyricsLines = displayLyricsLines,
                        activeLineIndex = activeIndex,
                        onDismissRequest = { showShareDialog = false },
                    )
                }
            }
        }
    }
}

private data class WordRenderItem(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val isBackground: Boolean = false,
)

private data class WordGroup(
    val syllables: List<WordRenderItem>,
)

private val BackgroundOuterBracketPairs = mapOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
    '（' to '）',
    '［' to '］',
    '｛' to '｝',
    '〈' to '〉',
    '《' to '》',
    '「' to '」',
    '『' to '』',
)

private fun stripOuterBackgroundBrackets(items: List<WordRenderItem>): List<WordRenderItem> {
    var cleaned = items

    while (true) {
        val firstIndex = cleaned.indexOfFirst { item -> item.text.any { !it.isWhitespace() } }
        val lastIndex = cleaned.indexOfLast { item -> item.text.any { !it.isWhitespace() } }
        if (firstIndex < 0 || lastIndex < 0) {
            return cleaned.filter { it.text.isNotBlank() }
        }

        val firstText = cleaned[firstIndex].text
        val openingIndex = firstText.indexOfFirst { !it.isWhitespace() }
        val closing = BackgroundOuterBracketPairs[firstText[openingIndex]] ?: return cleaned
        val lastText = cleaned[lastIndex].text
        val closingIndex = lastText.indexOfLast { !it.isWhitespace() }
        if (lastText[closingIndex] != closing) return cleaned

        val updated = cleaned.toMutableList()
        val first = updated[firstIndex]
        updated[firstIndex] = first.copy(
            text = first.text.removeRange(openingIndex, openingIndex + 1),
        )
        val last = updated[lastIndex]
        val updatedClosingIndex = last.text.indexOfLast { !it.isWhitespace() }
        updated[lastIndex] = last.copy(
            text = last.text.removeRange(updatedClosingIndex, updatedClosingIndex + 1),
        )
        cleaned = updated
    }
}

private val WordBoundaryPunctuation =
    setOf(',', '.', '!', '?', ';', ':', '-', '—', ')', ']', '}', '"', '\'')

private fun groupIntoWords(
    items: List<WordRenderItem>,
    fullLineText: String,
    isCjk: Boolean,
    isRtl: Boolean,
): List<WordGroup> {
    if (items.isEmpty()) return emptyList()
    if (isCjk) {
        return items.map { WordGroup(listOf(it)) }
    }

    val groups = mutableListOf<WordGroup>()
    val currentSyllables = mutableListOf<WordRenderItem>()
    var searchIndex = 0

    val lineHasSpaces = fullLineText.any { it.isWhitespace() }

    items.forEachIndexed { index, item ->
        val rawText = item.text
        val trimmed = rawText.trim()
        val hasTrailingSpace = rawText.isNotEmpty() && rawText.last().isWhitespace()
        val endsWithHyphen = trimmed.endsWith('-') || trimmed.endsWith('—')

        var isWordBoundary = true

        if (endsWithHyphen) {
            isWordBoundary = false
        } else {
            val matchStart = if (lineHasSpaces && trimmed.isNotEmpty()) {
                fullLineText.indexOf(trimmed, searchIndex, ignoreCase = true)
                    .takeIf { it >= 0 }
                    ?: fullLineText.indexOf(trimmed, 0, ignoreCase = true).takeIf { it >= 0 }
            } else null

            if (matchStart != null) {
                val endPos = matchStart + trimmed.length
                searchIndex = endPos

                if (!hasTrailingSpace) {
                    val isInternalSyllable =
                        endPos < fullLineText.length && fullLineText[endPos].isLetterOrDigit()
                    if (isInternalSyllable) {
                        isWordBoundary = false
                    }
                }
            }
        }

        currentSyllables.add(item.copy(text = trimmed))

        if (isWordBoundary || index == items.lastIndex) {
            if (currentSyllables.isNotEmpty()) {
                groups.add(WordGroup(currentSyllables.toList()))
                currentSyllables.clear()
            }
        }
    }

    if (currentSyllables.isNotEmpty()) {
        groups.add(WordGroup(currentSyllables.toList()))
    }

    return groups
}

@Composable
private fun LyricsLineItem(
    line: LyricsLine,
    isActive: Boolean,
    index: Int,
    nextLineStartMs: Long?,
    currentTimeProvider: () -> Long,
    textColor: Color,
    secondaryTextColor: Color,
    showRomanization: Boolean = true,
    showTranslation: Boolean = false,
    singerLaneMap: Map<String, LyricSingerLane> = emptyMap(),
    modifier: Modifier = Modifier,
    onLineClick: (LyricsLine, Int) -> Unit = { _, _ -> },
) {
    val lane = resolveLane(line.singer, line.agent, singerLaneMap)
    val textAlign = when (lane) {
        LyricSingerLane.Left -> TextAlign.Start
        LyricSingerLane.Right -> TextAlign.End
        LyricSingerLane.Center -> TextAlign.Center
    }
    val horizontalAlignment = when (lane) {
        LyricSingerLane.Left -> Alignment.Start
        LyricSingerLane.Right -> Alignment.End
        LyricSingerLane.Center -> Alignment.CenterHorizontally
    }

    val lineEndMs = if (line.endMs > line.startMs) {
        line.endMs
    } else {
        nextLineStartMs ?: (line.startMs + 4000L)
    }

    if (line.isInstrumental) {
        val currentTime = currentTimeProvider()
        val isInstrumentalActive = isActive || (currentTime in line.startMs..lineEndMs)
        AnimatedVisibility(
            visible = isInstrumentalActive,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            ) + fadeIn(animationSpec = tween(350)),
            exit = shrinkVertically(
                animationSpec = tween(
                    durationMillis = 350,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeOut(animationSpec = tween(250)),
        ) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                contentAlignment = when (lane) {
                    LyricSingerLane.Left -> Alignment.CenterStart
                    LyricSingerLane.Right -> Alignment.CenterEnd
                    LyricSingerLane.Center -> Alignment.Center
                },
            ) {
                WaitingDotsView(
                    startTime = line.startMs,
                    endTime = lineEndMs,
                    currentProgressMs = currentTime,
                    primaryColor = textColor,
                    onClick = {
                        onLineClick(line, index)
                    },
                )
            }
        }
        return
    }

    val lineFontSize = 28.sp
    val lineFontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold
    val lineIsRtl = remember(line.text) { isRtlText(line.text) }
    val isCjk =
        remember(line.text) { isChinese(line.text) || isJapanese(line.text) || isKorean(line.text) }

    val wordSpacing = if (isCjk) 0.dp else 7.dp
    val flowArrangement = when (lane) {
        LyricSingerLane.Left -> Arrangement.spacedBy(wordSpacing, Alignment.Start)
        LyricSingerLane.Right -> Arrangement.spacedBy(wordSpacing, Alignment.End)
        LyricSingerLane.Center -> Arrangement.spacedBy(wordSpacing, Alignment.CenterHorizontally)
    }
    val bgWordSpacing = if (isCjk) 0.dp else 6.dp
    val bgFlowArrangement = when (lane) {
        LyricSingerLane.Left -> Arrangement.spacedBy(bgWordSpacing, Alignment.Start)
        LyricSingerLane.Right -> Arrangement.spacedBy(bgWordSpacing, Alignment.End)
        LyricSingerLane.Center -> Arrangement.spacedBy(bgWordSpacing, Alignment.CenterHorizontally)
    }

    val baseWords = remember(line.words) { line.words.filter { it.text.isNotEmpty() } }
    val hasWordTimings = baseWords.isNotEmpty()

    val wordsToRender: List<WordRenderItem> =
        remember(baseWords, line.text, lineIsRtl, isCjk, lineEndMs) {
            if (!hasWordTimings) emptyList()
            else {
                baseWords.flatMapIndexed { idx, word ->
                    val prevText = baseWords.getOrNull(idx - 1)?.text
                    val nextText = baseWords.getOrNull(idx + 1)?.text
                    val includeSpace = if (isCjk) {
                        val currEdge =
                            if (lineIsRtl) word.text.firstOrNull() else word.text.lastOrNull()
                        val neighborEdge =
                            if (lineIsRtl) prevText?.lastOrNull() else nextText?.firstOrNull()
                        val neighbor = if (lineIsRtl) prevText else nextText
                        currEdge != null && neighborEdge != null && neighbor != null &&
                                (currEdge.code < 0x3000 || neighborEdge.code < 0x3000) &&
                                shouldAppendWordSpace(
                                    if (lineIsRtl) neighbor else word.text,
                                    if (lineIsRtl) word.text else neighbor,
                                )
                    } else false

                    val wordStartMs = word.startMs
                    val wordEndMs = word.endMs
                        ?.takeIf { it > wordStartMs }
                        ?: baseWords.getOrNull(idx + 1)?.startMs?.takeIf { it > wordStartMs }
                        ?: lineEndMs.takeIf { it > wordStartMs }
                        ?: (wordStartMs + 400L)
                    val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

                    if (isCjk && word.text.length > 3) {
                        val chars = word.text.toList()
                        chars.mapIndexed { charIdx, char ->
                            val charStartMs = wordStartMs + (wordDuration * charIdx / chars.size)
                            val charEndMs =
                                wordStartMs + (wordDuration * (charIdx + 1) / chars.size)
                            val charText = when {
                                includeSpace && !lineIsRtl && charIdx == chars.lastIndex -> "$char "
                                includeSpace && lineIsRtl && charIdx == 0 -> " $char"
                                else -> char.toString()
                            }
                            WordRenderItem(charText, charStartMs, charEndMs, false)
                        }
                    } else {
                        listOf(WordRenderItem(word.text, wordStartMs, wordEndMs, false))
                    }
                }
            }
        }

    val baseBgWords =
        remember(line.backgroundWords) { line.backgroundWords.filter { it.text.isNotEmpty() } }
    val bgWordsToRender: List<WordRenderItem> =
        remember(baseBgWords, line.text, lineIsRtl, isCjk, lineEndMs) {
            if (baseBgWords.isEmpty()) emptyList()
            else stripOuterBackgroundBrackets(
                baseBgWords.mapIndexed { idx, word ->
                    val wordStartMs = word.startMs
                    val wordEndMs = word.endMs
                        ?.takeIf { it > wordStartMs }
                        ?: baseBgWords.getOrNull(idx + 1)?.startMs?.takeIf { it > wordStartMs }
                        ?: lineEndMs.takeIf { it > wordStartMs }
                        ?: (wordStartMs + 400L)
                    WordRenderItem(word.text, wordStartMs, wordEndMs, true)
                },
            )
        }
    val visibleBgWords by remember(bgWordsToRender, currentTimeProvider) {
        derivedStateOf {
            val currentTime = currentTimeProvider()
            val firstBackgroundStart = bgWordsToRender.minOfOrNull { it.startMs }
            if (firstBackgroundStart != null && currentTime >= firstBackgroundStart) {
                bgWordsToRender
            } else {
                emptyList()
            }
        }
    }

    val wordGroups = remember(wordsToRender, line.text, isCjk, lineIsRtl) {
        groupIntoWords(wordsToRender, line.text, isCjk, lineIsRtl)
    }
    val bgWordGroups = remember(visibleBgWords, line.text, isCjk, lineIsRtl) {
        groupIntoWords(visibleBgWords, line.text, isCjk, lineIsRtl)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        if (hasWordTimings) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = flowArrangement,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                wordGroups.forEach { group ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        group.syllables.forEach { item ->
                            KaraokeWord(
                                text = item.text,
                                startTime = item.startMs,
                                endTime = item.endMs,
                                currentTimeProvider = currentTimeProvider,
                                isRtl = lineIsRtl,
                                fontSize = lineFontSize,
                                textColor = textColor,
                                inactiveAlpha = if (isActive) 0.35f else 0.52f,
                                fontWeight = lineFontWeight,
                                isBackground = false,
                            )
                        }
                    }
                }
            }
        } else {
            LineSyncedSweepText(
                text = line.text,
                startTime = line.startMs,
                endTime = lineEndMs,
                currentTimeProvider = currentTimeProvider,
                isRtl = lineIsRtl,
                fontSize = lineFontSize,
                textColor = textColor,
                isActive = isActive,
                alignment = textAlign,
            )
        }

        AnimatedVisibility(
            visible = visibleBgWords.isNotEmpty(),
            enter = expandVertically(
                expandFrom = Alignment.CenterVertically,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) + slideInVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { it / 3 },
            exit = shrinkVertically(
                shrinkTowards = Alignment.CenterVertically,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) + slideOutVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { it / 3 },
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = bgFlowArrangement,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    bgWordGroups.forEach { group ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            group.syllables.forEach { item ->
                                KaraokeWord(
                                    text = item.text,
                                    startTime = item.startMs,
                                    endTime = item.endMs,
                                    currentTimeProvider = currentTimeProvider,
                                    isRtl = lineIsRtl,
                                    fontSize = 22.sp,
                                    textColor = textColor.copy(alpha = 0.92f),
                                    inactiveAlpha = if (isActive) 0.5f else 0.52f,
                                    fontWeight = FontWeight.SemiBold,
                                    isBackground = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTranslation && line.translations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            val transText = line.translations.joinToString("\n") { it.text }
            Text(
                text = transText,
                fontSize = 16.sp,
                lineHeight = (16 * 1.35f).sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor.copy(alpha = 0.76f),
                textAlign = textAlign,
                style = LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showRomanization && !line.romanization.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = line.romanization,
                fontSize = 15.sp,
                lineHeight = (15 * 1.35f).sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor.copy(alpha = 0.65f),
                textAlign = textAlign,
                style = LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun KaraokeWord(
    text: String,
    startTime: Long,
    endTime: Long,
    currentTimeProvider: () -> Long,
    isRtl: Boolean,
    fontSize: TextUnit,
    textColor: Color,
    inactiveAlpha: Float,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    isBackground: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val duration = (endTime - startTime).coerceAtLeast(1L)
    val glowPadding = 10.dp

    val len = text.trim().length.coerceAtLeast(1)
    val msPerChar = duration.toFloat() / len.toFloat()
    val isHeavy = (msPerChar >= 200f && len <= 9) || duration >= 900L

    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val glowPaddingPx = glowPadding.roundToPx()
                val looseConstraints = constraints.copy(
                    minWidth = 0,
                    maxWidth = Constraints.Infinity,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                )
                val placeable = measurable.measure(looseConstraints)

                val coreWidth = (placeable.width - glowPaddingPx * 2).coerceAtLeast(0)
                val coreHeight = (placeable.height - glowPaddingPx * 2).coerceAtLeast(0)

                layout(coreWidth, coreHeight) {
                    placeable.place(-glowPaddingPx, -glowPaddingPx)
                }
            }
            .graphicsLayer {
                clip = false
            },
    ) {
        val effectiveFontSize = if (isBackground) fontSize * 0.78f else fontSize
        val effectiveAlpha = if (isBackground) 0.85f else 1f
        val activeWordShadow = if (isHeavy) {
            Shadow(
                color = textColor.copy(alpha = 0.50f * effectiveAlpha),
                offset = Offset.Zero,
                blurRadius = 14f,
            )
        } else {
            null
        }

        // 1. Inactive (unfilled) layer
        Text(
            text = text,
            fontSize = effectiveFontSize,
            lineHeight = 38.sp,
            color = textColor.copy(alpha = inactiveAlpha * effectiveAlpha),
            fontWeight = fontWeight,
            modifier = Modifier.padding(glowPadding),
        )

        // 2. Completed (filled) layer
        Text(
            text = text,
            fontSize = effectiveFontSize,
            lineHeight = 38.sp,
            color = textColor.copy(alpha = effectiveAlpha),
            fontWeight = fontWeight,
            modifier = Modifier
                .padding(glowPadding)
                .drawWithContent {
                    val currentTime = currentTimeProvider()
                    val isDone = currentTime >= endTime
                    if (isDone) {
                        drawContent()
                    }
                },
        )

        // 3. Active (filling) layer - soft mask
        Box(
            modifier = Modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen

                    val currentTime = currentTimeProvider()
                    val fadeDuration = 200L

                    if (currentTime >= endTime) {
                        val timeSinceEnd = currentTime - endTime
                        val fadeProgress =
                            (timeSinceEnd.toFloat() / fadeDuration.toFloat()).coerceIn(0f, 1f)
                        alpha = 1f - fadeProgress
                    } else {
                        alpha = 1f
                    }
                }
                .drawWithContent {
                    val currentTime = currentTimeProvider()
                    val progress = if (duration > 0) {
                        val elapsed = currentTime - startTime
                        (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else if (currentTime >= endTime) {
                        1f
                    } else {
                        0f
                    }

                    val fadeDuration = 200L
                    val isFading = currentTime >= endTime && currentTime < (endTime + fadeDuration)

                    if ((progress > 0f && progress < 1f) || isFading) {
                        drawContent()

                        val fadeWidth = 20f
                        val totalWidth = size.width
                        val paddingPx = glowPadding.toPx()

                        val textWidth = totalWidth - (paddingPx * 2)
                        val fillWidth = textWidth * progress

                        val endFraction =
                            ((paddingPx + fillWidth + fadeWidth) / totalWidth).coerceIn(0f, 1f)
                        val solidFraction = ((paddingPx + fillWidth) / totalWidth).coerceIn(0f, 1f)

                        val softFillBrush = if (!isRtl) {
                            val solidPos = solidFraction.coerceIn(0f, 1f)
                            val endPos = endFraction.coerceIn(solidPos, 1f)
                            Brush.horizontalGradient(
                                0f to Color.Black,
                                solidPos to Color.Black,
                                endPos to Color.Transparent,
                                1f to Color.Transparent,
                            )
                        } else {
                            val solidStartX =
                                (paddingPx + (textWidth - fillWidth)).coerceIn(0f, totalWidth)
                            val fadeStartX = (solidStartX - fadeWidth).coerceIn(0f, totalWidth)
                            val fadeStartPos = (fadeStartX / totalWidth).coerceIn(0f, 1f)
                            val solidStartPos =
                                (solidStartX / totalWidth).coerceIn(fadeStartPos, 1f)
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                fadeStartPos to Color.Transparent,
                                solidStartPos to Color.Black,
                                1f to Color.Black,
                            )
                        }

                        drawRect(
                            brush = softFillBrush,
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
                .padding(glowPadding),
        ) {
            Text(
                text = text,
                fontSize = effectiveFontSize,
                lineHeight = 38.sp,
                color = textColor.copy(alpha = effectiveAlpha),
                fontWeight = fontWeight,
                style = LocalTextStyle.current.copy(shadow = activeWordShadow),
            )
        }
    }
}

@Composable
private fun LineSyncedSweepText(
    text: String,
    startTime: Long,
    endTime: Long,
    currentTimeProvider: () -> Long,
    isRtl: Boolean,
    fontSize: TextUnit,
    textColor: Color,
    isActive: Boolean,
    alignment: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier,
) {
    val duration = (endTime - startTime).coerceAtLeast(1L)
    val lineHeight = 38.sp
    val paragraphStyle = LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph)

    if (!isActive) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = textColor.copy(alpha = 0.52f),
            textAlign = alignment,
            lineHeight = lineHeight,
            style = paragraphStyle,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = when (alignment) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        },
    ) {
        // 1. Inactive underlying text
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.35f),
            textAlign = alignment,
            lineHeight = lineHeight,
            style = paragraphStyle,
            modifier = Modifier.fillMaxWidth(),
        )

        // 2. Completed layer (once current time >= endTime)
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            textAlign = alignment,
            lineHeight = lineHeight,
            style = paragraphStyle,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    val currentTime = currentTimeProvider()
                    if (currentTime >= endTime) {
                        drawContent()
                    }
                },
        )

        // 3. Active filling layer with horizontal sweep gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    val currentTime = currentTimeProvider()
                    val fadeDuration = 250L
                    if (currentTime >= endTime) {
                        val timeSinceEnd = currentTime - endTime
                        val fadeProgress =
                            (timeSinceEnd.toFloat() / fadeDuration.toFloat()).coerceIn(0f, 1f)
                        alpha = 1f - fadeProgress
                    } else {
                        alpha = 1f
                    }
                }
                .drawWithContent {
                    val currentTime = currentTimeProvider()
                    val progress = if (duration > 0) {
                        val elapsed = currentTime - startTime
                        (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else if (currentTime >= endTime) {
                        1f
                    } else {
                        0f
                    }

                    val fadeDuration = 250L
                    val isFading = currentTime >= endTime && currentTime < (endTime + fadeDuration)

                    if ((progress > 0f && progress < 1f) || isFading) {
                        drawContent()

                        val fadeWidth = 40f
                        val totalWidth = size.width
                        val fillWidth = totalWidth * progress

                        val endFraction = ((fillWidth + fadeWidth) / totalWidth).coerceIn(0f, 1f)
                        val solidFraction = (fillWidth / totalWidth).coerceIn(0f, 1f)

                        val sweepBrush = if (!isRtl) {
                            val solidPos = solidFraction.coerceIn(0f, 1f)
                            val endPos = endFraction.coerceIn(solidPos, 1f)
                            Brush.horizontalGradient(
                                0f to Color.Black,
                                solidPos to Color.Black,
                                endPos to Color.Transparent,
                                1f to Color.Transparent,
                            )
                        } else {
                            val solidStartX = (totalWidth - fillWidth).coerceIn(0f, totalWidth)
                            val fadeStartX = (solidStartX - fadeWidth).coerceIn(0f, totalWidth)
                            val fadeStartPos = (fadeStartX / totalWidth).coerceIn(0f, 1f)
                            val solidStartPos =
                                (solidStartX / totalWidth).coerceIn(fadeStartPos, 1f)
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                fadeStartPos to Color.Transparent,
                                solidStartPos to Color.Black,
                                1f to Color.Black,
                            )
                        }

                        drawRect(
                            brush = sweepBrush,
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                textAlign = alignment,
                lineHeight = lineHeight,
                style = paragraphStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NudgeChip(
    label: String,
    isReset: Boolean = false,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
        else if (isReset) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

private fun List<LyricsLine>.hasTiming(): Boolean =
    any { line ->
        line.startMs > 0L || line.endMs > 0L ||
                (line.words + line.backgroundWords).any { word -> word.startMs > 0L }
    }

private fun List<LyricsLine>.hasWordTimings(): Boolean =
    any { line -> line.words.isNotEmpty() || line.backgroundWords.isNotEmpty() }

private fun cleanSingerName(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    var s = raw.trim()
    while ((s.startsWith("[") && s.endsWith("]")) || (s.startsWith("(") && s.endsWith(")"))) {
        s = s.substring(1, s.length - 1).trim()
    }
    s = s.removeSuffix(":").removeSuffix("：").trim()
    return s.replace(Regex("\\s*/\\s*"), " & ").trim()
}

enum class LyricSingerLane {
    Left,
    Right,
    Center,
}

private fun isDuetOrGroup(singerName: String?): Boolean {
    if (singerName.isNullOrBlank()) return false
    val lower = singerName.lowercase()
    return lower == "v3" || lower == "singer 3" ||
            lower.contains('/') || lower.contains('&') ||
            lower.contains("both") || lower.contains("all") ||
            lower.contains("chorus") || lower.contains("duet") ||
            lower.contains("together") || lower.contains("ft.") ||
            lower.contains("feat.") || lower.contains('合')
}

private fun resolveLane(
    singer: String?,
    agent: String?,
    singerLaneMap: Map<String, LyricSingerLane>,
): LyricSingerLane {
    val cleanedSinger = cleanSingerName(singer).takeIf { it.isNotBlank() }
    if (cleanedSinger != null) {
        val sLower = cleanedSinger.lowercase()
        val mapped = singerLaneMap[sLower]
        if (mapped != null) return mapped
        if (isDuetOrGroup(sLower)) return LyricSingerLane.Center
        if (sLower.contains("sean") || sLower.contains("paul") || sLower.contains("henriques")) {
            return LyricSingerLane.Right
        }
        if (sLower.contains("sia")) {
            return LyricSingerLane.Left
        }
    }

    val agentLower = agent?.trim()?.lowercase()
    if (agentLower != null) {
        val mapped = singerLaneMap[agentLower]
        if (mapped != null) return mapped
        if (isDuetOrGroup(agentLower) || agentLower == "v3") return LyricSingerLane.Center
        if (agentLower == "v1") return LyricSingerLane.Left
        if (agentLower == "v2") return LyricSingerLane.Right
        if (agentLower.contains("sean") || agentLower.contains("paul") || agentLower.contains("henriques")) {
            return LyricSingerLane.Right
        }
        if (agentLower.contains("sia")) return LyricSingerLane.Left
    }

    return LyricSingerLane.Left
}
