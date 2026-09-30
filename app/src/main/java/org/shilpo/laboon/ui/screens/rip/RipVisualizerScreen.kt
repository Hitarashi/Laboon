@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.rip

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.AuthSession
import org.shilpo.laboon.playback.ArtworkUrlHelper
import org.shilpo.laboon.rip.RipTaskDownloadLane
import org.shilpo.laboon.rip.RipTaskSnapshot
import org.shilpo.laboon.rip.RipTaskUploadLane
import org.shilpo.laboon.rip.RipWebSocketClient
import org.shilpo.laboon.ui.design.CodecIcon
import org.shilpo.laboon.ui.design.ProviderIcon
import org.shilpo.laboon.ui.design.UserAvatar
import java.util.Locale

@Composable
fun RipVisualizerScreen(
    client: RipWebSocketClient,
    session: AuthSession?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by client.state.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron),
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .rotate(90f),
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.home_title),
                                    style = MaterialTheme.typography.titleLargeEmphasized.copy(
                                        fontSize = 25.sp,
                                        lineHeight = 30.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Live pipeline",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 14.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        UserAvatar(
                            session = session,
                            onClick = null,
                            size = 48.dp,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }

            val orderedTasks = remember(state.activeTasks) {
                state.activeTasks.asReversed()
                    .sortedByDescending { it.download != null || it.upload != null }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                if (orderedTasks.isEmpty()) {
                    item {
                        EmptyTasksState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp)
                        )
                    }
                } else {
                    itemsIndexed(
                        items = orderedTasks,
                        key = { _, task -> task.taskId },
                    ) { index, task ->
                        RipTaskSegmentedItem(
                            task = task,
                            serverUrl = state.serverUrl,
                            onCancel = { client.cancelTask(task.taskId) },
                            index = index,
                            count = orderedTasks.size,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RipTaskSegmentedItem(
    task: RipTaskSnapshot,
    serverUrl: String,
    onCancel: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(
            index = index,
            count = count,
        ),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier.fillMaxWidth(),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val artUrl =
                        if (task.resultTrackId != null && task.resultTrackId > 0 && serverUrl.isNotEmpty()) {
                            "$serverUrl/api/v1/assets/tracks/${task.resultTrackId}/artwork"
                        } else if (serverUrl.isNotEmpty() && task.sourceTrackId.isNotEmpty()) {
                            "$serverUrl/api/v1/assets/providers/${task.provider}/tracks/${task.sourceTrackId}/artwork"
                        } else null

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artUrl?.let { ArtworkUrlHelper.toLowQuality(it) })
                            .crossfade(true)
                            .build(),
                        placeholder = painterResource(R.drawable.app_icon_small),
                        error = painterResource(R.drawable.app_icon_small),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        val decodedTitle = remember(task.title) {
                            cleanHtmlEntities(task.title) ?: "Resolving Track..."
                        }
                        Text(
                            text = decodedTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitleText = remember(task.artist, task.album, task.title) {
                            when {
                                !task.artist.isNullOrBlank() -> cleanHtmlEntities(task.artist)
                                !task.album.isNullOrBlank() && task.album != task.title -> cleanHtmlEntities(
                                    task.album
                                )

                                else -> null
                            }
                        }
                        if (!subtitleText.isNullOrBlank()) {
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        val failedCount = task.failedTracks
                        if (failedCount != null && failedCount > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$failedCount failed",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    if (task.isOwner) {
                        Surface(
                            onClick = onCancel,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_clear),
                                    contentDescription = "Cancel task",
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                    ),
                                )
                            }
                        }
                    }
                }

                val hasLanes = task.download != null || task.upload != null
                if (hasLanes) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(
                        thickness = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    task.download?.let { lane ->
                        LaneGauge(
                            lane = lane,
                            task = task,
                        )
                    }

                    if (task.download != null && task.upload != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    task.upload?.let { lane ->
                        UploadLaneGauge(
                            lane = lane,
                            task = task,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun LaneGauge(
    lane: RipTaskDownloadLane,
    task: RipTaskSnapshot,
    modifier: Modifier = Modifier,
) {
    val trackPosition = if (lane.trackIndex != null) {
        val total = lane.totalTracks ?: task.totalTracks
        if (total != null && total > 0) {
            "${lane.trackIndex}/$total"
        } else {
            "${lane.trackIndex}"
        }
    } else if (task.isAlbum && task.currentTrackIndex != null) {
        if (task.totalTracks != null && task.totalTracks > 0) {
            "${task.currentTrackIndex}/${task.totalTracks}"
        } else {
            "${task.currentTrackIndex}"
        }
    } else null

    val trackTitle = lane.title ?: task.currentTrackTitle ?: if (task.isAlbum) null else task.title

    LaneProgressIndicator(
        isDownload = true,
        stage = lane.stage,
        bytesDone = lane.bytesDone,
        bytesTotal = lane.bytesTotal,
        percent = lane.percent,
        speedBytesPerSec = lane.speedBytesPerSec,
        trackTitle = trackTitle,
        trackPosition = trackPosition,
        provider = task.provider,
        codec = lane.codec,
        modifier = modifier,
    )
}

@Composable
private fun UploadLaneGauge(
    lane: RipTaskUploadLane,
    task: RipTaskSnapshot,
    modifier: Modifier = Modifier,
) {
    val isArchiveStage = lane.stage.contains("archive", ignoreCase = true)
    val trackPosition = if (!isArchiveStage && lane.trackIndex != null) {
        val total = lane.totalTracks ?: task.totalTracks
        if (total != null && total > 0) {
            "${lane.trackIndex}/$total"
        } else {
            "${lane.trackIndex}"
        }
    } else if (task.isAlbum && !isArchiveStage && task.currentTrackIndex != null) {
        if (task.totalTracks != null && task.totalTracks > 0) {
            "${task.currentTrackIndex}/${task.totalTracks}"
        } else {
            "${task.currentTrackIndex}"
        }
    } else null

    val trackTitle = if (isArchiveStage) {
        if (!lane.title.isNullOrBlank() && lane.title != "Album ZIP archive" && lane.title != "Full Album Archive") {
            lane.title
        } else if (!task.artist.isNullOrBlank() && !task.title.isNullOrBlank()) {
            val codecSuffix = lane.codec?.uppercase(Locale.getDefault())?.let { " [$it]" } ?: ""
            "${task.artist} - ${task.title}$codecSuffix.zip"
        } else if (!task.title.isNullOrBlank()) {
            "${task.title}.zip"
        } else {
            "Album.zip"
        }
    } else {
        lane.title ?: (task.currentTrackTitle ?: if (task.isAlbum) null else task.title)
    }

    LaneProgressIndicator(
        isDownload = false,
        stage = lane.stage,
        bytesDone = lane.bytesDone,
        bytesTotal = lane.bytesTotal,
        percent = lane.percent,
        speedBytesPerSec = lane.speedBytesPerSec,
        trackTitle = trackTitle,
        trackPosition = trackPosition,
        provider = task.provider,
        codec = lane.codec,
        modifier = modifier,
    )
}

@Composable
private fun LaneProgressIndicator(
    isDownload: Boolean,
    stage: String,
    bytesDone: Long?,
    bytesTotal: Long?,
    percent: Float?,
    speedBytesPerSec: Long,
    trackTitle: String? = null,
    trackPosition: String? = null,
    provider: String? = null,
    codec: String? = null,
    modifier: Modifier = Modifier,
) {
    val progress = (percent ?: 0f) / 100f
    val animatedPercent by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(250),
        label = if (isDownload) "p_down" else "p_up",
    )
    val accentColor =
        if (isDownload) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val normalizedStage = stage.lowercase(Locale.getDefault())
            val (stageIconRes, stageIconModifier) = when {
                normalizedStage.contains("connect") -> {
                    R.drawable.ic_stage_link to Modifier.size(14.dp)
                }

                normalizedStage.contains("archive") || normalizedStage.contains("bundle") -> {
                    R.drawable.ic_stage_archive to Modifier.size(14.dp)
                }

                normalizedStage.contains("decrypt") -> {
                    R.drawable.ic_stage_decrypt to Modifier.size(14.dp)
                }

                normalizedStage.contains("tag") -> {
                    R.drawable.ic_stage_tag to Modifier.size(14.dp)
                }

                normalizedStage.contains("resolv") || normalizedStage.contains("check") -> {
                    R.drawable.ic_stage_resolving to Modifier.size(14.dp)
                }

                normalizedStage.contains("cached") || normalizedStage.contains("materializ") -> {
                    R.drawable.ic_stage_flash to Modifier.size(14.dp)
                }

                normalizedStage.contains("queue") || normalizedStage.contains("wait") -> {
                    R.drawable.ic_stage_hourglass to Modifier.size(14.dp)
                }

                else -> {
                    R.drawable.ic_chevron_double to Modifier
                        .size(14.dp)
                        .then(if (!isDownload) Modifier.rotate(180f) else Modifier)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Icon(
                    painter = painterResource(stageIconRes),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = stageIconModifier,
                )
                Text(
                    text = humanReadableStage(stage),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val isTransferStage = stage.contains("download", ignoreCase = true) ||
                    stage.contains("upload", ignoreCase = true)
            if (speedBytesPerSec > 0 || isTransferStage) {
                Text(
                    text = speedToHumanReadable(speedBytesPerSec),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = accentColor,
                )
            }
        }

        val hasTrackInfo =
            !trackTitle.isNullOrBlank() || !trackPosition.isNullOrBlank() || !provider.isNullOrBlank() || !codec.isNullOrBlank()
        if (hasTrackInfo) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!trackPosition.isNullOrBlank()) {
                    Text(
                        text = trackPosition,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = accentColor,
                    )
                }
                if (!trackPosition.isNullOrBlank() && !trackTitle.isNullOrBlank()) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                if (!trackTitle.isNullOrBlank()) {
                    Text(
                        text = cleanHtmlEntities(trackTitle) ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                val hasIcons = !provider.isNullOrBlank() || !codec.isNullOrBlank()
                if ((!trackPosition.isNullOrBlank() || !trackTitle.isNullOrBlank()) && hasIcons) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                if (!provider.isNullOrBlank()) {
                    ProviderIcon(
                        provider = provider,
                        height = 10.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
                if (!codec.isNullOrBlank()) {
                    CodecIcon(
                        codec = codec,
                        height = 10.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    if (!isLosslessOrDolby(codec)) {
                        val displayCodec = when (codec.lowercase(Locale.getDefault())) {
                            "mp4a.40.2", "mp4a.40.5" -> "AAC"
                            else -> codec.uppercase(Locale.getDefault())
                        }
                        Text(
                            text = displayCodec,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { animatedPercent },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            strokeCap = StrokeCap.Round,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val bytesText = if (bytesDone != null && bytesTotal != null) {
                "${bytesToHumanReadable(bytesDone)} / ${bytesToHumanReadable(bytesTotal)}"
            } else if (bytesDone != null) {
                bytesToHumanReadable(bytesDone)
            } else {
                if (isDownload) "Processing..." else "Uploading..."
            }

            Text(
                text = bytesText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun EmptyTasksState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_song_wave),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No active tasks",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun isLosslessOrDolby(codec: String?): Boolean {
    val c = codec?.trim()?.lowercase(Locale.getDefault()) ?: return false
    return c in listOf("lossless", "alac", "flac", "ec-3", "ec3", "atmos", "dolby", "dolby_atmos")
}

private fun humanReadableStage(stage: String): String =
    when (stage.lowercase(Locale.getDefault())) {
        "resolving_metadata" -> "Resolving Metadata"
        "connecting" -> "Connecting to CDN"
        "downloading" -> "Downloading Audio"
        "decrypting" -> "Decrypting DRM"
        "tagging" -> "Embedding ID3 Tags"
        "cached_delivery" -> "Cache Hit Delivery"
        "materializing_cached_media" -> "Materializing Media"
        "uploading_track" -> "Uploading to Telegram"
        "building_archive", "bundling_album" -> "Bundling Album"
        "uploading_archive" -> "Uploading Archive"
        "checking_cache" -> "Checking Cache"
        "queued" -> "Queued in Engine"
        "processing_next" -> "Advancing Track"
        "waiting_duplicate" -> "Coalescing Duplicate"
        else -> stage.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

private fun bytesToHumanReadable(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

private fun speedToHumanReadable(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_000_000.0)
    bytesPerSec >= 1_000 -> String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1_000.0)
    bytesPerSec > 0 -> "$bytesPerSec B/s"
    else -> "0 KB/s"
}

private fun cleanHtmlEntities(text: String?): String? {
    if (text == null) return null
    var cleaned = text.trim()
    if (cleaned.startsWith("Album: ", ignoreCase = true)) {
        cleaned = cleaned.substring("Album: ".lenSafe()).trim()
    }
    if (!cleaned.contains("&")) return cleaned
    return try {
        HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    } catch (_: Exception) {
        cleaned
    }
}

private fun String.lenSafe(): Int = length.coerceAtMost(7)
