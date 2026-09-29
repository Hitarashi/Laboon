@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.library

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.shilpo.laboon.R
import org.shilpo.laboon.ui.design.FloatingNavBarClearance
import org.shilpo.laboon.ui.design.PlaceholderCard
import org.shilpo.laboon.ui.design.ScreenHeadline
import org.shilpo.laboon.ui.design.ScreenList
import org.shilpo.laboon.ui.design.ScreenScaffold

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier = modifier) {
        ScreenList(bottomClearance = FloatingNavBarClearance) {
            item {
                ScreenHeadline(text = stringResource(id = R.string.library_title))
            }

            item {
                PlaceholderCard(
                    title = stringResource(id = R.string.library_placeholder_title),
                    subtitle = stringResource(id = R.string.library_placeholder_subtitle),
                )
            }
        }
    }
}
