@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package eu.hxreborn.biometricapplock.ui.component

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.updates.UpdateSheetState
import kotlinx.coroutines.launch

data class FeatureSheetItem(
    val icon: ImageVector,
    val label: String? = null,
    val title: String,
    val body: String? = null,
    val isBreaking: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhatsNewSheet(
    state: UpdateSheetState,
    items: List<FeatureSheetItem>,
    versionLabel: String,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onLater: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val title = stringResource(state.titleRes)

    val contentAlpha = remember { Animatable(0f) }
    val contentOffsetY = remember { Animatable(24f) }

    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        launch { contentAlpha.animateTo(1f, tween(300)) }
        launch { contentOffsetY.animateTo(0f, tween(300)) }
    }

    val dismiss: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Tokens.SheetContentPadding)
                    .padding(bottom = Tokens.SheetContentPadding)
                    .graphicsLayer {
                        alpha = contentAlpha.value
                        translationY = contentOffsetY.value
                    },
            verticalArrangement = Arrangement.spacedBy(Tokens.SheetSectionSpacing),
        ) {
            SheetHeader(versionLabel = versionLabel, title = title)

            SheetBody(state = state, items = items, sheetTitle = title)

            SheetActions(
                state = state,
                onDismiss = dismiss,
                onDownload = onDownload,
                onLater = onLater,
                onRetry = onRetry,
            )

            Spacer(Modifier.height(Tokens.SpacingSm))
        }
    }
}

@Composable
private fun SheetHeader(
    versionLabel: String,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SheetItemSpacing),
        modifier = Modifier.semantics { heading() },
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "v$versionLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier.padding(
                        horizontal = Tokens.SpacingSm,
                        vertical = Tokens.SpacingXs,
                    ),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SheetBody(
    state: UpdateSheetState,
    items: List<FeatureSheetItem>,
    sheetTitle: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Tokens.SheetItemSpacing),
    ) {
        when (state) {
            UpdateSheetState.Checking -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(Tokens.ChangelogLoadingHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(modifier = Modifier.size(Tokens.ChangelogLoadingIndicatorSize))
                }
            }

            is UpdateSheetState.UpToDate -> {
                Callout(
                    icon = Icons.Filled.CheckCircle,
                    contentDescription = sheetTitle,
                    text = stringResource(R.string.updates_sheet_up_to_date_callout),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                ItemList(items)
            }

            is UpdateSheetState.Available -> {
                Text(
                    text = stringResource(R.string.updates_sheet_version_delta, state.currentVersion, state.latestVersion),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (!state.notesAvailable) {
                    Callout(
                        icon = Icons.Outlined.Info,
                        contentDescription = sheetTitle,
                        text = stringResource(R.string.updates_sheet_notes_unavailable),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ItemList(items)
            }

            is UpdateSheetState.FailedOffline -> {
                Callout(
                    icon = Icons.Outlined.CloudOff,
                    contentDescription = sheetTitle,
                    text = stringResource(R.string.updates_dialog_failed_offline),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (state.cachedFallback != null && items.isNotEmpty()) {
                    CachedCaption()
                    ItemList(items)
                }
            }

            is UpdateSheetState.FailedNetwork -> {
                Callout(
                    icon = Icons.Outlined.ErrorOutline,
                    contentDescription = sheetTitle,
                    text = stringResource(R.string.updates_dialog_failed_network),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (state.cachedFallback != null && items.isNotEmpty()) {
                    CachedCaption()
                    ItemList(items)
                }
            }

            is UpdateSheetState.RateLimited -> {
                val body =
                    if (state.resetAtEpochMs != null) {
                        stringResource(
                            R.string.updates_dialog_rate_limited_body,
                            DateUtils
                                .getRelativeTimeSpanString(
                                    state.resetAtEpochMs,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                ).toString(),
                        )
                    } else {
                        stringResource(R.string.updates_dialog_rate_limited_body_unknown)
                    }
                Callout(
                    icon = Icons.Outlined.HourglassEmpty,
                    contentDescription = sheetTitle,
                    text = body,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            UpdateSheetState.LatestUnknown -> {
                Callout(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = sheetTitle,
                    text = stringResource(R.string.updates_sheet_latest_unknown_body),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
                ItemList(items)
            }

            UpdateSheetState.WhatsNew -> {
                ItemList(items)
            }
        }
    }
}

@Composable
private fun ItemList(items: List<FeatureSheetItem>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SheetItemSpacing)) {
        items.forEachIndexed { index, item ->
            FeatureSheetItemRow(item = item, index = index)
        }
    }
}

@Composable
private fun CachedCaption() {
    Text(
        text = stringResource(R.string.updates_sheet_cached_caption),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Callout(
    icon: ImageVector,
    contentDescription: String,
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(Tokens.PreferencePadding),
            horizontalArrangement = Arrangement.spacedBy(Tokens.PreferenceHorizontalSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(Tokens.SettingsIconSize),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun SheetActions(
    state: UpdateSheetState,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    onLater: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        UpdateSheetState.Checking -> {
            Unit
        }

        is UpdateSheetState.Available -> {
            PrimaryAction(
                label = stringResource(R.string.updates_sheet_download),
                onClick = {
                    onDownload(state.releaseUrl)
                    onDismiss()
                },
            )
            SecondaryAction(
                label = stringResource(R.string.updates_sheet_later),
                onClick = {
                    onLater(state.latestVersion)
                    onDismiss()
                },
            )
        }

        is UpdateSheetState.FailedOffline,
        is UpdateSheetState.FailedNetwork,
        UpdateSheetState.LatestUnknown,
        -> {
            PrimaryAction(
                label = stringResource(R.string.updates_dialog_retry),
                onClick = onRetry,
            )
            SecondaryAction(
                label = stringResource(R.string.updates_dialog_close),
                onClick = onDismiss,
            )
        }

        is UpdateSheetState.RateLimited -> {
            SecondaryAction(
                label = stringResource(R.string.updates_dialog_close),
                onClick = onDismiss,
            )
        }

        is UpdateSheetState.UpToDate,
        UpdateSheetState.WhatsNew,
        -> {
            PrimaryAction(
                label = stringResource(R.string.whats_new_got_it),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(Tokens.PrimaryActionHeight),
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(Tokens.PrimaryActionHeight),
        shape = CircleShape,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FeatureSheetItemRow(
    item: FeatureSheetItem,
    index: Int,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (item.onClick != null) Modifier.clickable(onClick = item.onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(Tokens.PreferencePadding),
            horizontalArrangement = Arrangement.spacedBy(Tokens.PreferenceHorizontalSpacing),
            verticalAlignment = Alignment.Top,
        ) {
            ExpressiveIconBadge(
                index = index,
                size = Tokens.FeatureBadgeSize,
                containerColor = if (item.isBreaking) MaterialTheme.colorScheme.errorContainer else null,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(Tokens.FeatureBadgeIconSize),
                    tint =
                        if (item.isBreaking) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpacingXs)) {
                if (item.label != null) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (item.isBreaking) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (!item.body.isNullOrBlank()) {
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
