package org.shilpo.laboon.permissions

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

internal enum class PermissionKind { Runtime, Settings, Automatic }

internal data class PermissionSpec(
    val id: String,
    val manifestPermission: String?,
    val kind: PermissionKind,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val isRequired: Boolean,
)
