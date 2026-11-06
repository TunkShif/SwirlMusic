package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

private const val COMPLETE_PROGRESS = 0.25f
private val COMPLETE_FLING = 1_200.dp

/**
 * Feeds a vertical down-drag into the navigation-event system as a back gesture with
 * [NavigationEvent.EDGE_NONE], so NavDisplay runs its predictive pop (screen underneath
 * composed) and the player follows the finger. Without a dispatcher (previews) the gesture
 * falls back to calling [onFallbackBack] once the drag passes the threshold.
 */
@Composable
internal fun Modifier.collapseOnDragDown(
    enabled: Boolean = true,
    onFallbackBack: () -> Unit,
): Modifier {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val input = remember { DirectNavigationEventInput() }
    DisposableEffect(dispatcher) {
        dispatcher?.addInput(input)
        onDispose { dispatcher?.removeInput(input) }
    }

    // Root height, not the handle's: the progress maps to how far the screen travels.
    val heightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    val flingPx = with(LocalDensity.current) { COMPLETE_FLING.toPx() }
    var dragPx by remember { mutableFloatStateOf(0f) }

    if (!enabled) return this

    return draggable(
        orientation = Orientation.Vertical,
        onDragStarted = {
            dragPx = 0f
            if (dispatcher != null) {
                input.backStarted(
                    NavigationEvent(
                        swipeEdge = NavigationEvent.EDGE_NONE,
                        touchX = it.x,
                        touchY = it.y,
                        progress = 0f,
                    ),
                )
            }
        },
        state = rememberDraggableState { delta ->
            dragPx = (dragPx + delta).coerceAtLeast(0f)
            if (dispatcher != null) {
                input.backProgressed(
                    NavigationEvent(
                        swipeEdge = NavigationEvent.EDGE_NONE,
                        progress = (dragPx / heightPx).coerceIn(0f, 1f),
                    ),
                )
            }
        },
        onDragStopped = { velocity ->
            val progress = dragPx / heightPx
            if (dispatcher != null) {
                if (progress > COMPLETE_PROGRESS || velocity > flingPx) {
                    input.backCompleted()
                } else {
                    input.backCancelled()
                }
            } else if (progress > COMPLETE_PROGRESS || velocity > flingPx) {
                onFallbackBack()
            }
            dragPx = 0f
        },
    )
}
