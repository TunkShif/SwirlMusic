package one.tunkshif.swirlmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.tunkshif.swirlmusic.ui.theme.Spacing

/**
 * Square artwork over a title and one line of metadata. Used by every grid and row of cards.
 */
@Composable
fun MediaCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    width: Dp = 132.dp,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .width(width)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Artwork(
            url = artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(width),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
