package org.shilpo.laboon.permissions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
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
        PermissionIds.NOTIFICATIONS -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            hasManifestPermission(spec)
        }

        PermissionIds.BATTERY -> isIgnoringBatteryOptimizations()
        else -> hasManifestPermission(spec)
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

            PermissionIds.NOTIFICATIONS -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                openSettings(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        context.packageName,
                    ),
                )
            } else {
                requestRuntime(spec)
            }

            else -> {
                when (spec.kind) {
                    PermissionKind.Runtime -> requestRuntime(spec)
                    PermissionKind.Settings -> openSettings()
                    PermissionKind.Automatic -> Unit
                }
            }
        }
    }

    private fun hasManifestPermission(spec: PermissionSpec): Boolean =
        spec.manifestPermission?.let { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        } ?: false

    private fun requestRuntime(spec: PermissionSpec) {
        spec.manifestPermission?.let { permission -> requestRuntimePermission?.invoke(permission) }
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
