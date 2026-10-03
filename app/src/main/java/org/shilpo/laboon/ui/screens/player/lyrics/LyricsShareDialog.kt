package org.shilpo.laboon.ui.screens.player.lyrics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shilpo.laboon.R
import org.shilpo.laboon.home.HomeTrack
import org.shilpo.laboon.lyrics.LyricsLine
import java.io.File
import java.io.FileOutputStream

@Composable
fun LyricsShareDialog(
    track: HomeTrack,
    lyricsLines: List<LyricsLine>,
    activeLineIndex: Int = -1,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val vocalLines = remember(lyricsLines) {
        lyricsLines.filter { !it.isInstrumental && it.text.isNotBlank() }
    }

    val selectedIndices = remember(vocalLines, activeLineIndex) {
        mutableStateListOf<Int>().apply {
            if (vocalLines.isNotEmpty()) {
                val initialIdx = if (activeLineIndex in lyricsLines.indices) {
                    val activeLine = lyricsLines[activeLineIndex]
                    vocalLines.indexOf(activeLine).takeIf { it >= 0 } ?: 0
                } else 0
                add(initialIdx)
            }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isGeneratingImage by remember { mutableStateOf(false) }

    val hasRomanization =
        remember(vocalLines) { vocalLines.any { !it.romanization.isNullOrBlank() } }
    val hasTranslation = remember(vocalLines) { vocalLines.any { it.translations.isNotEmpty() } }

    var includeRomanization by remember(hasRomanization) { mutableStateOf(hasRomanization) }
    var includeTranslation by remember(hasTranslation) { mutableStateOf(hasTranslation) }

    val selectedLines = remember(selectedIndices.toList(), vocalLines) {
        selectedIndices.sorted().mapNotNull { vocalLines.getOrNull(it) }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_lyrics),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Share Lyrics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_clear),
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {},
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Frosted Card", fontWeight = FontWeight.Bold) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Plain Text", fontWeight = FontWeight.Bold) },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f),
                ) {
                    if (selectedTab == 0) {
                        FrostedLyricsCardPreview(
                            track = track,
                            selectedLines = selectedLines,
                            includeRomanization = includeRomanization,
                            includeTranslation = includeTranslation,
                        )
                    } else {
                        PlainTextPreview(
                            track = track,
                            selectedLines = selectedLines,
                            includeRomanization = includeRomanization,
                            includeTranslation = includeTranslation,
                        )
                    }

                    if (hasRomanization || hasTranslation) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            if (hasRomanization) {
                                FilterChip(
                                    selected = includeRomanization,
                                    onClick = { includeRomanization = !includeRomanization },
                                    label = { Text("Romanization") },
                                )
                            }
                            if (hasTranslation) {
                                FilterChip(
                                    selected = includeTranslation,
                                    onClick = { includeTranslation = !includeTranslation },
                                    label = { Text("Translation") },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Select lines (${selectedLines.size}/6)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(max = 200.dp),
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            itemsIndexed(vocalLines) { index, line ->
                                val isSelected = selectedIndices.contains(index)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .toggleable(
                                            value = isSelected,
                                            role = Role.Checkbox,
                                            onValueChange = { checked ->
                                                if (checked) {
                                                    if (selectedIndices.size < 6) {
                                                        selectedIndices.add(index)
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Maximum 6 lines allowed",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } else if (selectedIndices.size > 1) {
                                                    selectedIndices.remove(index)
                                                }
                                            },
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            copyLyricsToClipboard(
                                context,
                                track,
                                selectedLines,
                                includeRomanization,
                                includeTranslation
                            )
                            Toast.makeText(
                                context,
                                "Lyrics copied to clipboard",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Copy text", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            if (selectedTab == 1) {
                                shareLyricsAsText(
                                    context,
                                    track,
                                    selectedLines,
                                    includeRomanization,
                                    includeTranslation
                                )
                                onDismissRequest()
                            } else if (!isGeneratingImage) {
                                isGeneratingImage = true
                                coroutineScope.launch {
                                    try {
                                        val bitmap = renderCardBitmap(
                                            context,
                                            track,
                                            selectedLines,
                                            includeRomanization,
                                            includeTranslation
                                        )
                                        shareLyricsCardBitmap(context, bitmap)
                                        onDismissRequest()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Failed to generate card: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isGeneratingImage = false
                                    }
                                }
                            }
                        },
                        enabled = selectedLines.isNotEmpty() && !isGeneratingImage,
                        modifier = Modifier.weight(1.6f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        if (isGeneratingImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = if (selectedTab == 0) "Share card" else "Share text",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrostedLyricsCardPreview(
    track: HomeTrack,
    selectedLines: List<LyricsLine>,
    includeRomanization: Boolean = true,
    includeTranslation: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF232538),
                            Color(0xFF141522),
                            Color(0xFF0C0D15),
                        )
                    )
                )
                .padding(20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(track.artworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .heightIn(min = 36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.5f))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (selectedLines.isEmpty()) {
                            Text(
                                text = "No lines selected",
                                fontSize = 16.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        } else {
                            selectedLines.forEach { line ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = line.text,
                                        fontSize = 17.sp,
                                        lineHeight = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    if (includeRomanization && !line.romanization.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = line.romanization,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = Color.White.copy(alpha = 0.70f),
                                        )
                                    }
                                    if (includeTranslation && line.translations.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = line.translations.joinToString("\n") { it.text },
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = Color.White.copy(alpha = 0.85f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_song_wave),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color.White.copy(alpha = 0.65f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Laboon Music",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainTextPreview(
    track: HomeTrack,
    selectedLines: List<LyricsLine>,
    includeRomanization: Boolean = true,
    includeTranslation: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val text = formatLyricsOnlyText(selectedLines, includeRomanization, includeTranslation)
            Text(
                text = if (text.isNotBlank()) "\"$text\"" else "No lines selected",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "— ${track.title} · ${track.artist}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatLyricsOnlyText(
    lines: List<LyricsLine>,
    includeRomanization: Boolean = true,
    includeTranslation: Boolean = true,
): String {
    return lines.filter { it.text.isNotBlank() }.joinToString("\n\n") { line ->
        buildString {
            append(line.text.trim())
            if (includeRomanization && !line.romanization.isNullOrBlank()) {
                append("\n(").append(line.romanization.trim()).append(")")
            }
            if (includeTranslation && line.translations.isNotEmpty()) {
                append("\n→ ").append(line.translations.joinToString(" / ") { it.text.trim() })
            }
        }
    }
}

private fun formatShareText(
    track: HomeTrack,
    lines: List<LyricsLine>,
    includeRomanization: Boolean = true,
    includeTranslation: Boolean = true,
): String {
    val text = formatLyricsOnlyText(lines, includeRomanization, includeTranslation)
    return buildString {
        append("\"")
        append(text)
        append("\"\n\n")
        append(track.title)
        append(" — ")
        append(track.artist)
    }
}

fun shareLyricsAsText(
    context: Context,
    track: HomeTrack,
    lines: List<LyricsLine>,
    includeRomanization: Boolean = true,
    includeTranslation: Boolean = true,
) {
    val shareBody = formatShareText(track, lines, includeRomanization, includeTranslation)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareBody)
    }
    context.startActivity(Intent.createChooser(intent, "Share Lyrics"))
}

@Suppress("UsePropertyAccessSyntax")
fun copyLyricsToClipboard(
    context: Context,
    track: HomeTrack,
    lines: List<LyricsLine>,
    includeRomanization: Boolean = true,
    includeTranslation: Boolean = true,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(
        "Lyrics",
        formatShareText(track, lines, includeRomanization, includeTranslation)
    )
    clipboard.setPrimaryClip(clip)
}

suspend fun renderCardBitmap(
    context: Context,
    track: HomeTrack,
    lines: List<LyricsLine>,
    includeRomanization: Boolean = true,
    includeTranslation: Boolean = true,
): Bitmap = withContext(Dispatchers.IO) {
    val width = 1080

    val artworkBitmap: Bitmap? = track.artworkUrl?.let { url ->
        runCatching {
            val loader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            result.image?.toBitmap()
        }.getOrNull()
    }

    val padding = 72f
    val headerHeight = 150f
    val footerHeight = 90f
    val linePaint = TextPaint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 46f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val romanPaint = TextPaint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(180, 255, 255, 255)
        textSize = 32f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val transPaint = TextPaint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(215, 255, 255, 255)
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    val textWidth = (width - padding * 2 - 80f).toInt().coerceAtLeast(100)

    data class LineBlock(
        val main: StaticLayout,
        val roman: StaticLayout?,
        val trans: StaticLayout?,
    ) {
        val totalHeight: Int
            get() = main.height +
                    (roman?.let { it.height + 8 } ?: 0) +
                    (trans?.let { it.height + 8 } ?: 0)
    }

    val blocks = lines.filter { it.text.isNotBlank() }.map { line ->
        val mainLayout = StaticLayout.Builder.obtain(
            line.text.trim(),
            0,
            line.text.trim().length,
            linePaint,
            textWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(16f, 1f)
            .build()
        val romanLayout = if (includeRomanization && !line.romanization.isNullOrBlank()) {
            val rText = line.romanization.trim()
            StaticLayout.Builder.obtain(rText, 0, rText.length, romanPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(10f, 1f)
                .build()
        } else null
        val transLayout = if (includeTranslation && line.translations.isNotEmpty()) {
            val tText = line.translations.joinToString("\n") { it.text.trim() }
            StaticLayout.Builder.obtain(tText, 0, tText.length, transPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(10f, 1f)
                .build()
        } else null
        LineBlock(mainLayout, romanLayout, transLayout)
    }

    val lyricsContentHeight = blocks.sumOf { it.totalHeight } + (blocks.size * 28)
    val cardHeight =
        (padding + headerHeight + 50f + lyricsContentHeight + 50f + footerHeight + padding).toInt()
            .coerceAtLeast(960)

    val bitmap = Bitmap.createBitmap(width, cardHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), cardHeight.toFloat(),
            intArrayOf(
                android.graphics.Color.rgb(28, 30, 48),
                android.graphics.Color.rgb(18, 19, 30),
                android.graphics.Color.rgb(11, 12, 19),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), cardHeight.toFloat(), bgPaint)

    artworkBitmap?.let { art ->
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 32
        }
        val scaledArt = Bitmap.createScaledBitmap(art, width, width, false)
        canvas.drawBitmap(scaledArt, 0f, -width * 0.35f, artPaint)
    }

    val cardRect = RectF(padding, padding, width - padding, cardHeight - padding)
    val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(90, 25, 27, 42)
        style = Paint.Style.FILL
    }
    val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(cardRect, 48f, 48f, cardFillPaint)
    canvas.drawRoundRect(cardRect, 48f, 48f, cardBorderPaint)

    val innerLeft = cardRect.left + 54f
    var currentY = cardRect.top + 54f

    val artSize = 130f
    val artRect = RectF(innerLeft, currentY, innerLeft + artSize, currentY + artSize)
    if (artworkBitmap != null) {
        val artPath = android.graphics.Path().apply {
            addRoundRect(artRect, 26f, 26f, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(artPath)
        val scaledArt =
            Bitmap.createScaledBitmap(artworkBitmap, artSize.toInt(), artSize.toInt(), false)
        canvas.drawBitmap(scaledArt, innerLeft, currentY, null)
        canvas.restore()
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(80, 255, 255, 255)
        }
        canvas.drawRoundRect(artRect, 26f, 26f, placeholderPaint)
    }

    val titlePaint = TextPaint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 44f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val artistPaint = TextPaint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(200, 255, 255, 255)
        textSize = 34f
    }

    val textX = innerLeft + artSize + 36f
    canvas.drawText(track.title.take(35), textX, currentY + 54f, titlePaint)
    canvas.drawText(track.artist.take(40), textX, currentY + 104f, artistPaint)

    currentY += artSize + 60f

    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(160, 255, 255, 255)
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(
        innerLeft,
        currentY,
        innerLeft,
        currentY + lyricsContentHeight.toFloat(),
        accentPaint
    )

    var lyricY = currentY
    blocks.forEach { block ->
        canvas.save()
        canvas.translate(innerLeft + 36f, lyricY)
        block.main.draw(canvas)
        canvas.restore()
        lyricY += block.main.height

        block.roman?.let { rLayout ->
            lyricY += 8f
            canvas.save()
            canvas.translate(innerLeft + 36f, lyricY)
            rLayout.draw(canvas)
            canvas.restore()
            lyricY += rLayout.height
        }

        block.trans?.let { tLayout ->
            lyricY += 8f
            canvas.save()
            canvas.translate(innerLeft + 36f, lyricY)
            tLayout.draw(canvas)
            canvas.restore()
            lyricY += tLayout.height
        }

        lyricY += 28f
    }

    val footerPaint = TextPaint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(180, 255, 255, 255)
        textSize = 32f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val footerY = cardRect.bottom - 46f
    canvas.drawText("♪  Laboon Music", innerLeft, footerY, footerPaint)

    bitmap
}

fun shareLyricsCardBitmap(context: Context, bitmap: Bitmap) {
    val cachePath = File(context.cacheDir, "shared_images")
    cachePath.mkdirs()
    val file = File(cachePath, "lyrics_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Lyrics Card"))
}
