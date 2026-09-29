@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
    topBar: (@Composable BoxScope.() -> Unit)? = null,
    bottomBar: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            if (topBar != null) topBar()
            if (bottomBar != null) bottomBar()
        }
    }
}

@Composable
fun ScreenList(
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    verticalPadding: Dp = 24.dp,
    itemSpacing: Dp = 20.dp,
    appliesStatusBarPadding: Boolean = true,
    itemContent: LazyListScope.() -> Unit,
) {
    val topInset = if (appliesStatusBarPadding) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = verticalPadding + topInset,
            bottom = verticalPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        itemContent()
        item {
            Spacer(modifier = Modifier.height(bottomClearance + navBarBottomInset + 16.dp))
        }
    }
}

@Composable
fun ScreenHeadline(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineLargeEmphasized.copy(
            fontSize = 34.sp,
            color = color,
        ),
    )
}

