package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToLong
import one.tunkshif.swirlmusic.R

/**
 * Test seam for goldens: a fixed clock freezes extrapolation, like [LocalArtworkPainter] does for
 * images. Production never sets it.
 */
internal val LocalPositionClock = compositionLocalOf<(() -> Long)?> { null }

internal const val SEEK_CAUGHT_UP_MS = 1_000L
internal const val SEEK_HOLD_TIMEOUT_MS = 2_000L
internal const val REBASE_THRESHOLD_MS = 300L

/** A released drag target: hold it on screen until the player reports catching up. */
internal data class SeekHold(val targetMs: Long, val releasedAtMs: Long)

/** What the seek bar shows: the drag while dragging, the hold target until caught up. */
internal fun displayedPositionMs(
    reportedMs: Long,
    dragMs: Long?,
    hold: SeekHold?,
    nowMs: Long,
): Long = when {
    dragMs != null -> dragMs
    hold != null -> hold.targetMs
    else -> reportedMs
}

/** Drop the hold once the reported position reaches the target, or it times out. */
internal fun SeekHold?.releaseIfCaughtUp(reportedMs: Long, nowMs: Long): SeekHold? = when {
    this == null -> null
    abs(reportedMs - targetMs) <= SEEK_CAUGHT_UP_MS -> null
    nowMs - releasedAtMs >= SEEK_HOLD_TIMEOUT_MS -> null
    else -> this
}

/** A new report beyond the jitter threshold re-bases extrapolation; below it, it is tick noise. */
internal fun shouldRebasePosition(reportedMs: Long, extrapolatedMs: Long): Boolean =
    abs(reportedMs - extrapolatedMs) > REBASE_THRESHOLD_MS

/**
 * The seek position the bar and the elapsed label share. The controller reports every 500 ms;
 * between ticks this extrapolates on a frame clock so the wave doesn't stutter, and it holds a
 * released drag target until the player reports catching up (no jump-back).
 */
@Stable
internal class DisplayedPosition(
    initialMs: Long,
    /** One monotonic source for base, hold and frames — frame and wall clocks do not share a base. */
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    private val _ms = mutableLongStateOf(initialMs)
    val ms: Long get() = _ms.longValue

    private var baseMs = initialMs
    private var baseFrameMs = now()
    private var hold: SeekHold? = null

    /** The user dropped the thumb: show the target until the player catches up. */
    fun onSeeked(targetMs: Long) {
        hold = SeekHold(targetMs, now())
        baseMs = targetMs
        baseFrameMs = now()
        _ms.longValue = targetMs
    }

    /**
     * A fresh report from the controller. Paused playback always re-bases so a resume does not
     * extrapolate across the whole pause; while playing only a drift beyond the jitter
     * threshold re-bases. A held seek target is never re-based away by a stale report.
     */
    fun onReported(reportedMs: Long, durationMs: Long, isPlaying: Boolean) {
        val nowMs = now()
        hold = hold.releaseIfCaughtUp(reportedMs, nowMs)
        if (!isPlaying && hold == null) {
            baseMs = reportedMs
            baseFrameMs = nowMs
        } else if (hold == null) {
            val extrapolated = baseMs + (nowMs - baseFrameMs).coerceAtLeast(0)
            if (shouldRebasePosition(reportedMs, extrapolated)) {
                baseMs = reportedMs
                baseFrameMs = nowMs
            }
        }
        _ms.longValue = displayedPositionMs(reportedMs, dragMs = null, hold = hold, nowMs = nowMs)
            .coerceIn(0, durationMs.coerceAtLeast(0))
    }

    /** One frame while playing: extrapolate from the base, or show the held target. */
    fun onFrame(reportedMs: Long, durationMs: Long) {
        val nowMs = now()
        hold = hold.releaseIfCaughtUp(reportedMs, nowMs)
        _ms.longValue = displayedPositionMs(
            reportedMs = baseMs + (nowMs - baseFrameMs).coerceAtLeast(0),
            dragMs = null,
            hold = hold,
            nowMs = nowMs,
        ).coerceIn(0, durationMs.coerceAtLeast(0))
    }
}

/**
 * The position the seek bar and the time label both draw. Paused, it is simply the reported
 * value; playing, a frame clock extrapolates between the controller's 500 ms ticks.
 */
@Composable
internal fun rememberDisplayedPosition(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
): DisplayedPosition {
    val clock = LocalPositionClock.current
    val displayed = remember {
        DisplayedPosition(positionMs, now = clock ?: { System.nanoTime() / 1_000_000 })
    }
    LaunchedEffect(positionMs, durationMs, isPlaying) {
        displayed.onReported(positionMs, durationMs, isPlaying)
        if (!isPlaying) return@LaunchedEffect
        // The infinite-animation frame clock lets test environments pause this loop.
        while (true) {
            withInfiniteAnimationFrameMillis {
                displayed.onFrame(positionMs, durationMs)
            }
        }
    }
    return displayed
}

/**
 * The M3 wavy seek bar: a `Slider` for drag, tap-to-seek and `SetProgress` semantics, with a
 * `LinearWavyProgressIndicator` as its track. The wave flattens while paused or disabled.
 * `onSeek` fires only when the drag ends, never during it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySeekBar(
    position: () -> Long,
    durationMs: Long,
    isPlaying: Boolean,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // The range is fixed at construction, so a track with a new duration gets a new state.
    val sliderState = remember(durationMs) {
        SliderState(0f, 0, 0f..durationMs.coerceAtLeast(1).toFloat())
    }
    // Reads the position outside composition, so frame-by-frame updates move the thumb without
    // recomposing the bar.
    LaunchedEffect(sliderState) {
        snapshotFlow { position() }.collect { ms ->
            if (!sliderState.isDragging) {
                sliderState.value = ms.toFloat().coerceIn(0f, durationMs.toFloat())
            }
        }
    }

    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying && enabled && !sliderState.isDragging) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "seekAmplitude",
    )
    val seekDescription = stringResource(R.string.playing_seek)

    Slider(
        state = sliderState,
        modifier = modifier.semantics { contentDescription = seekDescription },
        enabled = enabled,
        interactionSource = interactionSource,
        onValueChange = { sliderState.value = it.coerceIn(0f, durationMs.toFloat()) },
        onValueChangeFinished = {
            onSeek(sliderState.value.roundToLong().coerceIn(0, durationMs))
        },
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                enabled = enabled,
                thumbSize = DpSize(NowPlayingDimens.seekThumbWidth, NowPlayingDimens.seekThumbHeight),
            )
        },
        track = { state ->
            Box(modifier = Modifier.fillMaxWidth()) {
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { (sliderState.value / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                    gapSize = NowPlayingDimens.seekGap,
                    stopSize = WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
                    amplitude = { p -> WavyProgressIndicatorDefaults.indicatorAmplitude(p) * amplitude },
                    wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
                )
            }
        },
    )
}
