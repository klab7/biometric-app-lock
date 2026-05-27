@file:Suppress("AssignedValueIsNeverRead")
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.prefs.ThemeMode
import eu.hxreborn.biometricapplock.ui.component.ExpandedTitle
import eu.hxreborn.biometricapplock.ui.component.FeatureSheetItem
import eu.hxreborn.biometricapplock.ui.component.LockSwitch
import eu.hxreborn.biometricapplock.ui.component.SectionPosition
import eu.hxreborn.biometricapplock.ui.component.WhatsNewSheet
import eu.hxreborn.biometricapplock.ui.component.changeTypeLabelRes
import eu.hxreborn.biometricapplock.ui.screen.RelockDelayDialog
import eu.hxreborn.biometricapplock.ui.screen.relockDelaySummary
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.util.LauncherIconHelper
import eu.hxreborn.biometricapplock.updates.ChangeType
import eu.hxreborn.biometricapplock.updates.UpdateSheetState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onShowUpdateSheet: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs by App.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
    val cachedAvailable by App.updateRepository.cachedAvailable.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRelockDialog by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var launcherIconHidden by remember { mutableStateOf(!LauncherIconHelper.isLauncherIconVisible(context)) }

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
        val uriHandler = LocalUriHandler.current
        val whatsNewItems =
            App.updateRepository.bundledChangelog
                .filter { it.version == BuildConfig.VERSION_NAME }
                .map { entry ->
                    val type = ChangeType.from(entry.type, entry.breaking)
                    FeatureSheetItem(
                        label = stringResource(changeTypeLabelRes(type)),
                        scope = entry.scope?.takeIf { it.isNotBlank() },
                        changeType = type,
                        title = entry.title,
                        body = entry.description,
                        onClick = entry.url?.let { url -> { uriHandler.openUri(url) } },
                    )
                }

        WhatsNewSheet(
            state = UpdateSheetState.WhatsNew,
            items = whatsNewItems,
            versionLabel = BuildConfig.VERSION_NAME,
            onDismiss = { showWhatsNew = false },
        )
    }

    val showDotIndicator =
        cachedAvailable?.latestVersion?.let { it != prefs.lastDismissedAvailableVersion } == true

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
                    onClick = null,
                    trailing = {
                        LockSwitch(checked = false, onCheckedChange = null, enabled = false)
                    },
                    modifier = Modifier.alpha(Tokens.DISABLED_ALPHA),
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = stringResource(R.string.settings_hide_launcher),
                    summary = stringResource(R.string.settings_hide_launcher_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        val hidden = !launcherIconHidden
                        LauncherIconHelper.setLauncherIconVisible(context, !hidden)
                        launcherIconHidden = hidden
                    },
                    trailing = {
                        LockSwitch(checked = launcherIconHidden, onCheckedChange = null)
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
                    position = SectionPosition.Middle,
                    onClick = {
                        App.prefsRepository.save(Prefs.USE_DYNAMIC_COLOR, !prefs.useDynamicColor)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.useDynamicColor, onCheckedChange = null)
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.RoundedCorner,
                    title = stringResource(R.string.settings_floating_nav_bar),
                    summary = stringResource(R.string.settings_floating_nav_bar_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        App.prefsRepository.save(Prefs.FLOATING_NAV_BAR, !prefs.floatingNavBar)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.floatingNavBar, onCheckedChange = null)
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
                        onShowUpdateSheet()
                        coroutineScope.launch { App.updateRepository.checkNow() }
                    },
                    trailing = {
                        if (showDotIndicator) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(Tokens.SpacingSm)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                            )
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
                    icon = Icons.Outlined.NewReleases,
                    title = stringResource(R.string.about_whats_new_title),
                    summary = stringResource(R.string.about_whats_new_summary, BuildConfig.VERSION_NAME),
                    position = SectionPosition.Top,
                    onClick = { showWhatsNew = true },
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
private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.FOLLOW_SYSTEM -> stringResource(R.string.settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }
