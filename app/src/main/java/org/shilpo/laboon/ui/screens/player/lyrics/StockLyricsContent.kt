@file:OptIn(ExperimentalLayoutApi::class)

package org.shilpo.laboon.ui.screens.player.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyrics.LyricsLine

@Composable
internal fun StockLyricsContent(
    track: HomeTrack, lines: List<LyricsLine>, loading: Boolean, positionMs: Long, durationMs: Long,
    onSeek: (Float) -> Unit, displayOptions: LyricsDisplayOptions, onDismissShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shareVisible by remember { mutableStateOf(false) }
    var romanization by remember { mutableStateOf(displayOptions.showRomanization) }
    var translation by remember { mutableStateOf(displayOptions.showTranslation) }
    var offsetMs by remember { mutableLongStateOf(displayOptions.syncOffsetMs) }
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(track.title, style = MaterialTheme.typography.titleLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { shareVisible = true }) { Text("Share") }
            FilterChip(
                selected = romanization,
                onClick = { romanization = !romanization },
                label = { Text("Romanization") })
            FilterChip(
                selected = translation,
                onClick = { translation = !translation },
                label = { Text("Translation") })
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = {
                offsetMs = (offsetMs - 100).coerceAtLeast(-10_000)
            }) { Text("Earlier") }
            Text("${offsetMs} ms", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = {
                offsetMs = (offsetMs + 100).coerceAtMost(10_000)
            }) { Text("Later") }
        }
        if (loading) CircularProgressIndicator()
        if (!loading && lines.isEmpty()) Text(
            "No lyrics available",
            style = MaterialTheme.typography.bodyLarge
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val active =
                    positionMs + offsetMs >= line.startMs && positionMs + offsetMs < line.endMs
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    onClick = {
                        if (durationMs > 0) onSeek(
                            (line.startMs.toFloat() / durationMs).coerceIn(
                                0f,
                                1f
                            )
                        )
                    }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(line.text, style = MaterialTheme.typography.titleLarge)
                        if (romanization) line.romanization?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (translation) displayOptions.translatedLines?.getOrNull(index)?.text?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
    if (shareVisible || displayOptions.showShareDialog) LyricsShareDialog(
        track, lines,
        onDismissRequest = { shareVisible = false; onDismissShare() })
}
