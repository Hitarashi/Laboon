package org.shilpo.laboon.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.home.TrackFormatVariant
import org.shilpo.laboon.playback.AudioQualityInfo
import java.util.Locale

private enum class AudioQualityDetailState {
    Idle,
    Loading,
    Locked,
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun AudioQualityBadge(
    quality: AudioQualityInfo?,
    fallbackCodec: String? = null,
    track: HomeTrack? = null,
    availableVariants: List<TrackFormatVariant> = emptyList(),
    switchingFormat: String? = null,
    onVariantSelected: ((TrackFormatVariant) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    showTrackInfo: Boolean = true,
) {
    val isStale = track != null && quality?.trackId != null && quality.trackId != track.id
    val isLocked = quality?.isLocked == true && !isStale
    val showLoading = !isLocked && isLoading
    val detailState = when {
        isLocked -> AudioQualityDetailState.Locked
        showLoading -> AudioQualityDetailState.Loading
        else -> AudioQualityDetailState.Idle
    }
    val effectiveCodec = (if (!isStale) quality?.codec else null) ?: track?.codec ?: fallbackCodec
    val normCodec = effectiveCodec?.trim()?.lowercase(Locale.ROOT)

    val isHiRes = if (!isStale && quality != null) {
        quality.isHiRes
    } else {
        normCodec?.let { it.contains("hires") || it == "hi-res" } == true
    }

    val isLossless = if (!isStale && quality != null) {
        quality.isLossless
    } else {
        normCodec?.let {
            it == "lossless" || it == "alac" || it == "flac" || it.contains("lossless") ||
                    it.contains("hires") || it == "hi-res" || it.contains("24-")
        } == true
    }

    val isDolby = if (!isStale && quality != null) {
        quality.isDolby
    } else {
        normCodec?.let { it.contains("dolby") || it.contains("atmos") || it == "ec-3" || it == "ec3" } == true
    }

    val fallbackText = when {
        isHiRes || isLossless || isDolby -> null
        !quality?.codec.isNullOrBlank() && !isStale -> quality.codec
        !normCodec.isNullOrBlank() -> when {
            normCodec.contains("aac") || normCodec.contains("mp4a") -> "AAC"
            normCodec.contains("opus") -> "OPUS"
            normCodec.contains("mp3") || normCodec.contains("mpeg") -> "MP3"
            else -> effectiveCodec.trim().uppercase(Locale.ROOT)
        }

        else -> null
    }

    if (!isHiRes && !isLossless && !isDolby && fallbackText.isNullOrBlank()) {
        return
    }

    val currentQuality = quality?.takeIf { !isStale && it.isLocked }
    val pipeline = currentQuality?.pipelineDetails
    val bitDepthStr = pipeline?.bitDepth?.takeIf { it != "Float32" }
        ?: currentQuality?.bitDepth?.let { "${it}-bit" }
    val kbpsStr = pipeline?.bitrateKbps?.let { "${it} kbps" }

    val colorScheme = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "audioBadgePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val formatOptions =
        remember(availableVariants, isDolby, isLossless, fallbackText, normCodec, track) {
            val distinct = availableVariants.distinctBy { it.format.lowercase(Locale.ROOT) }
            if (distinct.isNotEmpty()) {
                distinct
            } else {
                val format = when {
                    isDolby -> "ec-3"
                    isLossless -> "alac"
                    !fallbackText.isNullOrBlank() -> fallbackText.lowercase(Locale.ROOT)
                    !normCodec.isNullOrBlank() -> normCodec
                    else -> null
                }
                if (format != null) {
                    listOf(
                        TrackFormatVariant(
                            format = format,
                            backendTrackId = track?.backendTrackId ?: 0
                        )
                    )
                } else {
                    emptyList()
                }
            }
        }
    val isQualityAvailable = showTrackInfo && quality != null && !isStale && quality.isLocked &&
            (!bitDepthStr.isNullOrBlank() || !kbpsStr.isNullOrBlank())

    if (!isQualityAvailable && formatOptions.isEmpty()) {
        return
    }

    val hasMultipleVariants = formatOptions.size > 1 && onVariantSelected != null
    val groupItemCount = formatOptions.size + if (isQualityAvailable) 1 else 0
    val groupInteractionSources = remember(groupItemCount) {
        List(groupItemCount) { MutableInteractionSource() }
    }

    ButtonGroup(
        modifier = modifier,
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        horizontalArrangement = Arrangement.spacedBy(
            space = ButtonGroupDefaults.ConnectedSpaceBetween,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isQualityAvailable) {
            val interactionSource = groupInteractionSources[0]
            customItem(
                buttonGroupContent = {
                    FilledTonalButton(
                        onClick = { onClick?.invoke() },
                        enabled = onClick != null,
                        shapes = groupedButtonShapes(index = 0, count = groupItemCount),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .animateWidth(interactionSource, compressionLimit = 14.dp)
                            .height(42.dp)
                            .semantics { contentDescription = "Audio quality details" },
                        interactionSource = interactionSource,
                    ) {
                        QualityMetricsContent(
                            detailState = AudioQualityDetailState.Locked,
                            bitDepthStr = bitDepthStr,
                            kbpsStr = kbpsStr,
                            isHiRes = isHiRes,
                            pulseAlpha = pulseAlpha,
                            contentColor = colorScheme.onSecondaryContainer,
                        )
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text("Audio quality details") },
                        enabled = onClick != null,
                        onClick = {
                            onClick?.invoke()
                            menuState.dismiss()
                        },
                    )
                },
            )
        }

        formatOptions.forEachIndexed { index, variant ->
            val groupIndex = index + if (isQualityAvailable) 1 else 0
            val interactionSource = groupInteractionSources[groupIndex]
            val isSelected = hasMultipleVariants && (
                    variant.backendTrackId == track?.backendTrackId ||
                            (track?.backendTrackId == null && variant.format.equals(
                                track?.codec,
                                ignoreCase = true
                            ))
                    )
            val isSwitching = switchingFormat.equals(variant.format, ignoreCase = true)
            val isHiResVariant = (isSelected || !hasMultipleVariants) && isHiRes
            val accessibleFormat = if (
                variant.format.equals("alac", ignoreCase = true) && isHiResVariant
            ) {
                "Hi-Res ${formatAccessibleName(variant.format)}"
            } else {
                formatAccessibleName(variant.format)
            }
            customItem(
                buttonGroupContent = {
                    val itemContentColor = if (isSelected) {
                        colorScheme.onSecondary
                    } else {
                        colorScheme.onSecondaryContainer
                    }
                    if (hasMultipleVariants) {
                        FilledTonalToggleButton(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onVariantSelected(variant)
                                }
                            },
                            shapes = groupedToggleButtonShapes(
                                index = groupIndex,
                                count = groupItemCount,
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .animateWidth(interactionSource, compressionLimit = 14.dp)
                                .height(42.dp)
                                .semantics {
                                    contentDescription = "$accessibleFormat quality"
                                },
                            interactionSource = interactionSource,
                        ) {
                            if (isSwitching) {
                                LoadingIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = itemContentColor,
                                )
                            } else if (variant.format.equals("aac", ignoreCase = true)) {
                                Text(
                                    text = "AAC",
                                    color = itemContentColor,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.2.sp,
                                    ),
                                )
                            } else {
                                VariantFormatMark(
                                    format = variant.format,
                                    isHiRes = isHiResVariant,
                                    color = itemContentColor,
                                )
                            }
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { onClick?.invoke() },
                            enabled = onClick != null,
                            shapes = groupedButtonShapes(
                                index = groupIndex,
                                count = groupItemCount,
                            ),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer,
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .animateWidth(interactionSource, compressionLimit = 14.dp)
                                .height(42.dp)
                                .semantics {
                                    contentDescription = "$accessibleFormat quality"
                                },
                            interactionSource = interactionSource,
                        ) {
                            if (isSwitching) {
                                LoadingIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = colorScheme.onSecondaryContainer,
                                )
                            } else if (variant.format.equals("aac", ignoreCase = true)) {
                                Text(
                                    text = "AAC",
                                    color = colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.2.sp,
                                    ),
                                )
                            } else {
                                VariantFormatMark(
                                    format = variant.format,
                                    isHiRes = isHiResVariant,
                                    color = colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text(formatAccessibleName(variant.format)) },
                        leadingIcon = {
                            VariantFormatMark(
                                format = variant.format,
                                isHiRes = isHiResVariant,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            if (hasMultipleVariants && !isSelected) {
                                onVariantSelected(variant)
                            } else {
                                onClick?.invoke()
                            }
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun groupedToggleButtonShapes(index: Int, count: Int) = when {
    count <= 1 -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    index == count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun groupedButtonShapes(index: Int, count: Int): ButtonShapes {
    if (count <= 1) return ButtonDefaults.shapesFor(42.dp)

    val toggleShapes = groupedToggleButtonShapes(index, count)
    return ButtonShapes(
        shape = toggleShapes.shape,
        pressedShape = toggleShapes.pressedShape,
    )
}

@Composable
private fun QualityMetricsContent(
    detailState: AudioQualityDetailState,
    bitDepthStr: String?,
    kbpsStr: String?,
    isHiRes: Boolean = false,
    pulseAlpha: Float,
    contentColor: Color,
) {
    AnimatedContent(
        targetState = detailState,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith
                    fadeOut(animationSpec = tween(160))
        },
        label = "audioQualityTransition",
    ) { state ->
        when (state) {
            AudioQualityDetailState.Locked -> QualityMetricsRow(
                bitDepthStr = bitDepthStr,
                kbpsStr = kbpsStr,
                isHiRes = isHiRes,
                contentColor = contentColor,
            )

            AudioQualityDetailState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.5.dp),
                ) {
                    BadgeDot(contentColor)
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(6.5.dp)
                            .graphicsLayer { alpha = pulseAlpha }
                            .background(
                                color = contentColor.copy(alpha = 0.40f),
                                shape = RoundedCornerShape(percent = 50),
                            ),
                    )
                }
            }

            AudioQualityDetailState.Idle -> QualityMetricsRow(
                bitDepthStr = bitDepthStr,
                kbpsStr = kbpsStr,
                isHiRes = isHiRes,
                contentColor = contentColor,
            )
        }
    }
}

@Composable
private fun QualityMetricsRow(
    bitDepthStr: String?,
    kbpsStr: String?,
    isHiRes: Boolean = false,
    contentColor: Color,
) {
    val hasBitDepth = !bitDepthStr.isNullOrBlank()
    val hasBitrate = !kbpsStr.isNullOrBlank()
    if (!hasBitDepth && !hasBitrate && !isHiRes) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.5.dp),
    ) {
        if (hasBitDepth) {
            Text(
                text = bitDepthStr.orEmpty(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.2.sp,
                ),
                color = contentColor,
            )
        }
        if (hasBitDepth && hasBitrate) BadgeDot(contentColor)
        if (hasBitrate) {
            Text(
                text = kbpsStr.orEmpty(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.2.sp,
                ),
                color = contentColor,
            )
        }
        if (isHiRes) {
            if (hasBitDepth || hasBitrate) BadgeDot(contentColor)
            Icon(
                painter = painterResource(R.drawable.ic_codec_hires),
                contentDescription = "Hi-Res",
                tint = contentColor,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun VariantFormatMark(format: String, isHiRes: Boolean, color: Color) {
    when (format.lowercase(Locale.ROOT)) {
        "alac" -> Icon(
            painter = painterResource(R.drawable.ic_codec_lossless),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(width = 20.dp, height = 12.dp),
        )

        "ec-3", "ec3", "dolby_atmos" -> Icon(
            painter = painterResource(R.drawable.ic_codec_dolby),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(width = 18.dp, height = 13.dp),
        )

        "aac" -> Text(
            text = "AAC",
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.2.sp,
            ),
        )

        else -> Text(
            text = format.uppercase(Locale.ROOT),
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.2.sp,
            ),
        )
    }
}

private fun formatAccessibleName(format: String): String = when (format.lowercase(Locale.ROOT)) {
    "alac" -> "Lossless ALAC"
    "ec-3", "ec3", "dolby_atmos" -> "Dolby Atmos"
    "aac" -> "AAC"
    else -> format.uppercase(Locale.ROOT)
}

@Composable
private fun BadgeDot(color: Color) {
    Box(
        modifier = Modifier
            .size(2.5.dp)
            .background(color.copy(alpha = 0.45f), CircleShape)
    )
}
