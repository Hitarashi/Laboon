@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// 24dp is a deliberate app value; M3's nearest roles are largeIncreased (20dp) and
// extraLarge (28dp), so it is declared here rather than approximated by a token.
val AppCardShape = RoundedCornerShape(24.dp)

@Composable
fun PlaceholderCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = AppCardShape,
            colors = colors,
        ) {
            PlaceholderCardBody(title = title, subtitle = subtitle)
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = AppCardShape,
            colors = colors,
        ) {
            PlaceholderCardBody(title = title, subtitle = subtitle)
        }
    }
}

@Composable
private fun ColumnScope.PlaceholderCardBody(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
