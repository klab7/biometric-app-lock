package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.hxreborn.biometricapplock.ui.theme.Tokens

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val cardModifier =
        modifier
            .fillMaxWidth()
            .padding(
                horizontal = Tokens.SectionHorizontalMargin,
                vertical = Tokens.SectionItemSpacing,
            )
    val colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    when {
        onLongClick != null -> {
            Card(
                modifier =
                    cardModifier.combinedClickable(
                        enabled = onClick != null || onLongClick != null,
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    ),
                colors = colors,
            ) { content() }
        }

        onClick != null -> {
            Card(onClick = onClick, modifier = cardModifier, colors = colors) { content() }
        }

        else -> {
            Card(modifier = cardModifier, colors = colors) { content() }
        }
    }
}
