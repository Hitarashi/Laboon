package org.shilpo.laboon.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.theme.RoundedSans

private const val ESCAPE_SEQUENCE = "\\\\"
private const val ESCAPE_PLACEHOLDER = "\u0000ESCAPED\u0000"
private val DEFAULT_ARTIST_WORD_DELIMITERS = listOf(
    "featuring", "feat.", "feat", "ft.", "ft", "vs.", "vs", "versus", "with", "prod.", "prod"
)
private val DEFAULT_ARTIST_CHAR_DELIMITERS = listOf("/", ";", ",", "&", "、")

internal fun String.splitArtistsByDelimiters(
    delimiters: List<String> = DEFAULT_ARTIST_CHAR_DELIMITERS,
    wordDelimiters: List<String> = DEFAULT_ARTIST_WORD_DELIMITERS,
): List<String> {
    if ((delimiters.isEmpty() && wordDelimiters.isEmpty()) || this.isBlank()) {
        return listOf(this.trim()).filter { it.isNotEmpty() }
    }

    val sortedDelimiters = delimiters.sortedByDescending { it.length }

    var working = this

    val escapedMappings = mutableMapOf<String, String>()
    sortedDelimiters.forEachIndexed { index, delimiter ->
        val placeholder = "${ESCAPE_PLACEHOLDER}${index}${ESCAPE_PLACEHOLDER}"
        escapedMappings[placeholder] = delimiter
        working = working.replace(ESCAPE_SEQUENCE + delimiter, placeholder)
        working = working.replace("\\$delimiter", placeholder)
    }

    val patternParts = mutableListOf<String>()

    val sortedWordDelimiters = wordDelimiters.sortedByDescending { it.length }
    for (wd in sortedWordDelimiters) {
        val escaped = Regex.escape(wd)
        if (wd.length == 1) {
            patternParts.add("\\s+$escaped\\s+")
        } else {
            patternParts.add("\\s+$escaped\\s+|\\s+$escaped$|^$escaped\\s+")
        }
    }

    if (sortedDelimiters.isNotEmpty()) {
        val charPattern = sortedDelimiters.joinToString("|") { Regex.escape(it) }
        patternParts.add(charPattern)
    }

    if (patternParts.isEmpty()) {
        return listOf(this.trim()).filter { it.isNotEmpty() }
    }

    val combinedPattern = patternParts.joinToString("|")
    val regex = Regex(combinedPattern, RegexOption.IGNORE_CASE)

    val parts = working.split(regex)

    return parts
        .map { part ->
            var restored = part
            escapedMappings.forEach { (placeholder, delimiter) ->
                restored = restored.replace(placeholder, delimiter)
            }
            restored.trim()
        }
        .filter { it.isNotEmpty() }
        .distinct()
        .ifEmpty { if (this.trim().isNotEmpty()) listOf(this.trim()) else emptyList() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerArtistPickerBottomSheet(
    artists: List<String>,
    sheetState: SheetState,
    useRoundedTypography: Boolean,
    onDismiss: () -> Unit,
    onArtistClick: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        },
        containerColor = colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.artist_picker_title),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = if (useRoundedTypography) RoundedSans else MaterialTheme.typography.headlineMedium.fontFamily,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                artists.forEachIndexed { index, artistName ->
                    PlayerArtistShortcutCard(
                        artistName = artistName,
                        isPrimary = index == 0,
                        useRoundedTypography = useRoundedTypography,
                        shape = artistShortcutShape(
                            index = index,
                            count = artists.size,
                        ),
                        onClick = { onArtistClick(artistName) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun PlayerArtistShortcutCard(
    artistName: String,
    isPrimary: Boolean,
    useRoundedTypography: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (isPrimary) {
        colorScheme.secondaryContainer
    } else {
        colorScheme.surfaceContainerLow
    }
    val contentColor = if (isPrimary) {
        colorScheme.onSecondaryContainer
    } else {
        colorScheme.onSurface
    }
    val labelContainerColor = if (isPrimary) {
        colorScheme.tertiary
    } else {
        colorScheme.surfaceContainerHighest
    }
    val labelContentColor = if (isPrimary) {
        colorScheme.onTertiary
    } else {
        colorScheme.onSurfaceVariant
    }
    val avatarBackground = if (isPrimary) {
        colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
    } else {
        colorScheme.surfaceContainerHighest
    }
    val trailingContainerColor = contentColor.copy(alpha = 0.12f)
    val avatarSize = 52.dp

    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(avatarBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = materialSymbolPainterResource("person", filled = true),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = if (useRoundedTypography) RoundedSans else MaterialTheme.typography.titleMedium.fontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Surface(
                    color = labelContainerColor,
                    shape = CircleShape,
                ) {
                    Text(
                        text = stringResource(
                            if (isPrimary) {
                                R.string.artist_picker_primary_label
                            } else {
                                R.string.artist_picker_shortcut_label
                            }
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = if (useRoundedTypography) RoundedSans else MaterialTheme.typography.labelMedium.fontFamily,
                        color = labelContentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(trailingContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = materialSymbolPainterResource("arrow_forward"),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

internal fun artistShortcutShape(
    index: Int,
    count: Int,
): RoundedCornerShape {
    val outerCorner = 26.dp
    val innerCorner = 10.dp
    return when {
        count <= 1 -> RoundedCornerShape(outerCorner)
        index == 0 -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = innerCorner,
            bottomEnd = innerCorner,
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner,
        )
        else -> RoundedCornerShape(innerCorner)
    }
}
