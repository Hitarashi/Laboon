package org.shilpo.laboon.ui.screens.library

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.theme.renderer.materialSymbolPainterResource
import org.shilpo.laboon.ui.design.theme.RoundedSans

internal enum class SongsLibrarySortOption(val label: Int) {
    DEFAULT_ORDER(R.string.library_sort_default_order),
    TITLE(R.string.library_sort_title),
    ARTIST(R.string.library_sort_artist),
    ALBUM(R.string.library_sort_album),
    DURATION(R.string.library_sort_duration),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongsLibrarySortBottomSheet(
    selectedOption: SongsLibrarySortOption,
    isDescending: Boolean,
    onDismiss: () -> Unit,
    onOptionSelected: (SongsLibrarySortOption) -> Unit,
    onDirectionToggle: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val selectedColor = MaterialTheme.colorScheme.secondaryContainer
    val unselectedColor = MaterialTheme.colorScheme.surfaceContainerLow

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.library_sort_by),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = RoundedSans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, bottom = 16.dp),
            )

            SongsSortDirectionCard(
                modifier = Modifier.padding(bottom = 12.dp),
                isDescending = isDescending,
                enabled = selectedOption != SongsLibrarySortOption.DEFAULT_ORDER,
                onClick = onDirectionToggle,
            )

            Column(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SongsLibrarySortOption.entries.forEach { option ->
                    val isSelected = selectedOption == option
                    Surface(
                        color = if (isSelected) selectedColor else unselectedColor,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onOptionSelected(option) },
                                role = Role.RadioButton,
                            )
                            .semantics { selected = isSelected },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(option.label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            RadioButton(selected = isSelected, onClick = null)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SongsSortDirectionCard(
    modifier: Modifier = Modifier,
    isDescending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val transition = updateTransition(targetState = isDescending, label = "songsSortDirection")
    val containerColor by transition.animateColor(
        transitionSpec = {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "songsSortDirectionContainer",
    ) { descending ->
        when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerLow
            descending -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }
    }
    val contentColor by transition.animateColor(
        transitionSpec = {
            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "songsSortDirectionContent",
    ) { descending ->
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            descending -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
    }
    val iconContainerColor by animateColorAsState(
        targetValue = contentColor.copy(alpha = if (enabled) 0.16f else 0.1f),
        label = "songsSortDirectionIconContainer",
    )
    val iconRotation by transition.animateFloat(
        transitionSpec = {
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
        },
        label = "songsSortDirectionIconRotation",
    ) { descending -> if (descending) 0f else 180f }
    val iconScale by transition.animateFloat(
        transitionSpec = {
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "songsSortDirectionIconScale",
    ) { descending -> if (descending) 1f else 1.08f }
    val directionLabel = when {
        !enabled -> R.string.library_sort_original_order
        isDescending -> R.string.library_sort_descending
        else -> R.string.library_sort_ascending
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.72f)
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = materialSymbolPainterResource("arrow_downward"),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(iconRotation)
                        .scale(iconScale),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.library_sort_order),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor.copy(alpha = 0.82f),
                )
                Text(
                    text = stringResource(directionLabel),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
            }
        }
    }
}
