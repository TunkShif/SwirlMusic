package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.components.Artwork
import one.tunkshif.swirlmusic.ui.theme.Spacing

internal enum class CoverSlot { Previous, Current, Next }

/** Measures the child [bleed] wider on each side but reports the original width — the clip region
 * reaches into the screen's side padding so neighbours slide off the physical edge while the
 * cover keeps its usual span. */
private fun Modifier.bleedHorizontally(bleed: Dp) = layout { measurable, constraints ->
    val extra = (bleed * 2).toPx().roundToInt()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * The Now Playing cover as a horizontal carousel: dragging reveals the previous/next cover and
 * settling on one skips to it; settling back leaves the current track. [coverModifier] carries
 * the current cover's sizing/shared-element/clip/click chain.
 *
 * Neighbours are measured copies of the current cover: their size is read off the current
 * Artwork, not the container, so on short screens where the cover is narrower than the slot
 * they stay square and the drag step matches one cover width + gap.
 */
@Composable
internal fun CoverCarousel(
    current: NowPlayingTrack,
    currentUid: Long?,
    previous: NowPlayingCover?,
    next: NowPlayingCover?,
    onSkipTo: (Long) -> Unit,
    coverModifier: Modifier,
    modifier: Modifier = Modifier,
    /** Equal to the screen's side padding: neighbours bleed into it so covers exit at the edge. */
    bleed: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val state = remember {
        AnchoredDraggableState(initialValue = CoverSlot.Current)
    }
    val fling = AnchoredDraggableDefaults.flingBehavior(
        state = state,
        positionalThreshold = { totalDistance: Float -> totalDistance * 0.3f },
        animationSpec = tween(),
    )

    val gapPx = with(density) { Spacing.lg.toPx() }
    var coverSize by remember { mutableStateOf(IntSize.Zero) }
    val step = coverSize.width + gapPx

    LaunchedEffect(step, previous != null, next != null) {
        if (step <= 0f) return@LaunchedEffect
        state.updateAnchors(
            DraggableAnchors {
                previous?.let { CoverSlot.Previous at step }
                CoverSlot.Current at 0f
                next?.let { CoverSlot.Next at -step }
            },
        )
    }

    // Settling on a neighbour skips to it; the uid change snaps back to Current seamlessly.
    LaunchedEffect(state.settledValue) {
        when (state.settledValue) {
            CoverSlot.Previous -> previous?.let { onSkipTo(it.uid) }
            CoverSlot.Next -> next?.let { onSkipTo(it.uid) }
            CoverSlot.Current -> Unit
        }
    }
    LaunchedEffect(currentUid) {
        if (state.currentValue != CoverSlot.Current) state.snapTo(CoverSlot.Current)
    }
    // If the skip never landed (failed request), slide back to Current.
    LaunchedEffect(state.settledValue, currentUid) {
        val settled = state.settledValue
        if (settled == CoverSlot.Current) return@LaunchedEffect
        delay(1_500)
        if (state.settledValue == settled) state.animateTo(CoverSlot.Current)
    }

    val prevLabel = stringResource(R.string.playing_previous)
    val nextLabel = stringResource(R.string.playing_next)
    val neighbourModifier = with(density) {
        Modifier.size(coverSize.width.toDp(), coverSize.height.toDp())
    }

    Box(
        modifier = modifier
            .bleedHorizontally(bleed)
            .clipToBounds()
            .anchoredDraggable(state, Orientation.Horizontal, flingBehavior = fling)
            .padding(horizontal = bleed),
        contentAlignment = Alignment.Center,
    ) {
        val offset = state.offset.takeIf { !it.isNaN() } ?: 0f

        // Neighbours compose only while dragged into view — no extra image loads at rest.
        if (offset > 0f && previous != null) {
            Artwork(
                url = previous.artworkUrl,
                placeholderUrl = previous.placeholderArtworkUrl,
                contentDescription = null,
                modifier = neighbourModifier
                    .offset { IntOffset((offset - step).roundToInt(), 0) }
                    .clip(MaterialTheme.shapes.extraLarge),
            )
        }
        if (offset < 0f && next != null) {
            Artwork(
                url = next.artworkUrl,
                placeholderUrl = next.placeholderArtworkUrl,
                contentDescription = null,
                modifier = neighbourModifier
                    .offset { IntOffset((offset + step).roundToInt(), 0) }
                    .clip(MaterialTheme.shapes.extraLarge),
            )
        }

        Artwork(
            url = current.artworkUrl,
            placeholderUrl = current.placeholderArtworkUrl,
            contentDescription = null,
            // The offset wraps the whole cover chain so clip/shared-bounds/clickable travel
            // with it — the resting clip must not crop the translated cover.
            modifier = Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .then(coverModifier)
                .onSizeChanged { coverSize = it }
                .semantics {
                    customActions = buildList {
                        previous?.let {
                            add(CustomAccessibilityAction(prevLabel) { onSkipTo(it.uid); true })
                        }
                        next?.let {
                            add(CustomAccessibilityAction(nextLabel) { onSkipTo(it.uid); true })
                        }
                    }
                },
        )
    }
}
