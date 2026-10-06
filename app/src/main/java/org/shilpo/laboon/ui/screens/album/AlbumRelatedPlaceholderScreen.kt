@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.shilpo.laboon.ui.screens.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shilpo.laboon.R

internal sealed interface AlbumRelatedDestination {
    val name: String
    val title: String
    val identity: String

    data class Artist(override val name: String) : AlbumRelatedDestination {
        override val title: String = "Artist"
        override val identity: String = "ARTIST"
    }

    data class RecordLabel(override val name: String) : AlbumRelatedDestination {
        override val title: String = "Record label"
        override val identity: String = "RECORD LABEL"
    }
}

internal fun albumArtistDestination(name: String?): AlbumRelatedDestination? =
    name?.trim()?.takeIf(String::isNotEmpty)?.let { AlbumRelatedDestination.Artist(it) }

internal fun albumRecordLabelDestination(name: String?): AlbumRelatedDestination? =
    name?.trim()?.takeIf(String::isNotEmpty)?.let { AlbumRelatedDestination.RecordLabel(it) }

@Composable
internal fun AlbumRelatedPlaceholderScreen(
    destination: AlbumRelatedDestination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = TopAppBarDefaults.windowInsets,
        topBar = {
            TopAppBar(
                title = { Text(destination.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = "Back to album",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(90f),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = destination.identity,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = destination.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Coming soon",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
