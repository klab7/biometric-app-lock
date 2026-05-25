@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package eu.hxreborn.biometricapplock.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.prefs.ThemeMode
import eu.hxreborn.biometricapplock.ui.component.ChangelogSheet
import eu.hxreborn.biometricapplock.ui.component.ExpandedTitle
import eu.hxreborn.biometricapplock.ui.component.FeatureSheetItem
import eu.hxreborn.biometricapplock.ui.component.LockSwitch
import eu.hxreborn.biometricapplock.ui.component.SectionPosition
import eu.hxreborn.biometricapplock.ui.component.WhatsNewSheet
import eu.hxreborn.biometricapplock.ui.component.changeTypeIcon
import eu.hxreborn.biometricapplock.ui.component.changeTypeLabelRes
import eu.hxreborn.biometricapplock.ui.screen.RelockDelayDialog
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.util.LauncherIconHelper
import eu.hxreborn.biometricapplock.updates.ChangeType
import eu.hxreborn.biometricapplock.updates.UpdateSheetState
import eu.hxreborn.biometricapplock.updates.UpdateState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs by App.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
    val cachedAvailable by App.updateRepository.cachedAvailable.collectAsStateWithLifecycle()
    val updateState by App.updateRepository.currentState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRelockDialog by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var showUpdateChangelog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        ThemeDialog(
            current = prefs.themeMode,
            onSelect = { mode ->
                App.prefsRepository.save(Prefs.DARK_THEME_CONFIG, mode.prefValue)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showRelockDialog) {
        RelockDelayDialog(
            currentSeconds = prefs.relockDelaySeconds,
            onSelect = { seconds ->
                App.prefsRepository.save(Prefs.RELOCK_DELAY_SECONDS, seconds)
                showRelockDialog = false
            },
            onDismiss = { showRelockDialog = false },
        )
    }

    if (showWhatsNew) {
        val changelogEntries by App.updateRepository.cachedChangelog.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { App.updateRepository.fetchChangelog() }

        val whatsNewItems =
            changelogEntries
                ?.filter { it.version == BuildConfig.VERSION_NAME }
                ?.map { entry ->
                    val type = ChangeType.from(entry.type, entry.breaking)
                    val typeLabel = stringResource(changeTypeLabelRes(type))
                    val labelText = if (!entry.scope.isNullOrBlank()) "$typeLabel · ${entry.scope}" else typeLabel
                    FeatureSheetItem(
                        icon = changeTypeIcon(type),
                        label = labelText,
                        title = entry.title,
                        body = entry.description,
                        isBreaking = type == ChangeType.Breaking,
                    )
                } ?: emptyList()

        WhatsNewSheet(
            state = UpdateSheetState.WhatsNew,
            items = whatsNewItems,
            versionLabel = BuildConfig.VERSION_NAME,
            onDismiss = { showWhatsNew = false },
        )
    }

    if (showUpdateChangelog) {
        ChangelogSheet(onDismiss = { showUpdateChangelog = false })
    }

    val showDotIndicator =
        cachedAvailable != null &&
            cachedAvailable!!.latestVersion != prefs.lastDismissedAvailableVersion

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { ExpandedTitle(stringResource(R.string.tab_settings)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + Tokens.SpacingLg,
                ),
        ) {
            item { SettingsSectionHeader(title = stringResource(R.string.settings_lock)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Timer,
                    title = stringResource(R.string.settings_relock_delay_title),
                    summary = stringResource(R.string.settings_relock_delay_summary),
                    position = SectionPosition.Top,
                    onClick = { showRelockDialog = true },
                    trailing = { ValueText(relockDelaySummary(prefs.relockDelaySeconds)) },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Screenshot,
                    title = stringResource(R.string.settings_disable_flag_secure_title),
                    summary = stringResource(R.string.settings_disable_flag_secure_summary),
                    position = SectionPosition.Middle,
                    onClick = {
                        App.prefsRepository.save(Prefs.DISABLE_FLAG_SECURE, !prefs.disableFlagSecure)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.disableFlagSecure, onCheckedChange = null)
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = stringResource(R.string.settings_hide_launcher),
                    summary = stringResource(R.string.settings_hide_launcher_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        val hidden = !prefs.launcherHidden
                        LauncherIconHelper.setLauncherIconVisible(context, !hidden)
                        App.prefsRepository.save(Prefs.LAUNCHER_HIDDEN, hidden)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.launcherHidden, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_appearance)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_theme),
                    summary = stringResource(R.string.settings_theme_summary),
                    position = SectionPosition.Top,
                    onClick = { showThemeDialog = true },
                    trailing = { ValueText(themeModeLabel(prefs.themeMode)) },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.FormatPaint,
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        App.prefsRepository.save(Prefs.USE_DYNAMIC_COLOR, !prefs.useDynamicColor)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.useDynamicColor, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_updates)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.updates_check_title),
                    summary = stringResource(R.string.updates_check_summary),
                    position = SectionPosition.Top,
                    onClick = {
                        showUpdateChangelog = true
                        coroutineScope.launch { App.updateRepository.checkNow() }
                    },
                    trailing = {
                        when {
                            updateState is UpdateState.Checking -> {
                                LoadingIndicator(modifier = Modifier.size(Tokens.LoadingIndicatorSize))
                            }

                            showDotIndicator -> {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(Tokens.SpacingSm)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Update,
                    title = stringResource(R.string.updates_auto_check_title),
                    summary = stringResource(R.string.updates_auto_check_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        App.prefsRepository.save(Prefs.AUTO_CHECK_UPDATE, !prefs.autoCheckUpdate)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.autoCheckUpdate, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_about)) }
            item {
                PreferenceRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = stringResource(R.string.about_whats_new_title),
                    summary = stringResource(R.string.about_whats_new_summary, BuildConfig.VERSION_NAME),
                    position = SectionPosition.Top,
                    onClick = { showWhatsNew = true },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.BugReport,
                    title = stringResource(R.string.settings_verbose_logging),
                    summary = stringResource(R.string.settings_verbose_logging_summary),
                    position = SectionPosition.Middle,
                    onClick = {
                        App.prefsRepository.save(Prefs.VERBOSE, !prefs.verbose)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.verbose, onCheckedChange = null)
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_about_entry),
                    summary = stringResource(R.string.settings_about_entry_summary),
                    position = SectionPosition.Bottom,
                    onClick = onNavigateToAbout,
                )
            }
        }
    }
}

@Composable
private fun ValueText(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun relockDelaySummary(seconds: Int): String =
    when (seconds) {
        -1 -> stringResource(R.string.app_detail_relock_delay_never)
        0 -> stringResource(R.string.app_detail_relock_delay_immediate)
        30 -> stringResource(R.string.app_detail_relock_delay_30s)
        60 -> stringResource(R.string.app_detail_relock_delay_1m)
        300 -> stringResource(R.string.app_detail_relock_delay_5m)
        1800 -> stringResource(R.string.app_detail_relock_delay_30m)
        else -> "$seconds s"
    }

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.FOLLOW_SYSTEM -> stringResource(R.string.settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }
