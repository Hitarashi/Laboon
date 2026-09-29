@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> SegmentedSection(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    leadingContent: (@Composable (item: T) -> Unit)? = null,
    supportingContent: (@Composable (item: T) -> Unit)? = null,
    trailingContent: (@Composable (item: T) -> Unit)? = null,
    content: @Composable (item: T) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized.copy(
                color = MaterialTheme.colorScheme.primary,
            ),
            modifier = titleModifier,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            items.forEachIndexed { index, item ->
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = items.size,
                    ),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = leadingContent?.let { slot -> @Composable { slot(item) } },
                    content = { content(item) },
                    supportingContent = supportingContent?.let { slot -> @Composable { slot(item) } },
                    trailingContent = trailingContent?.let { slot -> @Composable { slot(item) } },
                )
            }
        }
    }
}
