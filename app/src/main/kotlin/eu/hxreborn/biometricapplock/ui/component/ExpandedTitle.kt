package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import eu.hxreborn.biometricapplock.ui.theme.Tokens

@Composable
fun ExpandedTitle(text: String) {
    val isExpanded = LocalTextStyle.current.fontSize >= MaterialTheme.typography.headlineMedium.fontSize
    Text(
        text = text,
        style =
            if (isExpanded) {
                MaterialTheme.typography.headlineLarge.copy(
                    lineHeight = Tokens.ExpandedTitleLineHeight,
                )
            } else {
                LocalTextStyle.current
            },
        maxLines = if (isExpanded) Tokens.EXPANDED_TITLE_MAX_LINES else 1,
        overflow = TextOverflow.Ellipsis,
    )
}
