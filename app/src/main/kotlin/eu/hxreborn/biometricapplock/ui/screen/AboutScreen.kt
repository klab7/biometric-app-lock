@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package eu.hxreborn.biometricapplock.ui.screen

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.component.SoftBlobBadge
import eu.hxreborn.biometricapplock.ui.component.WhatsNewSheet
import eu.hxreborn.biometricapplock.ui.screen.settings.PreferenceRow
import eu.hxreborn.biometricapplock.ui.screen.settings.SettingsSectionHeader
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.viewmodel.FrameworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REPO_URL = "https://github.com/hxreborn/biometric-app-lock"
private val BlobSize: Dp = 128.dp
private val BlobIconSize: Dp = 72.dp

@Composable
fun AboutScreen(
    framework: FrameworkInfo?,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val packageName = context.packageName
    var showWhatsNew by remember { mutableStateOf(false) }
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    pm
                        .getApplicationInfo(packageName, 0)
                        .loadIcon(pm)
                        .toBitmap()
                        .asImageBitmap()
                }.getOrNull()
            }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                navigationIcon = {
                    Surface(
                        modifier = Modifier.padding(start = Tokens.SpacingSm).size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.about_back_cd),
                            )
                        }
                    }
                },
                title = { Text(stringResource(R.string.about_title)) },
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
            item { Spacer(Modifier.height(Tokens.SectionHeaderTopPadding)) }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    SoftBlobBadge(size = BlobSize) {
                        icon?.let {
                            Image(
                                bitmap = it,
                                contentDescription = null,
                                modifier = Modifier.size(BlobIconSize),
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Tokens.SpacingLg)) }

            item {
                Text(
                    text = stringResource(R.string.about_app_name),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.SectionHorizontalMargin),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.about_section_links)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Code,
                    title = stringResource(R.string.about_source_title),
                    summary = stringResource(R.string.about_source_summary),
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, REPO_URL.toUri()))
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.about_section_versions)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.about_app_version_title),
                    summary = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.about_libxposed_api_title),
                    summary = BuildConfig.LIBXPOSED_API_VERSION.toString(),
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Memory,
                    title = stringResource(R.string.about_framework_title),
                    summary =
                        framework?.let { "${it.name} ${it.version}" }
                            ?: stringResource(R.string.settings_lsposed_framework_unbound),
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.about_section_release)) }
            item {
                PreferenceRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = stringResource(R.string.about_whats_new_title),
                    summary = stringResource(R.string.about_whats_new_summary, BuildConfig.VERSION_NAME),
                    onClick = { showWhatsNew = true },
                )
            }
        }
    }

    if (showWhatsNew) {
        WhatsNewSheet(onDismiss = { showWhatsNew = false })
    }
}
