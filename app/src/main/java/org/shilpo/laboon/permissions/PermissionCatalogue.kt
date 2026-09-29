package org.shilpo.laboon.permissions

import android.Manifest
import org.shilpo.laboon.R

internal object PermissionIds {
    const val NOTIFICATIONS = "notifications"
    const val STORAGE = "storage"
    const val BT_CONNECT = "bt_connect"
    const val BT_SCAN = "bt_scan"
    const val BATTERY = "battery"
    const val INSTALL = "install"
    const val NETWORK = "network"
    const val AUDIO_VIBE = "audio_vibe"
}

internal val PermissionCatalogue: List<PermissionSpec> = listOf(
    PermissionSpec(
        id = PermissionIds.NOTIFICATIONS,
        manifestPermission = Manifest.permission.POST_NOTIFICATIONS,
        kind = PermissionKind.Runtime,
        titleRes = R.string.permissions_notif_title,
        descriptionRes = R.string.permissions_notif_desc,
        iconRes = R.drawable.ic_perm_notification,
        isRequired = true,
    ),
    PermissionSpec(
        id = PermissionIds.STORAGE,
        manifestPermission = Manifest.permission.READ_MEDIA_AUDIO,
        kind = PermissionKind.Runtime,
        titleRes = R.string.permissions_storage_title,
        descriptionRes = R.string.permissions_storage_desc,
        iconRes = R.drawable.ic_perm_storage,
        isRequired = true,
    ),
    PermissionSpec(
        id = PermissionIds.BT_CONNECT,
        manifestPermission = Manifest.permission.BLUETOOTH_CONNECT,
        kind = PermissionKind.Runtime,
        titleRes = R.string.permissions_bt_connect_title,
        descriptionRes = R.string.permissions_bt_connect_desc,
        iconRes = R.drawable.ic_perm_bt_connect,
        isRequired = false,
    ),
    PermissionSpec(
        id = PermissionIds.BT_SCAN,
        manifestPermission = Manifest.permission.BLUETOOTH_SCAN,
        kind = PermissionKind.Runtime,
        titleRes = R.string.permissions_bt_scan_title,
        descriptionRes = R.string.permissions_bt_scan_desc,
        iconRes = R.drawable.ic_perm_bt_scan,
        isRequired = false,
    ),
    PermissionSpec(
        id = PermissionIds.BATTERY,
        manifestPermission = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        kind = PermissionKind.Settings,
        titleRes = R.string.permissions_battery_title,
        descriptionRes = R.string.permissions_battery_desc,
        iconRes = R.drawable.ic_perm_battery,
        isRequired = true,
    ),
    PermissionSpec(
        id = PermissionIds.INSTALL,
        manifestPermission = Manifest.permission.REQUEST_INSTALL_PACKAGES,
        kind = PermissionKind.Settings,
        titleRes = R.string.permissions_install_title,
        descriptionRes = R.string.permissions_install_desc,
        iconRes = R.drawable.ic_perm_install,
        isRequired = true,
    ),
    PermissionSpec(
        id = PermissionIds.NETWORK,
        manifestPermission = Manifest.permission.INTERNET,
        kind = PermissionKind.Automatic,
        titleRes = R.string.permissions_network_title,
        descriptionRes = R.string.permissions_network_desc,
        iconRes = R.drawable.ic_perm_network,
        isRequired = true,
    ),
    PermissionSpec(
        id = PermissionIds.AUDIO_VIBE,
        manifestPermission = Manifest.permission.VIBRATE,
        kind = PermissionKind.Automatic,
        titleRes = R.string.permissions_audio_vibe_title,
        descriptionRes = R.string.permissions_audio_vibe_desc,
        iconRes = R.drawable.ic_perm_audio_vibe,
        isRequired = true,
    ),
)

internal fun requiredRuntimePermissions(
    catalogue: List<PermissionSpec> = PermissionCatalogue,
): List<PermissionSpec> = catalogue.filter { it.isRequired && it.kind == PermissionKind.Runtime }

internal fun missingRequiredPermissions(
    catalogue: List<PermissionSpec> = PermissionCatalogue,
    isSatisfied: (PermissionSpec) -> Boolean,
): List<PermissionSpec> = requiredRuntimePermissions(catalogue).filterNot(isSatisfied)

internal fun canLeaveOnboarding(
    catalogue: List<PermissionSpec> = PermissionCatalogue,
    state: PermissionState,
): Boolean = missingRequiredPermissions(catalogue, state::isSatisfied).isEmpty()
