package org.shilpo.laboon.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.shilpo.laboon.R

enum class MainTab(
    @StringRes val titleRes: Int,
    val symbol: String,
    @DrawableRes val iconOutlined: Int = 0,
    @DrawableRes val iconFilled: Int = 0,
    val order: Int,
) {
    Home(
        titleRes = R.string.nav_home,
        symbol = "home",
        iconOutlined = R.drawable.ic_nav_home,
        iconFilled = R.drawable.ic_nav_home_fill,
        order = 0,
    ),
    Browse(
        titleRes = R.string.nav_browse,
        symbol = "grid_view",
        order = 1,
    ),
    Search(
        titleRes = R.string.nav_search,
        symbol = "search",
        iconOutlined = R.drawable.ic_nav_search,
        iconFilled = R.drawable.ic_nav_search_fill,
        order = 2,
    ),
    Library(
        titleRes = R.string.nav_library,
        symbol = "library_music",
        iconOutlined = R.drawable.ic_nav_library,
        iconFilled = R.drawable.ic_nav_library_fill,
        order = 3,
    ),
}
