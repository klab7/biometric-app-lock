
package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.util.restartAppProcess

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusCard(
    isActive: Boolean,
    lockedAppCount: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val container = if (isActive) scheme.primaryContainer else scheme.surfaceContainerHighest
    val contentColor = if (isActive) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    val leadingIcon = if (isActive) Icons.Filled.CheckCircle else Icons.AutoMirrored.Outlined.HelpOutline
    val title = stringResource(if (isActive) R.string.status_active else R.string.status_inactive)
    val summary =
        when {
            !isActive -> stringResource(R.string.status_inactive_action)
            lockedAppCount == 0 -> stringResource(R.string.status_locked_empty)
            else -> pluralStringResource(R.plurals.status_locked_count, lockedAppCount, lockedAppCount)
        }

    val cardColors =
        CardDefaults.cardColors(
            containerColor = container,
            contentColor = contentColor,
        )
    val itemColors =
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = contentColor,
            supportingColor = contentColor,
            leadingIconColor = contentColor,
            trailingIconColor = contentColor,
        )
    val cardModifier = modifier.fillMaxWidth().padding(horizontal = Tokens.SpacingSm, vertical = Tokens.CardVerticalSpacing)
    val leading: @Composable () -> Unit = {
        Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(32.dp))
    }
    val trailing: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(Tokens.SmallIconSize),
        )
    }

    if (!isActive) {
        Card(
            onClick = { restartAppProcess(context) },
            modifier = cardModifier,
            colors = cardColors,
        ) {
            ListItem(
                leadingContent = leading,
                trailingContent = trailing,
                headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(summary) },
                colors = itemColors,
            )
        }
    } else {
        Card(modifier = cardModifier, colors = cardColors) {
            ListItem(
                leadingContent = leading,
                headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(summary) },
                colors = itemColors,
            )
        }
    }
}
