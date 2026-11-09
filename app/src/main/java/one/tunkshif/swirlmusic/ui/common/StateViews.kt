package one.tunkshif.swirlmusic.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.theme.Spacing

@Composable
fun SectionError(
    error: LoadError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                when (error) {
                    LoadError.Network -> R.string.error_network
                    LoadError.Server -> R.string.error_server
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
    )
}

/** Grey blocks while a list loads. Static on purpose: a golden must not animate. */
@Composable
fun LoadingCards(
    modifier: Modifier = Modifier,
    count: Int = 6,
    cardSize: Dp = 108.dp,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat((count + 2) / 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(cardSize)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

/**
 * A grey placeholder bar. With [textStyle], an invisible glyph row sizes it to exactly one
 * text line — the same height a real [Text] measures, not a hand-tuned constant.
 */
@Composable
fun SkeletonBar(
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    if (textStyle == null) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    } else {
        Box(modifier = modifier) {
            Text(
                text = " ",
                style = textStyle,
                maxLines = 1,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // Inset the paint, not the measure: the bar still occupies one text line.
                    .padding(vertical = 3.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

/**
 * Skeleton rows on the same [SegmentedListItem] setup as `TrackRow`, so a row's height is the
 * real one's by construction: same 48 dp leading, bars at the text styles' line heights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingRows(
    modifier: Modifier = Modifier,
    count: Int = 6,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        repeat(count) { index ->
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.segmentedShapes(index, count),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                leadingContent = {
                    SkeletonBar(
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(48.dp),
                    )
                },
                content = {
                    SkeletonBar(
                        textStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(0.6f),
                    )
                },
                supportingContent = {
                    SkeletonBar(
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(0.35f),
                    )
                },
            )
        }
    }
}
