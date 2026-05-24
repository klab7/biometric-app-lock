
package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_ICONS = 6

@Composable
fun LockedAppsStrip(
    scope: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (scope.isEmpty()) {
        EmptyStrip(onClick = onClick, modifier = modifier)
    } else {
        FilledStrip(scope = scope, onClick = onClick, modifier = modifier)
    }
}

@Composable
private fun EmptyStrip(
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.SpacingSm, vertical = Tokens.CardVerticalSpacing)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Tokens.PreferencePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(Tokens.SettingsIconSize),
            )
            Text(
                text = stringResource(R.string.dashboard_locked_apps_empty),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(Tokens.SmallIconSize),
            )
        }
    }
}

@Composable
private fun FilledStrip(
    scope: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val pkgs = scope.take(MAX_ICONS)
    val overflow = (scope.size - MAX_ICONS).coerceAtLeast(0)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.SpacingSm, vertical = Tokens.CardVerticalSpacing)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Tokens.PreferencePadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dashboard_locked_apps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.dashboard_locked_apps_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(Tokens.SmallIconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(Tokens.SpacingLg))
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm)) {
                pkgs.forEach { AppIconForPackage(it) }
                if (overflow > 0) {
                    OverflowChip(overflow)
                }
            }
        }
    }
}

@Composable
private fun AppIconForPackage(pkg: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = pkg) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager
                        .getApplicationIcon(pkg)
                        .toBitmap()
                        .asImageBitmap()
                }.getOrNull()
            }
    }

    Surface(
        modifier = Modifier.size(Tokens.AppIconSize),
        shape = RoundedCornerShape(Tokens.AppIconCornerRadius),
        color = if (icon != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OverflowChip(count: Int) {
    Surface(
        modifier = Modifier.size(Tokens.AppIconSize),
        shape = RoundedCornerShape(Tokens.AppIconCornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = "+$count",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
