package one.tunkshif.swirlmusic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/**
 * Test seam for screenshots. When set, [Artwork] paints this instead of loading the URL, so a
 * golden never depends on the network or on Coil's timing.
 */
val LocalArtworkPainter = staticCompositionLocalOf<Painter?> { null }

/**
 * Artwork slot. Shows the theme's surface while the image loads, or when there is none.
 */
@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    /** An already-loaded smaller URL, shown while [url] loads so the image never flashes empty. */
    placeholderUrl: String? = null,
) {
    val override = LocalArtworkPainter.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when {
            override != null -> Image(
                painter = override,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )

            url != null -> AsyncImage(
                model = if (placeholderUrl != null) {
                    ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .placeholderMemoryCacheKey(placeholderUrl)
                        .build()
                } else {
                    url
                },
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
