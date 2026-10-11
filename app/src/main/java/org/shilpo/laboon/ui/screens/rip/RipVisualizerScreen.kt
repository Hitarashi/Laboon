package org.shilpo.laboon.ui.screens.rip

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import org.shilpo.laboon.rip.RipWsStatus
import org.shilpo.laboon.ui.design.UserAvatar
import org.shilpo.laboon.ui.design.painterResource
import java.util.Locale

@Composable
fun RipVisualizerScreen(
    client: RipWebSocketClient,
    session: AuthSession?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomClearance: Dp = 0.dp,
) {
    val state by client.state.collectAsState()

    val orderedTasks = remember(state.activeTasks) {
        state.activeTasks.asReversed()
            .sortedByDescending { it.download != null || it.upload != null }
    }

    val totalDownloadSpeed = remember(state.activeTasks) {
        state.activeTasks.sumOf { it.download?.speedBytesPerSec ?: 0L }
    }
    val totalUploadSpeed = remember(state.activeTasks) {
        state.activeTasks.sumOf { it.upload?.speedBytesPerSec ?: 0L }
    }

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp),
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

                        Text(
                            text = "Live Pipeline",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                letterSpacing = (-0.3).sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        LiveConnectionBadge(status = state.wsStatus)
                    }

                    UserAvatar(
                        session = session,
                        onClick = null,
                        size = 38.dp,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = bottomClearance + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (orderedTasks.isNotEmpty()) {
                    item(key = "telemetry_summary") {
                        GlobalTelemetryPill(
                            taskCount = orderedTasks.size,
                            downloadSpeed = totalDownloadSpeed,
                            uploadSpeed = totalUploadSpeed,
                        )
                    }

                    itemsIndexed(
                        items = orderedTasks,
                        key = { _, task -> task.taskId },
                    ) { _, task ->
                        RipTaskCard(
                            task = task,
                            onCancel = { client.cancelTask(task.taskId) },
                        )
                    }
                } else {
                    item(key = "empty_state") {
                        ModernEmptyState(
                            wsStatus = state.wsStatus,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveConnectionBadge(
    status: RipWsStatus,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val (statusColor, statusText, isPulsing) = when (status) {
        RipWsStatus.CONNECTED -> Triple(Color(0xFF22C55E), "LIVE", true)
        RipWsStatus.CONNECTING -> Triple(Color(0xFFF59E0B), "CONNECTING", true)
        RipWsStatus.DISCONNECTED -> Triple(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), "OFFLINE", false)
        RipWsStatus.ERROR -> Triple(MaterialTheme.colorScheme.error, "ERROR", false)
    }

    Surface(
        shape = CircleShape,
        color = statusColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.25f)),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = if (isPulsing) pulseAlpha else 1f)),
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = statusColor,
            )
        }
    }
}

@Composable
private fun GlobalTelemetryPill(
    taskCount: Int,
    downloadSpeed: Long,
    uploadSpeed: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = if (taskCount == 1) "1 Active Task" else "$taskCount Active Tasks",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "↓",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = speedToHumanReadable(downloadSpeed),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Box(
                    modifier = Modifier
                        .height(12.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "↑",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        ),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = speedToHumanReadable(uploadSpeed),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun RipTaskCard(
    task: RipTaskSnapshot,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(ArtworkUrlHelper.toLowQuality(task.artworkUrl))
                        .crossfade(true)
                        .build(),
                    placeholder = painterResource(R.drawable.app_icon_small),
                    error = painterResource(R.drawable.app_icon_small),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val decodedTitle = remember(task.title) {
                        cleanHtmlEntities(task.title) ?: "Resolving Track..."
                    }
                    Text(
                        text = decodedTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val subtitleText = remember(task.artist, task.album, task.title) {
                        when {
                            !task.artist.isNullOrBlank() -> cleanHtmlEntities(task.artist)
                            !task.album.isNullOrBlank() && task.album != task.title -> cleanHtmlEntities(task.album)
                            else -> null
                        }
                    }
                    if (!subtitleText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (task.isAlbum) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val albumProgressText = if (task.currentTrackIndex != null && task.totalTracks != null) {
                            "Album • Track ${task.currentTrackIndex} of ${task.totalTracks}"
                        } else {
                            "Album"
                        }
                        Text(
                            text = albumProgressText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.primary,
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
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
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
                                modifier = Modifier.size(11.dp),
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
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                )
                Spacer(modifier = Modifier.height(10.dp))

                task.download?.let { lane ->
                    LaneGauge(
                        lane = lane,
                        task = task,
                    )
                }

                if (task.download != null && task.upload != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                task.upload?.let { lane ->
                    UploadLaneGauge(
                        lane = lane,
                        task = task,
                    )
                }
            }
        }
    }
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
            "${task.artist} - ${task.title}.zip"
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
            val normalizedStage = stage.lowercase(Locale.ROOT)
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
                val arrowPrefix = if (isDownload) "↓ " else "↑ "
                Text(
                    text = arrowPrefix + speedToHumanReadable(speedBytesPerSec),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = accentColor,
                )
            }
        }

        val hasTrackInfo = !trackTitle.isNullOrBlank() || !trackPosition.isNullOrBlank()
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
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModernEmptyState(
    wsStatus: RipWsStatus,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = Modifier.size(80.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_song_wave),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Pipeline Idle",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "No active downloads or uploads. Tracks and albums dispatched from Search or Home stream live here.",
            style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.85f),
        )

        Spacer(modifier = Modifier.height(18.dp))

        val (dotColor, statusMsg) = when (wsStatus) {
            RipWsStatus.CONNECTED -> Color(0xFF22C55E) to "Connected to pipeline backend"
            RipWsStatus.CONNECTING -> Color(0xFFF59E0B) to "Connecting to pipeline backend…"
            RipWsStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) to "Pipeline server offline"
            RipWsStatus.ERROR -> MaterialTheme.colorScheme.error to "Pipeline connection error"
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Text(
                    text = statusMsg,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
