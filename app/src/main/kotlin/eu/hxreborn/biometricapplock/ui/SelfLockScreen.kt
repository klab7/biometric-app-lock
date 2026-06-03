package eu.hxreborn.biometricapplock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens

@Composable
internal fun SelfLockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val appIcon =
        remember {
            context.packageManager
                .getApplicationIcon(context.packageName)
                .toBitmap()
                .asImageBitmap()
        }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tokens.SpacingLg),
        ) {
            Image(
                bitmap = appIcon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(onClick = onUnlock) {
                Text(stringResource(R.string.self_lock_prompt_title))
            }
        }
        Spacer(Modifier.weight(1.8f))
    }
}
