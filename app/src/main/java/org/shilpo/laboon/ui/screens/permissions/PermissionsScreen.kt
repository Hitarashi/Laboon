@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.shilpo.laboon.ui.screens.permissions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.shilpo.laboon.R
import org.shilpo.laboon.permissions.PermissionCatalogue
import org.shilpo.laboon.permissions.PermissionKind
import org.shilpo.laboon.permissions.PermissionSpec
import org.shilpo.laboon.permissions.PermissionState
import org.shilpo.laboon.permissions.missingRequiredPermissions
import org.shilpo.laboon.ui.design.ScreenHeadline
import org.shilpo.laboon.ui.design.ScreenScaffold
import org.shilpo.laboon.ui.design.SegmentedSection

@Composable
internal fun PermissionsScreen(
    modifier: Modifier = Modifier,
    state: PermissionState,
    permissionAnswersRevision: Int = 0,
    onContinue: () -> Unit = {},
) {
    val answers = remember(state) { mutableStateMapOf<String, Boolean>() }

    fun syncPermissionAnswers() {
        PermissionCatalogue.forEach { spec -> answers[spec.id] = state.isSatisfied(spec) }
    }

    LaunchedEffect(state, permissionAnswersRevision) { syncPermissionAnswers() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { syncPermissionAnswers() }

    val interactiveSpecs =
        remember { PermissionCatalogue.filter { it.kind != PermissionKind.Automatic } }
    val automaticSpecs =
        remember { PermissionCatalogue.filter { it.kind == PermissionKind.Automatic } }
    val missingRequired = missingRequiredPermissions(PermissionCatalogue) { answers[it.id] == true }
    val canLeave = missingRequired.isEmpty()

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ScreenScaffold(
        modifier = modifier,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!canLeave) {
                        val missingTitles = missingRequired.map { stringResource(it.titleRes) }
                        Text(
                            text = stringResource(
                                id = R.string.permissions_blocked_hint,
                                missingTitles.joinToString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = onContinue,
                        enabled = canLeave,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = ButtonDefaults.LargeContentPadding,
                    ) {
                        Text(
                            text = stringResource(id = R.string.permissions_continue),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                    }
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + 16.dp,
                bottom = bottomInset + 88.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    ScreenHeadline(
                        text = stringResource(id = R.string.permissions_title),
                        color = MaterialTheme.colorScheme.primary,
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
                    interactiveSpecs.forEachIndexed { index, spec ->
                        SegmentedPermissionRow(
                            spec = spec,
                            isSatisfied = answers[spec.id] == true,
                            onRequest = {
                                state.request(spec)
                                syncPermissionAnswers()
                            },
                            index = index,
                            count = interactiveSpecs.size,
                        )
                    }
                }
            }

            item {
                SegmentedSection(
                    title = stringResource(id = R.string.permissions_auto_title),
                    items = automaticSpecs,
                    titleModifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    leadingContent = { spec ->
                        PermissionStatusIcon(spec = spec, isSatisfied = answers[spec.id] == true)
                    },
                    supportingContent = { spec ->
                        Text(
                            text = stringResource(id = spec.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = { spec ->
                        PermissionTrailingAction(
                            spec = spec,
                            isSatisfied = answers[spec.id] == true,
                            onRequest = {
                                state.request(spec)
                                syncPermissionAnswers()
                            },
                        )
                    },
                ) { spec ->
                    Text(
                        text = stringResource(id = spec.titleRes),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedPermissionRow(
    spec: PermissionSpec,
    isSatisfied: Boolean,
    onRequest: () -> Unit,
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
        leadingContent = { PermissionStatusIcon(spec = spec, isSatisfied = isSatisfied) },
        content = {
            Text(
                text = stringResource(id = spec.titleRes),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(id = spec.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            PermissionTrailingAction(spec = spec, isSatisfied = isSatisfied, onRequest = onRequest)
        },
    )
}

@Composable
private fun PermissionStatusIcon(
    spec: PermissionSpec,
    isSatisfied: Boolean,
) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = if (isSatisfied) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = spec.iconRes),
                contentDescription = null,
                tint = if (isSatisfied) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PermissionTrailingAction(
    spec: PermissionSpec,
    isSatisfied: Boolean,
    onRequest: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme

    AnimatedContent(
        targetState = isSatisfied,
        transitionSpec = {
            fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                    fadeOut(animationSpec = motionScheme.fastEffectsSpec())
        },
        label = "permissionStateTransition",
    ) { isSatisfiedNow ->
        when {
            isSatisfiedNow -> AssistChip(
                onClick = {},
                enabled = false,
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_perm_check_badge),
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.permissions_granted),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                    )
                },
            )

            spec.kind != PermissionKind.Automatic -> Button(
                onClick = onRequest,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(
                    text = stringResource(id = R.string.permissions_grant),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                )
            }

            else -> Spacer(modifier = Modifier.size(36.dp))
        }
    }
}
