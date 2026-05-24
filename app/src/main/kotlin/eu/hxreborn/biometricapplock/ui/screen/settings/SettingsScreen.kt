@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package eu.hxreborn.biometricapplock.ui.screen.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.prefs.ThemeMode
import eu.hxreborn.biometricapplock.ui.component.LockSwitch
import eu.hxreborn.biometricapplock.ui.screen.RelockDelayDialog
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.util.LauncherIconHelper

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val prefs by App.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRelockDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
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
                    summary = relockDelaySummary(prefs.relockDelaySeconds),
                    onClick = { showRelockDialog = true },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_appearance)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_theme),
                    summary = themeModeLabel(prefs.themeMode),
                    onClick = { showThemeDialog = true },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.FormatPaint,
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    onClick = {
                        App.prefsRepository.save(Prefs.USE_DYNAMIC_COLOR, !prefs.useDynamicColor)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.useDynamicColor, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_secure_display)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Screenshot,
                    title = stringResource(R.string.settings_disable_flag_secure_title),
                    summary = stringResource(R.string.settings_disable_flag_secure_summary),
                    onClick = {
                        App.prefsRepository.save(Prefs.DISABLE_FLAG_SECURE, !prefs.disableFlagSecure)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.disableFlagSecure, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_privacy)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = stringResource(R.string.settings_hide_launcher),
                    summary = stringResource(R.string.settings_hide_launcher_summary),
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

            item { SettingsSectionHeader(title = stringResource(R.string.settings_diagnostics)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.BugReport,
                    title = stringResource(R.string.settings_verbose_logging),
                    summary = stringResource(R.string.settings_verbose_logging_summary),
                    onClick = {
                        App.prefsRepository.save(Prefs.VERBOSE, !prefs.verbose)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.verbose, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_about)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_about_entry),
                    summary = stringResource(R.string.settings_about_entry_summary),
                    onClick = onNavigateToAbout,
                )
            }
        }
    }
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
