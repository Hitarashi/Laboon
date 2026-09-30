@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import org.shilpo.laboon.R
import org.shilpo.laboon.auth.SessionStore
import org.shilpo.laboon.auth.SharedPreferencesKeyValueStore
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.search.SearchRepository
import org.shilpo.laboon.search.SearchRepositoryImpl
import org.shilpo.laboon.ui.design.CodecIcon
import org.shilpo.laboon.ui.design.ProviderIcon

@Composable
fun SearchScreen(
    onTrackClick: (HomeTrack) -> Unit = {},
    onPlayWithContext: ((HomeTrack, List<HomeTrack>) -> Unit)? = null,
    onPlayNext: ((HomeTrack) -> Unit)? = null,
    onAddToQueue: ((HomeTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
    searchRepository: SearchRepository? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val repository = remember(searchRepository, context) {
        searchRepository
            ?: SearchRepositoryImpl(SessionStore(SharedPreferencesKeyValueStore(context)))
    }

    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<HomeTrack>>(emptyList()) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(300)
        results = repository.search(trimmed)
        isSearching = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clear),
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                        )
                    }
                }

                query.isBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.search_placeholder_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.search_placeholder_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No results found for \"$query\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(results, key = { it.id }) { track ->
                            var menuExpanded by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (onPlayWithContext != null) {
                                            onPlayWithContext(track, results)
                                        } else {
                                            onTrackClick(track)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!track.artworkUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                                .data(track.artworkUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.app_icon_small),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.5f
                                            ),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp, end = 8.dp),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        ProviderIcon(
                                            provider = track.source,
                                            height = 10.dp,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.8f
                                            ),
                                        )
                                        CodecIcon(
                                            codec = track.codec,
                                            height = 10.dp,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.8f
                                            ),
                                        )
                                    }
                                }

                                if (onPlayNext != null || onAddToQueue != null) {
                                    Box {
                                        IconButton(
                                            onClick = { menuExpanded = true },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_more_vert),
                                                contentDescription = "Options",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.7f
                                                ),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                        ) {
                                            if (onPlayNext != null) {
                                                DropdownMenuItem(
                                                    text = { Text("Play Next") },
                                                    onClick = {
                                                        menuExpanded = false
                                                        onPlayNext(track)
                                                    },
                                                )
                                            }
                                            if (onAddToQueue != null) {
                                                DropdownMenuItem(
                                                    text = { Text("Add to Queue") },
                                                    onClick = {
                                                        menuExpanded = false
                                                        onAddToQueue(track)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
