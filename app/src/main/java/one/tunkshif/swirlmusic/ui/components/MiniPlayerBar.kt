package one.tunkshif.swirlmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
 * The mini player above the bottom bar. Tap opens the playing screen; the transport button starts
 * or pauses.
 */
@Composable
fun MiniPlayerBar(
    state: MiniPlayerUiState,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MINI_PLAYER_TAG),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Artwork(
                    url = state.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title ?: stringResource(R.string.mini_player_idle_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (state.isTrial) {
                            stringResource(R.string.mini_player_preview)
                        } else {
                            state.subtitle ?: stringResource(R.string.mini_player_idle_subtitle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = onPlayPause) {
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

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MiniPlayerBarPreview() {
    SwirlMusicTheme(dynamicColor = false) {
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
