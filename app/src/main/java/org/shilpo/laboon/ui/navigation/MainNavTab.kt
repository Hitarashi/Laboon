package org.shilpo.laboon.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.shilpo.laboon.R

enum class MainNavTab(
    @StringRes val titleRes: Int,
    @DrawableRes val iconOutlined: Int,
    @DrawableRes val iconFilled: Int,
) {
    Home(
        titleRes = R.string.nav_home,
        iconOutlined = R.drawable.ic_nav_home,
        iconFilled = R.drawable.ic_nav_home_fill,
    ),
    Search(
        titleRes = R.string.nav_search,
        iconOutlined = R.drawable.ic_nav_search,
        iconFilled = R.drawable.ic_nav_search_fill,
    ),
    Library(
        titleRes = R.string.nav_library,
        iconOutlined = R.drawable.ic_nav_library,
        iconFilled = R.drawable.ic_nav_library_fill,
    ),
}
