package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.updates.ChangeType
import eu.hxreborn.biometricapplock.updates.UpdateSheetState
import eu.hxreborn.biometricapplock.updates.toSheetState
import kotlinx.coroutines.launch

@Composable
fun ChangelogSheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val cachedEntries by App.updateRepository.cachedChangelog.collectAsStateWithLifecycle()
    val cachedAvailable by App.updateRepository.cachedAvailable.collectAsStateWithLifecycle()
    val updateState by App.updateRepository.currentState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        App.updateRepository.fetchChangelog()
    }

    val latestVersionForMatch =
        when (val available = cachedAvailable) {
            null -> BuildConfig.VERSION_NAME
            else -> available.latestVersion
        }
    val hasMatchingChangelogEntry =
        cachedEntries
            ?.any { it.version != null && it.version == latestVersionForMatch }
            ?: false

    val sheetState =
        remember(updateState, cachedAvailable, hasMatchingChangelogEntry) {
            updateState.toSheetState(
                cached = cachedAvailable,
                hasMatchingChangelogEntry = hasMatchingChangelogEntry,
            )
        }

    val versionLabel =
        when (sheetState) {
            is UpdateSheetState.Available -> sheetState.latestVersion
            is UpdateSheetState.UpToDate -> sheetState.currentVersion
            is UpdateSheetState.FailedOffline -> sheetState.cachedFallback?.latestVersion ?: BuildConfig.VERSION_NAME
            is UpdateSheetState.FailedNetwork -> sheetState.cachedFallback?.latestVersion ?: BuildConfig.VERSION_NAME
            else -> BuildConfig.VERSION_NAME
        }

    val items =
        cachedEntries?.map { entry ->
            val type = ChangeType.from(entry.type, entry.breaking)
            val typeLabel = stringResource(changeTypeLabelRes(type))
            val labelText = if (!entry.scope.isNullOrBlank()) "$typeLabel · ${entry.scope}" else typeLabel
            FeatureSheetItem(
                icon = changeTypeIcon(type),
                label = labelText,
                title = entry.title,
                body = entry.description,
                isBreaking = type == ChangeType.Breaking,
                onClick = entry.url?.let { url -> { uriHandler.openUri(url) } },
            )
        } ?: emptyList()

    WhatsNewSheet(
        state = sheetState,
        items = items,
        versionLabel = versionLabel,
        onDismiss = onDismiss,
        onDownload = { url -> uriHandler.openUri(url) },
        onRetry = {
            coroutineScope.launch { App.updateRepository.checkNow() }
        },
        onLater = { version ->
            App.prefsRepository.save(Prefs.LAST_DISMISSED_AVAILABLE_VERSION, version)
        },
    )
}

private fun changeTypeIcon(type: ChangeType): ImageVector =
    when (type) {
        ChangeType.Feat -> Icons.Filled.AutoAwesome
        ChangeType.Fix -> Icons.Outlined.BugReport
        ChangeType.Perf -> Icons.Outlined.Bolt
        ChangeType.Security -> Icons.Outlined.Shield
        ChangeType.Refactor -> Icons.Outlined.Tune
        ChangeType.Revert -> Icons.AutoMirrored.Outlined.Undo
        ChangeType.Ci -> Icons.Outlined.Cloud
        ChangeType.Test -> Icons.Outlined.Science
        ChangeType.Misc -> Icons.Outlined.MoreHoriz
        ChangeType.Breaking -> Icons.Outlined.PriorityHigh
    }

private fun changeTypeLabelRes(type: ChangeType): Int =
    when (type) {
        ChangeType.Feat -> R.string.change_type_feat
        ChangeType.Fix -> R.string.change_type_fix
        ChangeType.Perf -> R.string.change_type_perf
        ChangeType.Security -> R.string.change_type_security
        ChangeType.Refactor -> R.string.change_type_refactor
        ChangeType.Revert -> R.string.change_type_revert
        ChangeType.Ci -> R.string.change_type_ci
        ChangeType.Test -> R.string.change_type_test
        ChangeType.Misc -> R.string.change_type_misc
        ChangeType.Breaking -> R.string.change_type_breaking
    }
