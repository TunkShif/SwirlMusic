package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.player.LoopMode
import one.tunkshif.swirlmusic.player.next
import one.tunkshif.swirlmusic.player.PlaybackError
import one.tunkshif.swirlmusic.ui.components.Artwork
import one.tunkshif.swirlmusic.ui.components.EmptyState
import one.tunkshif.swirlmusic.ui.components.LocalSharedTransitionScope
import one.tunkshif.swirlmusic.ui.components.nowPlayingArtwork
import one.tunkshif.swirlmusic.ui.format.formatDuration
import one.tunkshif.swirlmusic.ui.theme.Spacing
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

internal const val PLAYING_TOP_ROW_TAG = "playingTopRow"
internal const val PLAYING_COVER_TAG = "playingCover"

/** Sizes on this screen. Spacing comes from the theme; these are local to the player. */

internal object NowPlayingDimens {
    val actionSmall = 48.dp
    val actionLarge = 56.dp
    val playButton = 96.dp
    val seekThumbWidth = 4.dp
    val seekThumbHeight = 28.dp
    val seekGap = 8.dp
    /** Matches `shapes.extraLarge`: the resting cover corner. */
    val coverCorner = 32.dp
    val toastIcon = 18.dp
}

/** The entry point wired into the shell's nav graph. */
@Composable
fun NowPlayingRoute(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenQueue: () -> Unit = {},
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    NowPlayingScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenAlbum = onOpenAlbum,
        onOpenQueue = onOpenQueue,
        lyrics = lyrics,
        showLyrics = showLyrics,
        onToggleLyrics = { showLyrics = !showLyrics },
        modifier = modifier,
    )
}

@Composable
fun NowPlayingScreen(
    state: NowPlayingUiState,
    onEvent: (NowPlayingEvent) -> Unit,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    lyrics: LyricsUiState = LyricsUiState.Loading,
    showLyrics: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    toastState: PlayerToastState = rememberPlayerToastState(),
) {
    // One shared position source for the seek bar, the elapsed label and the lyrics pane.
    val displayed = rememberDisplayedPosition(state.positionMs, state.durationMs, state.isPlaying)
    val seekTo: (Long) -> Unit = { target ->
        displayed.onSeeked(target)
        onEvent(NowPlayingEvent.SeekTo(target))
    }
    // Reading LocalNavAnimatedContentScope outside a NavEntry throws; it is only needed when a
    // shared transition exists, which previews and tests never provide.
    val navAnimatedScope =
        if (LocalSharedTransitionScope.current != null) LocalNavAnimatedContentScope.current else null

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.xl),
        ) {
            TopRow(
                onBack = onBack,
                hasTrack = state.track != null,
                showLyrics = showLyrics,
                onToggleLyrics = onToggleLyrics,
                modifier = Modifier
                    .collapseOnDragDown(onFallbackBack = onBack)
                    .testTag(PLAYING_TOP_ROW_TAG),
            )
            val track = state.track
            if (track == null) {
                EmptyState(
                    icon = Icons.Default.PlayArrow,
                    title = stringResource(R.string.playing_empty_title),
                    body = stringResource(R.string.playing_empty_body),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag(PLAYING_COVER_TAG),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = showLyrics,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "coverLyrics",
                    ) { lyricsShown ->
                        if (lyricsShown) {
                            LyricsPane(
                                state = lyrics,
                                position = { displayed.ms },
                                onSeek = seekTo,
                                onRetry = { onEvent(NowPlayingEvent.RetryLyrics) },
                                onCycleExtra = { target ->
                                    toastState.show(extraIconRes(target), extraToastRes(target))
                                    onEvent(NowPlayingEvent.CycleLyricsExtra)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = Spacing.lg),
                            )
                        } else {
                            CoverCarousel(
                                current = track,
                                currentUid = state.currentUid,
                                previous = state.previous,
                                next = state.next,
                                onSkipTo = { uid -> onEvent(NowPlayingEvent.SkipToCover(uid)) },
                                coverModifier = Modifier
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                    .nowPlayingArtwork(
                                        navAnimatedScope,
                                        visibleCorner = NowPlayingDimens.coverCorner,
                                    )
                                    // The clip lives outside so the shared element morphs the corners.
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .clickable(
                                        onClickLabel = stringResource(R.string.playing_show_lyrics),
                                        role = Role.Button,
                                    ) { onToggleLyrics() },
                                // bleed must stay equal to the Column's horizontal padding
                                // (Spacing.xl) so covers exit at the physical screen edge.
                                bleed = Spacing.xl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.lg)
                                    // The carousel's horizontal drag coexists with a
                                    // vertical collapse drag on different axes.
                                    .collapseOnDragDown(onFallbackBack = onBack),
                            )
                        }
                    }
                    PlayerToastHost(
                        state = toastState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = Spacing.lg),
                    )
                }
                TrackText(track, onOpenAlbum, isTrial = state.isTrial)
                Spacer(Modifier.size(Spacing.xl))
                SeekBlock(state, onEvent, displayed, seekTo)
                Spacer(Modifier.size(Spacing.md))
                if (state.error != null) {
                    Text(
                        text = stringResource(errorText(state.error)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(Spacing.sm))
                }
                TransportRow(
                    state = state,
                    onEvent = onEvent,
                    onOpenQueue = onOpenQueue,
                    toastState = toastState,
                )
                Spacer(Modifier.size(Spacing.xl))
            }
        }
    }
}

@Composable
private fun TopRow(
    onBack: () -> Unit,
    hasTrack: Boolean = false,
    showLyrics: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.size(NowPlayingDimens.actionSmall),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                contentDescription = stringResource(R.string.playing_collapse),
            )
        }
        Text(
            text = stringResource(R.string.playing_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (hasTrack) {
            FilledTonalIconToggleButton(
                checked = showLyrics,
                onCheckedChange = { onToggleLyrics() },
                modifier = Modifier.size(NowPlayingDimens.actionSmall),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lyrics),
                    contentDescription = stringResource(R.string.playing_lyrics),
                )
            }
        } else {
            Spacer(Modifier.width(NowPlayingDimens.actionSmall))
        }
    }
}

@Composable
private fun TrackText(
    track: NowPlayingTrack,
    onOpenAlbum: (Long) -> Unit,
    isTrial: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .basicMarquee(),
            )
            // A later slice draws a like button here; the space is already reserved.
            Spacer(Modifier.width(NowPlayingDimens.actionSmall))
        }
        Text(
            text = track.artistLine,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val albumName = track.albumName
        if (!albumName.isNullOrBlank()) {
            val albumId = track.albumId
            Text(
                text = albumName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (albumId != null) {
                    Modifier.clickable(
                        onClickLabel = stringResource(R.string.playing_open_album),
                        role = Role.Button,
                    ) { onOpenAlbum(albumId) }
                } else {
                    Modifier
                },
            )
        }
        if (isTrial) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.mini_player_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun SeekBlock(
    state: NowPlayingUiState,
    onEvent: (NowPlayingEvent) -> Unit,
    displayed: DisplayedPosition,
    seekTo: (Long) -> Unit,
) {
    val elapsedSeconds by remember { derivedStateOf { displayed.ms / 1_000 } }
    val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    Column {
        WavySeekBar(
            position = { displayed.ms },
            durationMs = state.durationMs,
            isPlaying = state.isPlaying,
            enabled = state.durationMs > 0 && !state.isLoading,
            onSeek = seekTo,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(elapsedSeconds * 1_000),
                style = timeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDuration(state.durationMs),
                style = timeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransportRow(
    state: NowPlayingUiState,
    onEvent: (NowPlayingEvent) -> Unit,
    onOpenQueue: () -> Unit,
    toastState: PlayerToastState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = {
                // Announce the target mode, not the current one.
                val target = state.loopMode.next()
                toastState.show(loopIcon(target), loopDescription(target))
                onEvent(NowPlayingEvent.CycleLoopMode)
            },
            modifier = Modifier.size(NowPlayingDimens.actionSmall),
        ) {
            Icon(
                painter = painterResource(loopIcon(state.loopMode)),
                contentDescription = stringResource(loopDescription(state.loopMode)),
            )
        }
        FilledTonalIconButton(
            onClick = { onEvent(NowPlayingEvent.Previous) },
            enabled = state.track != null,
            modifier = Modifier.size(NowPlayingDimens.actionLarge),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.playing_previous),
            )
        }
        FilledIconToggleButton(
            checked = state.isPlaying,
            onCheckedChange = { onEvent(NowPlayingEvent.PlayPause) },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(NowPlayingDimens.playButton),
        ) {
            if (state.isLoading) {
                LoadingIndicator(
                    color = LocalContentColor.current,
                    modifier = Modifier.size(NowPlayingDimens.actionSmall),
                )
            } else {
                Icon(
                    painter = painterResource(
                        if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    ),
                    contentDescription = stringResource(
                        if (state.isPlaying) R.string.playing_pause else R.string.playing_play,
                    ),
                    modifier = Modifier.size(NowPlayingDimens.actionSmall),
                )
            }
        }
        FilledTonalIconButton(
            onClick = { onEvent(NowPlayingEvent.Next) },
            enabled = state.hasNext,
            modifier = Modifier.size(NowPlayingDimens.actionLarge),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.playing_next),
            )
        }
        FilledTonalIconButton(
            onClick = onOpenQueue,
            modifier = Modifier.size(NowPlayingDimens.actionSmall),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_queue_music),
                contentDescription = stringResource(R.string.playing_queue),
            )
        }
    }
}

private fun loopIcon(mode: LoopMode): Int = when (mode) {
    LoopMode.ListLoop -> R.drawable.ic_repeat
    LoopMode.Shuffle -> R.drawable.ic_shuffle
    LoopMode.RepeatOne -> R.drawable.ic_repeat_one
}

private fun loopDescription(mode: LoopMode): Int = when (mode) {
    LoopMode.ListLoop -> R.string.playing_loop_list
    LoopMode.Shuffle -> R.string.playing_loop_shuffle
    LoopMode.RepeatOne -> R.string.playing_loop_one
}

private fun errorText(error: PlaybackError): Int = when (error) {
    PlaybackError.Unavailable -> R.string.playing_error_unavailable
    PlaybackError.Network -> R.string.playing_error_network
    PlaybackError.Server -> R.string.playing_error_server
}

@Preview(showBackground = true)
@Composable
private fun NowPlayingScreenPreview() {
    SwirlMusicTheme(dynamicColor = false) {
        NowPlayingScreen(
            state = NowPlayingUiState(
                track = NowPlayingTrack(
                    title = "Nightcall",
                    artistLine = "Kavinsky",
                    albumName = "OutRun",
                    albumId = 601,
                    artworkUrl = null,
                    placeholderArtworkUrl = null,
                ),
                isPlaying = true,
                positionMs = 42_000,
                durationMs = 258_000,
                hasNext = true,
            ),
            onEvent = {},
            onBack = {},
            onOpenAlbum = {},
            onOpenQueue = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NowPlayingEmptyPreview() {
    SwirlMusicTheme(dynamicColor = false) {
        NowPlayingScreen(
            state = NowPlayingUiState(),
            onEvent = {},
            onBack = {},
            onOpenAlbum = {},
            onOpenQueue = {},
        )
    }
}
