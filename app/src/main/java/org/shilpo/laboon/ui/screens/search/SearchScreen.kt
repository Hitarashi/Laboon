@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.shilpo.laboon.R
import org.shilpo.laboon.ui.design.FloatingNavBarClearance
import org.shilpo.laboon.ui.design.PlaceholderCard
import org.shilpo.laboon.ui.design.ScreenHeadline
import org.shilpo.laboon.ui.design.ScreenList
import org.shilpo.laboon.ui.design.ScreenScaffold

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier = modifier) {
        ScreenList(bottomClearance = FloatingNavBarClearance) {
            item {
                ScreenHeadline(text = stringResource(id = R.string.search_title))
            }

            item {
                val searchBarColors = SearchBarDefaults.colors()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SearchBarDefaults.InputFieldHeight),
                    shape = SearchBarDefaults.inputFieldShape,
                    color = searchBarColors.containerColor,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_nav_search),
                            contentDescription = null,
                            tint = searchBarColors.inputFieldColors.unfocusedLeadingIconColor,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = stringResource(id = R.string.search_placeholder_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = searchBarColors.inputFieldColors.unfocusedPlaceholderColor,
                        )
                    }
                }
            }

            item {
                PlaceholderCard(
                    title = stringResource(id = R.string.search_placeholder_title),
                    subtitle = stringResource(id = R.string.search_placeholder_subtitle),
                )
            }
        }
    }
}
