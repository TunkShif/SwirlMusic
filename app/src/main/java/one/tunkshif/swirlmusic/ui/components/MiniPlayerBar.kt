package one.tunkshif.swirlmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.player.MiniPlayerUiState
import one.tunkshif.swirlmusic.ui.theme.Spacing
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

/** Stable tag for tests: the bar is reachable from every tab. */
const val MINI_PLAYER_TAG = "mini_player"

/**
 * The mini player: its own coloured pill floating above the navigation bar. Tap opens the
 * playing screen; the transport button starts or pauses, ringed by a progress indicator.
 */
@Composable
fun MiniPlayerBar(
    state: MiniPlayerUiState,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MINI_PLAYER_TAG)
                .clickable(onClick = onOpen)
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Artwork(
                url = state.artworkUrl,
                contentDescription = null,
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.isTrial) {
                        stringResource(R.string.mini_player_preview)
                    } else {
                        state.subtitle.orEmpty()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        strokeWidth = 3.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        strokeWidth = 3.dp,
                    )
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (state.isPlaying) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pause),
                            contentDescription = stringResource(R.string.mini_player_pause),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.mini_player_play),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MiniPlayerBarPreview() {
    SwirlMusicTheme(dynamicColor = false) {
        Surface(modifier = Modifier.padding(Spacing.md)) {
            MiniPlayerBar(
                state = MiniPlayerUiState(
                    title = "Nightcall",
                    subtitle = "Kavinsky",
                    isPlaying = true,
                    progress = 0.35f,
                ),
                onPlayPause = {},
                onOpen = {},
            )
        }
    }
}
