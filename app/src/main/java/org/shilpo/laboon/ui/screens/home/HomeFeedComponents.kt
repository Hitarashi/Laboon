@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.ui.design.TrackCodecBadges
import org.shilpo.laboon.ui.design.painterResource
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource

@OptIn(ExperimentalTextApi::class)
internal val DailyMixRoundedSans = FontFamily(
    Font(
        resId = R.font.gsans_flex_full,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Light.weight), FontVariation.Setting("ROND", 100f),
        ),
    ),
    Font(
        resId = R.font.gsans_flex_full,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Normal.weight), FontVariation.Setting("ROND", 100f),
        ),
    ),
    Font(
        resId = R.font.gsans_flex_full,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Medium.weight), FontVariation.Setting("ROND", 100f),
        ),
    ),
    Font(
        resId = R.font.gsans_flex_full,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.SemiBold.weight), FontVariation.Setting("ROND", 100f),
        ),
    ),
    Font(
        resId = R.font.gsans_flex_full,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Bold.weight), FontVariation.Setting("ROND", 100f),
        ),
    ),
)

@Composable
internal fun HomeArtwork(artworkUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val request = remember(context, artworkUrl) {
        ImageRequest.Builder(context).data(artworkUrl).crossfade(true).build()
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.app_icon_small),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun HomeSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun HomeDailyMixPanel(
    tracks: List<HomeTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlayTrack: (HomeTrack, List<HomeTrack>) -> Unit,
    onPlayNext: (HomeTrack) -> Unit,
    onAddToQueue: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    onLoadTrackGenres: suspend (HomeTrack) -> List<String>,
    onOpenDailyMix: () -> Unit,
) {
    var selectedTrack by remember { mutableStateOf<HomeTrack?>(null) }
    val playbackQueue = remember(tracks) { tracks.toList() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                HomeDailyMixHeader(tracks = tracks.take(3))
                HomeDailyMixSongRows(
                    tracks = tracks.take(4),
                    playbackQueue = playbackQueue,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    onPlayTrack = onPlayTrack,
                    onMoreOptions = { selectedTrack = it },
                )
                FilledTonalButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    onClick = onOpenDailyMix,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(
                        topStart = 10.dp,
                        topEnd = 10.dp,
                        bottomEnd = 60.dp,
                        bottomStart = 60.dp,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_daily_mix_see_all),
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = DailyMixRoundedSans),
                            fontWeight = FontWeight.Medium,
                        )
                        DailyMixSymbol(
                            symbol = "arrow_forward",
                            iconSlot = "action.forward",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }

    selectedTrack?.let { track ->
        HomeDailyMixSongOptionsSheet(
            track = track,
            onDismiss = { selectedTrack = null },
            onPlay = {
                onPlayTrack(track, playbackQueue)
                selectedTrack = null
            },
            onPlayNext = {
                onPlayNext(track)
                selectedTrack = null
            },
            onAddToQueue = {
                onAddToQueue(track)
                selectedTrack = null
            },
            onLoadTrackGenres = onLoadTrackGenres,
            onDownload = {
                onDownloadTrack(track)
                selectedTrack = null
            },
        )
    }
}

@Composable
private fun HomeDailyMixHeader(tracks: List<HomeTrack>) {
    val titleStyle = rememberDailyMixTitleStyle()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                ),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_daily_mix),
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.home_daily_mix_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DailyMixRoundedSans),
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
                tracks.forEachIndexed { index, track ->
                    val sizeModifier = when (index) {
                        0 -> Modifier.size(50.dp).padding(top = 4.dp)
                        1 -> Modifier.size(44.dp).padding(bottom = 4.dp)
                        else -> Modifier.size(48.dp)
                    }
                    val shape = dailyMixArtworkShape(index)
                    Box(
                        modifier = sizeModifier
                            .clip(shape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, shape),
                    ) {
                        HomeArtwork(track.artworkUrl, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

internal fun dailyMixArtworkShape(index: Int, thirdShapeCornerRadius: androidx.compose.ui.unit.Dp = 16.dp): Shape = when (index) {
    0 -> RoundedStarShape(sides = 6, rotation = 10f)
    1 -> CircleShape
    else -> RoundedCornerShape(thirdShapeCornerRadius)
}

@Composable
internal fun HomeDailyMixSongRows(
    tracks: List<HomeTrack>,
    playbackQueue: List<HomeTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlayTrack: (HomeTrack, List<HomeTrack>) -> Unit,
    onMoreOptions: (HomeTrack) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        tracks.forEach { track ->
            HomeDailyMixTrackRow(
                track = track,
                isCurrentSong = currentTrackId == track.id,
                isPlaying = isPlaying && currentTrackId == track.id,
                onClick = { onPlayTrack(track, playbackQueue) },
                onMoreOptionsClick = { onMoreOptions(track) },
            )
        }
    }
}

@Composable
private fun HomeDailyMixTrackRow(
    track: HomeTrack,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rowShape = if (isCurrentSong) RoundedCornerShape(50.dp) else RoundedCornerShape(10.dp)
    val containerColor = if (isCurrentSong) colors.primaryContainer else colors.surfaceContainerLow
    val contentColor = if (isCurrentSong) colors.onPrimaryContainer else colors.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(containerColor)
            .clickable(
                enabled = track.isPlayable,
                role = Role.Button,
                onClickLabel = stringResource(R.string.home_play_track, track.title, track.artist),
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = DailyMixRoundedSans),
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DailyMixRoundedSans),
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrentSong && isPlaying) {
            Icon(
                painter = painterResource(R.drawable.ic_song_wave),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        FilledIconButton(
            onClick = onMoreOptionsClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isCurrentSong) colors.onPrimaryContainer else colors.surfaceContainerHigh,
                contentColor = if (isCurrentSong) colors.primaryContainer else colors.onSurface,
            ),
            modifier = Modifier.size(36.dp).padding(end = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.home_daily_mix_more_options, track.title),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun HomeDailyMixSongOptionsSheet(
    track: HomeTrack,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onLoadTrackGenres: suspend (HomeTrack) -> List<String>,
    onDownload: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val maxPagerHeight = (
        configuration.screenHeightDp.dp -
            safeInsets.calculateTopPadding() -
            safeInsets.calculateBottomPadding() -
            180.dp
        ).coerceAtLeast(280.dp)
    var trackGenres by remember(track.providerTrackId) {
        mutableStateOf<List<String>?>(null)
    }

    LaunchedEffect(track.providerTrackId) {
        snapshotFlow { pagerState.currentPage }.first { it == 1 }
        trackGenres = runCatching { onLoadTrackGenres(track) }.getOrDefault(emptyList())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(80.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeArtwork(
                        artworkUrl = track.artworkUrl,
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(26.dp)),
                    )
                    DailyMixAutoSizingText(
                        text = track.title,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        fontFamily = DailyMixRoundedSans,
                        fontWeight = FontWeight.Light,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxPagerHeight)
                        .animateContentSize(),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        when (page) {
                            0 -> HomeDailyMixOptionsPage(
                                track = track,
                                onPlay = onPlay,
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                onDownload = onDownload,
                            )
                            else -> HomeDailyMixInfoPage(track = track, genres = trackGenres)
                        }
                    }
                }
            }

            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(5.dp),
                containerColor = Color.Transparent,
                divider = {},
                indicator = {},
            ) {
                DailyMixSheetTab(
                    index = 0,
                    selectedIndex = pagerState.currentPage,
                    title = stringResource(R.string.home_daily_mix_options_tab),
                    symbol = "menu",
                    iconSlot = "action.options",
                    filled = true,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                )
                DailyMixSheetTab(
                    index = 1,
                    selectedIndex = pagerState.currentPage,
                    title = stringResource(R.string.home_daily_mix_info_tab),
                    symbol = "info",
                    iconSlot = "action.info",
                    filled = true,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                )
            }
        }
    }
}

@Composable
private fun HomeDailyMixOptionsPage(
    track: HomeTrack,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MediumExtendedFloatingActionButton(
                    text = {
                        Text(
                            modifier = Modifier.padding(end = 10.dp),
                            text = stringResource(R.string.home_daily_mix_play),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = DailyMixRoundedSans),
                        )
                    },
                    icon = {
                        DailyMixSymbol(
                            symbol = "play_arrow",
                            iconSlot = "playback.play",
                            filled = true,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = { if (track.isPlayable) onPlay() },
                    modifier = Modifier.weight(0.5f).fillMaxHeight(),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                    shape = RoundedCornerShape(26.dp),
                    containerColor = if (track.isPlayable) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    contentColor = if (track.isPlayable) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                FilledIconButton(
                    modifier = Modifier.weight(0.25f).fillMaxHeight(),
                    onClick = {},
                    enabled = false,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    DailyMixSymbol(
                        symbol = "favorite",
                        iconSlot = "playback.favorite",
                        filled = false,
                        contentDescription = stringResource(R.string.home_daily_mix_favorite_unavailable),
                        modifier = Modifier.size(24.dp),
                    )
                }
                FilledTonalIconButton(
                    modifier = Modifier.weight(0.25f).fillMaxHeight(),
                    onClick = { shareAppleMusicTrack(context, track) },
                    enabled = true,
                    shape = CircleShape,
                ) {
                    DailyMixSymbol(
                        symbol = "share",
                        iconSlot = "action.share",
                        contentDescription = stringResource(R.string.home_daily_mix_share_song),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(0.6f).heightIn(min = 66.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    shape = CircleShape,
                    enabled = track.isPlayable,
                    onClick = onAddToQueue,
                ) {
                    DailyMixSymbol(symbol = "queue_music", iconSlot = "queue.add")
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.home_daily_mix_add_to_queue),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = DailyMixRoundedSans),
                    )
                }
                FilledTonalButton(
                    modifier = Modifier.weight(0.4f).heightIn(min = 66.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    shape = CircleShape,
                    enabled = track.isPlayable,
                    onClick = onPlayNext,
                ) {
                    DailyMixSymbol(symbol = "queue_music", iconSlot = "queue.playNext", filled = true)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.home_daily_mix_next),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = DailyMixRoundedSans),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(0.5f).heightIn(min = 66.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    shape = CircleShape,
                    enabled = false,
                    onClick = {},
                ) {
                    DailyMixSymbol(symbol = "playlist_add", iconSlot = "playback.playlistAdd")
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.home_daily_mix_playlist),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = DailyMixRoundedSans),
                    )
                }
                FilledTonalButton(
                    modifier = Modifier.weight(0.5f).heightIn(min = 66.dp),
                    shape = CircleShape,
                    enabled = false,
                    onClick = onDownload,
                ) {
                    DailyMixSymbol(
                        symbol = "cloud_download",
                        iconSlot = "action.download",
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.home_daily_mix_download_track),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = DailyMixRoundedSans),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun HomeDailyMixInfoPage(track: HomeTrack, genres: List<String>?) {
    val context = LocalContext.current
    val selectedVariant = track.availableVariants.firstOrNull {
        it.backendTrackId == track.backendTrackId
    } ?: track.availableVariants.firstOrNull {
        it.format.equals(track.codec, ignoreCase = true)
    } ?: track.availableVariants.firstOrNull()
    val songMetadata = listOfNotNull(
        track.codec?.takeIf(String::isNotBlank)?.uppercase(),
        selectedVariant?.fileSizeBytes?.let { Formatter.formatShortFileSize(context, it) },
    ).joinToString(" · ")
    val genreText = when {
        genres == null -> stringResource(R.string.home_daily_mix_loading)
        genres.isEmpty() -> stringResource(R.string.home_daily_mix_metadata_unavailable)
        else -> genres.joinToString(" · ")
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HomeDailyMixInfoItem(
                    headline = stringResource(R.string.home_daily_mix_duration),
                    supporting = formatDailyMixDuration(track.durationMs),
                    symbol = "schedule",
                    iconSlot = "metadata.duration",
                )
                if (!track.album.isNullOrBlank()) {
                    HomeDailyMixInfoItem(
                        headline = stringResource(R.string.home_daily_mix_album),
                        supporting = track.album,
                        symbol = "album",
                        iconSlot = "metadata.album",
                    )
                }
                HomeDailyMixInfoItem(
                    headline = stringResource(R.string.home_daily_mix_artist),
                    supporting = track.artist,
                    symbol = "person",
                    iconSlot = "metadata.artist",
                )
                HomeDailyMixInfoItem(
                    headline = stringResource(R.string.home_daily_mix_genre),
                    supporting = genreText,
                    symbol = "local_library",
                    iconSlot = "metadata.album",
                )
                if (songMetadata.isNotBlank()) {
                    HomeDailyMixInfoItem(
                        headline = stringResource(R.string.home_daily_mix_song_metadata),
                        supporting = songMetadata,
                        symbol = "info",
                        iconSlot = "action.info",
                    )
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun HomeDailyMixInfoItem(
    headline: String,
    supporting: String,
    symbol: String,
    iconSlot: String,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                DailyMixSymbol(symbol = symbol, iconSlot = iconSlot)
            },
            content = { Text(headline) },
            supportingContent = { Text(supporting) },
        )
    }
}

@Composable
private fun DailyMixSheetTab(
    index: Int,
    selectedIndex: Int,
    title: String,
    symbol: String,
    iconSlot: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val isSelected = index == selectedIndex
    Tab(
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier
            .padding(5.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
        selectedContentColor = MaterialTheme.colorScheme.onPrimary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DailyMixSymbol(
                    symbol = symbol,
                    iconSlot = iconSlot,
                    filled = filled,
                    modifier = Modifier.padding(horizontal = 4.dp).size(24.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(title, fontFamily = DailyMixRoundedSans, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun DailyMixSymbol(
    symbol: String,
    iconSlot: String,
    filled: Boolean = false,
    contentDescription: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) {
    Icon(
        painter = materialSymbolPainterResource(name = symbol, slot = iconSlot, filled = filled),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

private fun shareAppleMusicTrack(context: Context, track: HomeTrack) {
    val trackId = track.providerTrackId?.trim()?.takeIf { id ->
        id.isNotEmpty() && id.all(Char::isDigit)
    } ?: return
    val slug = Uri.encode(track.title.trim().replace(Regex("\\s+"), "-")).ifBlank { "song" }
    val url = "https://music.apple.com/us/song/$slug/$trackId"
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, track.title)
        putExtra(Intent.EXTRA_TEXT, "${track.title}\n$url")
    }
    try {
        context.startActivity(
            Intent.createChooser(
                sendIntent,
                context.getString(R.string.home_daily_mix_share_song),
            ),
        )
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
}

@Composable
private fun DailyMixAutoSizingText(
    text: String,
    modifier: Modifier,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var fontSize by remember { mutableStateOf(8.sp) }
    var isMeasured by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.toInt()
        val maxHeightPx = with(density) { maxHeight.toPx() }.toInt()
        LaunchedEffect(text, maxWidthPx, maxHeightPx) {
            var low = 8f
            var high = 100f
            var best = low
            repeat(15) {
                val candidate = (low + high) / 2f
                val result = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        fontSize = candidate.sp,
                        lineHeight = (candidate * 1.2f).sp,
                    ),
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                    constraints = Constraints(
                        maxWidth = maxWidthPx.coerceAtLeast(0),
                        maxHeight = maxHeightPx.coerceAtLeast(0),
                    ),
                )
                if (result.hasVisualOverflow) high = candidate else {
                    best = candidate
                    low = candidate
                }
            }
            fontSize = best.sp
            isMeasured = true
        }
        if (isMeasured) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    fontSize = fontSize,
                    lineHeight = (fontSize.value * 1.2f).sp,
                ),
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatDailyMixDuration(durationMs: Long?): String {
    val seconds = ((durationMs ?: 0L).coerceAtLeast(0L) + 500L) / 1000L
    return (seconds / 60L).toString() + ":" + (seconds % 60L).toString().padStart(2, '0')
}

@OptIn(ExperimentalTextApi::class)
@Composable
internal fun rememberDailyMixTitleStyle(): TextStyle = remember {
    TextStyle(
        fontFamily = FontFamily(
            Font(
                resId = R.font.gsans_flex_full,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(630),
                    FontVariation.width(136f),
                    FontVariation.grade(40),
                    FontVariation.Setting("ROND", 100f),
                    FontVariation.Setting("XTRA", 520f),
                    FontVariation.Setting("YOPQ", 90f),
                    FontVariation.Setting("YTLC", 505f),
                ),
            ),
        ),
        fontWeight = FontWeight(630),
        fontSize = 20.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.35).sp,
    )
}


@Composable
internal fun HomeSongRow(
    track: HomeTrack,
    onTrackClick: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playable = track.isPlayable
    val enabled = playable || !track.providerTrackId.isNullOrBlank()
    val action = {
        if (playable) onTrackClick(track) else if (enabled) onDownloadTrack(track)
    }
    val rowShape = MaterialTheme.shapes.medium
    Surface(
        color = Color.Transparent,
        shape = rowShape,
        modifier = modifier.fillMaxWidth().clip(rowShape).clickable(
            enabled = enabled,
            role = Role.Button,
            onClickLabel = stringResource(
                if (playable) R.string.home_play_track else R.string.home_download_track,
                track.title,
                track.artist,
            ),
            onClick = action,
        ),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 76.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeArtwork(
                artworkUrl = track.artworkUrl,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TrackCodecBadges(track = track, height = 12.dp)
            }
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (playable) R.drawable.ic_play else R.drawable.ic_cloud_download),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun HomeTrackPills(
    title: String?,
    tracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
    onDownloadTrack: (HomeTrack) -> Unit,
    subtitle: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val columns = remember(tracks) { tracks.chunked(3) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (title != null) {
            HomeSectionHeading(title, Modifier.padding(horizontal = 24.dp), subtitle)
        }
        LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(columns, key = { it.first().id }) { column ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    column.forEach { track ->
                        val enabled = track.isPlayable || !track.providerTrackId.isNullOrBlank()
                        Surface(
                            onClick = {
                                if (track.isPlayable) onTrackClick(track) else onDownloadTrack(track)
                            },
                            enabled = enabled,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 64.dp)
                                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HomeArtwork(track.artworkUrl, Modifier.size(40.dp).clip(CircleShape))
                                Column(modifier = Modifier.width(136.dp)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmallEmphasized,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    painter = painterResource(
                                        if (track.isPlayable) R.drawable.ic_play else R.drawable.ic_cloud_download,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
