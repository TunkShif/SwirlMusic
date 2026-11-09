package one.tunkshif.swirlmusic.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.player.MiniPlayerUiState
import one.tunkshif.swirlmusic.ui.theme.Spacing
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

/** Stable tag for tests: the bar is reachable from every tab. */
const val MINI_PLAYER_TAG = "mini_player"

private object MiniPlayerLift {
    val maxLift = 24.dp
    /** Lift distance that expands; about 32 dp of finger travel at the 0.5 dampening. */
    val threshold = 16.dp
    val fling = 1_000.dp
}

/**
 * The mini player: its own coloured pill floating above the navigation bar. Tap opens the
 * playing screen; the transport button starts or pauses, ringed by a progress indicator.
 * Swiping the title sideways skips a track.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayerBar(
    state: MiniPlayerUiState,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenQueue: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    // A small lift hint: past the threshold or a quick upward fling expands the player.
    val lift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val maxLiftPx = with(LocalDensity.current) { MiniPlayerLift.maxLift.toPx() }
    val thresholdPx = with(LocalDensity.current) { MiniPlayerLift.threshold.toPx() }
    val flingPx = with(LocalDensity.current) { MiniPlayerLift.fling.toPx() }

    // The title/subtitle slide out on a committed swipe (-1 left = next, +1 right = previous)
    // and the new track's text slides in from the other side once [state.trackKey] changes.
    val textOffset = remember { Animatable(0f) }
    var textWidthPx by remember { mutableIntStateOf(0) }
    var swipeSkip by remember { mutableIntStateOf(0) }
    val slideSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    LaunchedEffect(state.trackKey) {
        if (swipeSkip != 0 && textWidthPx > 0) {
            textOffset.snapTo(-swipeSkip * textWidthPx.toFloat())
            textOffset.animateTo(0f, slideSpec)
            swipeSkip = 0
        }
    }
    // The track never changed (skip failed): slide the text back in, like CoverCarousel.
    LaunchedEffect(swipeSkip) {
        if (swipeSkip == 0) return@LaunchedEffect
        delay(1_500)
        if (swipeSkip != 0) {
            textOffset.animateTo(0f, slideSpec)
            swipeSkip = 0
        }
    }

    val previousLabel = stringResource(R.string.playing_previous)
    val nextLabel = stringResource(R.string.playing_next)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // A layout offset keeps the shared artwork's bounds truthful during the lift.
            .offset { IntOffset(0, lift.value.roundToInt()) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        lift.snapTo((lift.value + delta * 0.5f).coerceIn(-maxLiftPx, 0f))
                    }
                },
                onDragStopped = { velocity ->
                    scope.launch {
                        if (lift.value <= -thresholdPx || velocity < -flingPx) onOpen()
                        lift.animateTo(0f, spring())
                    }
                },
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MINI_PLAYER_TAG)
                .clickable(onClick = onOpen)
                .padding(Spacing.sm)
                .semantics {
                    customActions = buildList {
                        if (state.hasPrevious) {
                            add(CustomAccessibilityAction(previousLabel) { onPrevious(); true })
                        }
                        if (state.hasNext) {
                            add(CustomAccessibilityAction(nextLabel) { onNext(); true })
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Artwork(
                url = state.artworkUrl,
                contentDescription = null,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .nowPlayingArtwork(animatedVisibilityScope, visibleCorner = 24.dp),
            )

            // Only this column drags horizontally; the clip keeps sliding text inside its
            // bounds so it never draws over the artwork or the buttons.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
                    .onSizeChanged { textWidthPx = it.width }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                val width = textWidthPx.toFloat()
                                textOffset.snapTo((textOffset.value + delta).coerceIn(-width, width))
                            }
                        },
                        onDragStopped = { velocity ->
                            scope.launch {
                                val width = textWidthPx.toFloat()
                                val parked = swipeSkip
                                if (parked != 0) {
                                    // A skip is already in flight: keep the text parked.
                                    textOffset.animateTo(parked * width, slideSpec)
                                    return@launch
                                }
                                val direction = sign(
                                    textOffset.value.takeIf { it != 0f } ?: velocity,
                                ).toInt()
                                val target = when {
                                    direction < 0 -> state.hasNext
                                    direction > 0 -> state.hasPrevious
                                    else -> false
                                }
                                val committed = width > 0f && target && (
                                    abs(textOffset.value) > width / 3f ||
                                        (abs(velocity) > flingPx && sign(velocity) == direction.toFloat())
                                    )
                                if (committed) {
                                    textOffset.animateTo(direction * width, slideSpec)
                                    swipeSkip = direction
                                    if (direction < 0) onNext() else onPrevious()
                                } else {
                                    textOffset.animateTo(0f, spring())
                                }
                            }
                        },
                    ),
            ) {
                Column(
                    modifier = Modifier.offset { IntOffset(textOffset.value.roundToInt(), 0) },
                ) {
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
            }

            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_queue_music),
                    contentDescription = stringResource(R.string.playing_queue),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
