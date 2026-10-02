package org.shilpo.laboon.ui.screens.player.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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

private val SmoothDecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
private val NoSpaceAfterChars: Set<Char> = setOf('(', '[', '{', '«', '‹', '“', '‘')

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)

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
    var frozenPositionMs by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(track, lyricsLines) {
        playbackPositionMs.longValue = currentPositionMs.coerceAtLeast(0L)
        isManualScrolling = false
        lastManualScrollTime = 0L
        frozenPositionMs = -1L
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
    val hasMultipleSingers =
        remember(displayLyricsLines) { displayLyricsLines.any { it.agent?.lowercase() == "v2" } }

    val currentTimeProvider: () -> Long = remember(leadMs) {
        {
            val baseMs = if (isManualScrolling && frozenPositionMs >= 0L) {
                frozenPositionMs
            } else {
                playbackPositionMs.longValue
            }
            (baseMs + leadMs + LYRIC_VISUAL_TUNING_OFFSET_MS + lyricsSyncOffsetMs).coerceAtLeast(0L)
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
            frozenPositionMs = playbackPositionMs.longValue
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
            frozenPositionMs = -1L
        } else {
            frozenPositionMs = -1L
        }
    }

    LaunchedEffect(activeIndex, isManualScrolling) {
        if (!isManualScrolling && activeIndex in lyricsLines.indices && lyricsFractionProvider() > 0.05f) {
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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

                        val lineTransformOrigin = remember(line.agent) {
                            when (line.agent?.lowercase()) {
                                "v2" -> TransformOrigin(1f, 0.5f)
                                "v1", null -> TransformOrigin(0f, 0.5f)
                                else -> TransformOrigin(0.5f, 0.5f)
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
                            .clickable {
                                isManualScrolling = false
                                frozenPositionMs = -1L
                                onLineClick(line, index)
                            }
                            .padding(vertical = 4.dp)

                        LyricsLineItem(
                            line = line,
                            isActive = isActive,
                            nextLineStartMs = displayLyricsLines.getOrNull(index + 1)?.startMs,
                            currentTimeProvider = currentTimeProvider,
                            textColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            hasMultipleSingers = hasMultipleSingers,
                            showRomanization = showRomanization,
                            showTranslation = showTranslation,
                            modifier = lineModifier,
                        )
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
                                        frozenPositionMs = -1L
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

@Composable
private fun LyricsLineItem(
    line: LyricsLine,
    isActive: Boolean,
    nextLineStartMs: Long?,
    currentTimeProvider: () -> Long,
    textColor: Color,
    secondaryTextColor: Color,
    hasMultipleSingers: Boolean,
    showRomanization: Boolean = true,
    showTranslation: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val agentLower = line.agent?.lowercase()
    val textAlign = when (agentLower) {
        "v1", null -> TextAlign.Start
        "v2" -> TextAlign.End
        else -> TextAlign.Center
    }
    val horizontalAlignment = when (agentLower) {
        "v1", null -> Alignment.Start
        "v2" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    val flowArrangement = when (agentLower) {
        "v1", null -> Arrangement.spacedBy(0.dp, Alignment.Start)
        "v2" -> Arrangement.spacedBy(0.dp, Alignment.End)
        else -> Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally)
    }

    val lineEndMs = if (line.endMs > line.startMs) {
        line.endMs
    } else {
        nextLineStartMs ?: (line.startMs + 4000L)
    }

    if (line.isInstrumental) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = when (agentLower) {
                "v2" -> Alignment.CenterEnd
                "v1" -> Alignment.CenterStart
                else -> Alignment.Center
            },
        ) {
            WaitingDotsView(
                startTime = line.startMs,
                endTime = lineEndMs,
                currentProgressMs = currentTimeProvider(),
                primaryColor = textColor,
            )
        }
        return
    }

    val lineFontSize = 28.sp
    val lineFontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold
    val lineIsRtl = remember(line.text) { isRtlText(line.text) }
    val isCjk =
        remember(line.text) { isChinese(line.text) || isJapanese(line.text) || isKorean(line.text) }

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
                    } else if (lineIsRtl) {
                        prevText != null && shouldAppendWordSpace(prevText, word.text)
                    } else {
                        nextText != null && shouldAppendWordSpace(word.text, nextText)
                    }

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
                        val displayText = when {
                            !includeSpace -> word.text
                            lineIsRtl -> " ${word.text}"
                            else -> "${word.text} "
                        }
                        listOf(WordRenderItem(displayText, wordStartMs, wordEndMs, false))
                    }
                }
            }
        }

    val baseBgWords =
        remember(line.backgroundWords) { line.backgroundWords.filter { it.text.isNotEmpty() } }
    val bgWordsToRender: List<WordRenderItem> =
        remember(baseBgWords, line.text, lineIsRtl, isCjk, lineEndMs) {
            if (baseBgWords.isEmpty()) emptyList()
            else {
                baseBgWords.mapIndexed { idx, word ->
                    val prevText = baseBgWords.getOrNull(idx - 1)?.text
                    val nextText = baseBgWords.getOrNull(idx + 1)?.text
                    val includeSpace = if (lineIsRtl) {
                        prevText != null && shouldAppendWordSpace(prevText, word.text)
                    } else {
                        nextText != null && shouldAppendWordSpace(word.text, nextText)
                    }
                    val wordStartMs = word.startMs
                    val wordEndMs = word.endMs
                        ?.takeIf { it > wordStartMs }
                        ?: baseBgWords.getOrNull(idx + 1)?.startMs?.takeIf { it > wordStartMs }
                        ?: lineEndMs.takeIf { it > wordStartMs }
                        ?: (wordStartMs + 400L)
                    val displayText = when {
                        !includeSpace -> word.text
                        lineIsRtl -> " ${word.text}"
                        else -> "${word.text} "
                    }
                    WordRenderItem(displayText, wordStartMs, wordEndMs, true)
                }
            }
        }

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        // Singer badge for duet / multi-singer tracks
        if (hasMultipleSingers && !line.isInstrumental) {
            val singerLabel = when (agentLower) {
                "v1" -> "Singer 1"
                "v2" -> "Singer 2"
                "v3" -> "Singer 3"
                "all", "both", "chorus" -> "All"
                null -> "Singer 1"
                else -> line.agent.replaceFirstChar { it.uppercase() }
            }
            val singerTagColor = if (agentLower == "v2") {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }

            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = singerTagColor.copy(alpha = if (isActive) 0.22f else 0.12f),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Text(
                    text = singerLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = singerTagColor.copy(alpha = if (isActive) 1f else 0.75f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        if (hasWordTimings) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = flowArrangement,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                wordsToRender.forEach { item ->
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
                        nudgeEnabled = isActive,
                    )
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

        if (bgWordsToRender.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = flowArrangement,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                bgWordsToRender.forEach { item ->
                    KaraokeWord(
                        text = item.text,
                        startTime = item.startMs,
                        endTime = item.endMs,
                        currentTimeProvider = currentTimeProvider,
                        isRtl = lineIsRtl,
                        fontSize = 22.sp,
                        textColor = textColor.copy(alpha = 0.78f),
                        inactiveAlpha = if (isActive) 0.35f else 0.52f,
                        fontWeight = FontWeight.SemiBold,
                        isBackground = true,
                        nudgeEnabled = isActive,
                    )
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
    nudgeEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val duration = (endTime - startTime).coerceAtLeast(1L)
    val glowPadding = 10.dp

    val len = text.trim().length.coerceAtLeast(1)
    val msPerChar = duration.toFloat() / len.toFloat()
    val isHeavy = (msPerChar >= 200f && len <= 9) || duration >= 900L

    val particleEmitter = remember { SparkleParticleEmitter() }
    var particleTick by remember { mutableLongStateOf(0L) }
    val isWordActive = nudgeEnabled && isHeavy

    LaunchedEffect(isWordActive) {
        if (!isWordActive) {
            particleEmitter.clear()
            return@LaunchedEffect
        }
        var lastNanos = withFrameNanos { it }
        while (isWordActive) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNanos = now
            particleEmitter.update(dt)
            particleTick = now
        }
    }

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
                val currentTime = currentTimeProvider()

                val maxShift = 5f
                val attackDuration = 120L
                val decayDuration = 250L
                val totalImpulseTime = attackDuration + decayDuration

                val shift =
                    if (nudgeEnabled && currentTime >= startTime && currentTime < startTime + totalImpulseTime) {
                        val timeSinceStart = currentTime - startTime
                        if (timeSinceStart < attackDuration) {
                            val progress = timeSinceStart.toFloat() / attackDuration.toFloat()
                            lerp(0f, maxShift, progress)
                        } else {
                            val decayProgress =
                                (timeSinceStart - attackDuration).toFloat() / decayDuration.toFloat()
                            lerp(maxShift, 0f, decayProgress)
                        }
                    } else {
                        0f
                    }

                translationX = if (isRtl) -shift else shift
            },
    ) {
        val effectiveFontSize = if (isBackground) fontSize * 0.78f else fontSize
        val effectiveAlpha = if (isBackground) 0.6f else 1f

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
            )
        }

        // 4. Vocal climax sparkle particle emitter
        if (isHeavy) {
            Canvas(Modifier.matchParentSize()) {
                @Suppress("UNUSED_VARIABLE")
                val tick = particleTick
                val currentTime = currentTimeProvider()
                if (currentTime in startTime..endTime) {
                    val progress =
                        ((currentTime - startTime).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    val paddingPx = glowPadding.toPx()
                    val textWidth = (size.width - (paddingPx * 2)).coerceAtLeast(1f)
                    val fillWidth = textWidth * progress
                    val headX =
                        if (!isRtl) paddingPx + fillWidth else paddingPx + (textWidth - fillWidth)
                    particleEmitter.spawn(
                        headX = headX,
                        topY = paddingPx,
                        bottomY = size.height - paddingPx,
                        density = density,
                    )
                }
                particleEmitter.draw(this, textColor)
            }
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

    if (!isActive) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = textColor.copy(alpha = 0.52f),
            textAlign = alignment,
            lineHeight = lineHeight,
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
    any { line -> line.startMs > 0L || line.endMs > 0L || line.words.any { word -> word.startMs > 0L } }

private fun List<LyricsLine>.hasWordTimings(): Boolean =
    any { line -> line.words.isNotEmpty() }
