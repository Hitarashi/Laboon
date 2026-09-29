package org.shilpo.laboon.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.shilpo.laboon.R

enum class MainTab(
    @StringRes val titleRes: Int,
    @DrawableRes val iconOutlined: Int,
    @DrawableRes val iconFilled: Int,
    val order: Int,
) {
    Home(
        titleRes = R.string.nav_home,
        iconOutlined = R.drawable.ic_nav_home,
        iconFilled = R.drawable.ic_nav_home_fill,
        order = 0,
    ),
    Search(
        titleRes = R.string.nav_search,
        iconOutlined = R.drawable.ic_nav_search,
        iconFilled = R.drawable.ic_nav_search_fill,
        order = 1,
    ),
    Library(
        titleRes = R.string.nav_library,
        iconOutlined = R.drawable.ic_nav_library,
        iconFilled = R.drawable.ic_nav_library_fill,
        order = 2,
    ),
}
