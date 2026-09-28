@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.shilpo.laboon.R

data class PermissionItem(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int? = null,
    val isGranted: Boolean = false,
    val onRequest: () -> Unit = {},
)

@Composable
fun PermissionsScreen(
    modifier: Modifier = Modifier,
    customIcons: Map<String, Int> = emptyMap(),
    onContinue: () -> Unit = {},
) {
    val context = LocalContext.current

    var notificationsGranted by remember { mutableStateOf(false) }
    var storageGranted by remember { mutableStateOf(false) }
    var bluetoothConnectGranted by remember { mutableStateOf(false) }
    var bluetoothScanGranted by remember { mutableStateOf(false) }
    var backgroundPlaybackGranted by remember { mutableStateOf(false) }
    var appInstallGranted by remember { mutableStateOf(false) }

    fun updatePermissionStates() {
        notificationsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        storageGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        bluetoothConnectGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED

        bluetoothScanGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN,
        ) == PackageManager.PERMISSION_GRANTED

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        backgroundPlaybackGranted =
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

        appInstallGranted = context.packageManager.canRequestPackageInstalls()
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        updatePermissionStates()
    }

    LaunchedEffect(Unit) {
        updatePermissionStates()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        updatePermissionStates()
    }

    val interactiveItems = remember(
        notificationsGranted,
        storageGranted,
        bluetoothConnectGranted,
        bluetoothScanGranted,
        backgroundPlaybackGranted,
        appInstallGranted,
        customIcons,
    ) {
        listOf(
            PermissionItem(
                id = "notifications",
                titleRes = R.string.permissions_notif_title,
                descriptionRes = R.string.permissions_notif_desc,
                iconRes = customIcons["notifications"] ?: R.drawable.ic_perm_notification,
                isGranted = notificationsGranted,
                onRequest = {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            ),
            PermissionItem(
                id = "storage",
                titleRes = R.string.permissions_storage_title,
                descriptionRes = R.string.permissions_storage_desc,
                iconRes = customIcons["storage"] ?: R.drawable.ic_perm_storage,
                isGranted = storageGranted,
                onRequest = {
                    requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                },
            ),
            PermissionItem(
                id = "bt_connect",
                titleRes = R.string.permissions_bt_connect_title,
                descriptionRes = R.string.permissions_bt_connect_desc,
                iconRes = customIcons["bt_connect"] ?: R.drawable.ic_perm_bt_connect,
                isGranted = bluetoothConnectGranted,
                onRequest = {
                    requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                },
            ),
            PermissionItem(
                id = "bt_scan",
                titleRes = R.string.permissions_bt_scan_title,
                descriptionRes = R.string.permissions_bt_scan_desc,
                iconRes = customIcons["bt_scan"] ?: R.drawable.ic_perm_bt_scan,
                isGranted = bluetoothScanGranted,
                onRequest = {
                    requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                },
            ),
            PermissionItem(
                id = "battery",
                titleRes = R.string.permissions_battery_title,
                descriptionRes = R.string.permissions_battery_desc,
                iconRes = customIcons["battery"] ?: R.drawable.ic_perm_battery,
                isGranted = backgroundPlaybackGranted,
                onRequest = {
                    try {
                        val intent =
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val intent =
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            context.startActivity(intent)
                        }
                    }
                },
            ),
            PermissionItem(
                id = "install",
                titleRes = R.string.permissions_install_title,
                descriptionRes = R.string.permissions_install_desc,
                iconRes = customIcons["install"] ?: R.drawable.ic_perm_install,
                isGranted = appInstallGranted,
                onRequest = {
                    try {
                        val intent =
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        context.startActivity(intent)
                    }
                },
            ),
        )
    }

    val autoGrantedItems = remember(customIcons) {
        listOf(
            PermissionItem(
                id = "network",
                titleRes = R.string.permissions_network_title,
                descriptionRes = R.string.permissions_network_desc,
                iconRes = customIcons["network"] ?: R.drawable.ic_perm_network,
                isGranted = true,
            ),
            PermissionItem(
                id = "audio_vibe",
                titleRes = R.string.permissions_audio_vibe_title,
                descriptionRes = R.string.permissions_audio_vibe_desc,
                iconRes = customIcons["audio_vibe"] ?: R.drawable.ic_perm_audio_vibe,
                isGranted = true,
            ),
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onContinue,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = ButtonDefaults.LargeContentPadding,
                ) {
                    Text(
                        text = stringResource(id = R.string.permissions_continue),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.permissions_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(id = R.string.permissions_subtitle),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    interactiveItems.forEachIndexed { index, item ->
                        SegmentedPermissionItem(
                            item = item,
                            index = index,
                            count = interactiveItems.size,
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.permissions_auto_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        autoGrantedItems.forEachIndexed { index, item ->
                            SegmentedPermissionItem(
                                item = item,
                                index = index,
                                count = autoGrantedItems.size,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedPermissionItem(
    item: PermissionItem,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(
            index = index,
            count = count,
        ),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (item.isGranted) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.iconRes != null) {
                        Icon(
                            painter = painterResource(id = item.iconRes),
                            contentDescription = null,
                            tint = if (item.isGranted) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    color = if (item.isGranted) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }
        },
        content = {
            Text(
                text = stringResource(id = item.titleRes),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        },
        supportingContent = {
            Text(
                text = stringResource(id = item.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            AnimatedContent(
                targetState = item.isGranted,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "permissionStateTransition",
            ) { isGranted ->
                if (isGranted) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_perm_check_badge),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.permissions_granted),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = item.onRequest,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text(
                            text = stringResource(id = R.string.permissions_grant),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        },
    )
}
