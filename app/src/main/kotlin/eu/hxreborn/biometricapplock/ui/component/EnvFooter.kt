
package eu.hxreborn.biometricapplock.ui.component

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.viewmodel.FrameworkInfo

@Composable
fun EnvFooter(
    framework: FrameworkInfo?,
    modifier: Modifier = Modifier,
) {
    val unboundLabel = stringResource(R.string.env_footer_unbound)
    val text =
        remember(framework, unboundLabel) {
            val xposedPart = framework?.let { "${it.name} ${it.version}" } ?: unboundLabel
            "$xposedPart · Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}"
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = Tokens.SpacingLg, vertical = Tokens.SpacingSm),
    )
}
