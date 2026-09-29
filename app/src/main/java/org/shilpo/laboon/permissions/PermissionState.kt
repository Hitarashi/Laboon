package org.shilpo.laboon.permissions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

internal interface PermissionState {
    fun isSatisfied(spec: PermissionSpec): Boolean
    fun request(spec: PermissionSpec)
}

internal class AndroidPermissionState(
    private val context: Context,
    private val requestRuntimePermission: ((String) -> Unit)? = null,
) : PermissionState {

    override fun isSatisfied(spec: PermissionSpec): Boolean = when (spec.id) {
        PermissionIds.BATTERY -> isIgnoringBatteryOptimizations()
        PermissionIds.INSTALL -> context.packageManager.canRequestPackageInstalls()
        else -> spec.manifestPermission?.let { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        } ?: false
    }

    @SuppressLint("BatteryLife")
    override fun request(spec: PermissionSpec) {
        when (spec.id) {
            PermissionIds.BATTERY -> openSettings(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${context.packageName}".toUri()
                },
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            PermissionIds.INSTALL -> openSettings(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                },
            )

            else -> {
                val permission = spec.manifestPermission ?: return
                when (spec.kind) {
                    PermissionKind.Runtime -> requestRuntimePermission?.invoke(permission)
                    PermissionKind.Settings -> openSettings()
                    PermissionKind.Automatic -> Unit
                }
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    private fun openSettings(vararg preferred: Intent) {
        for (intent in preferred) {
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
        runCatching { context.startActivity(appDetails) }
    }
}
