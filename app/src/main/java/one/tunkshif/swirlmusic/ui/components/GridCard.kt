package one.tunkshif.swirlmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import one.tunkshif.swirlmusic.ui.theme.Spacing

/** A square artwork card in an adaptive grid; [circularArtwork] centres the text too. */
@Composable
fun GridCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    circularArtwork: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = if (circularArtwork) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = null,
                shape = if (circularArtwork) CircleShape else MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (circularArtwork) TextAlign.Center else null,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (circularArtwork) TextAlign.Center else null,
                )
            }
        }
    }
}
