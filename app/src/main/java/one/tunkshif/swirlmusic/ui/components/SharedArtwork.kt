package one.tunkshif.swirlmusic.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** Shared element key for the mini-player -> Now Playing cover morph. */
const val NOW_PLAYING_ARTWORK_KEY = "now_playing_artwork"

/** Null outside the shell: previews, tests, and goldens see a no-op modifier. */
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    staticCompositionLocalOf { null }

/**
 * Marks artwork as the shared element morphing between the mini player's circle and the Now
 * Playing cover. The overlay clip's corner radius lerps in pixels between a true circle
 * (`min(w, h) / 2`) and [visibleCorner] as the transition fraction runs, so it lands exactly on
 * the resting shape. No-op when either scope is absent, so previews, goldens, and tests need no
 * transition.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@androidx.compose.runtime.Composable
fun Modifier.nowPlayingArtwork(
    animatedVisibilityScope: AnimatedVisibilityScope?,
    visibleCorner: Dp,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    if (animatedVisibilityScope == null) return this

    val fraction by animatedVisibilityScope.transition
        .animateFloat(
            transitionSpec = { tween(durationMillis = 300) },
            label = "artworkCorner",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }

    val cornerShape = object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val radiusPx = androidx.compose.ui.util.lerp(
                size.minDimension / 2f,
                with(density) { visibleCorner.toPx() },
                fraction,
            )
            return RoundedCornerShape(radiusPx).createOutline(size, layoutDirection, density)
        }
    }

    return with(sharedScope) {
        this@nowPlayingArtwork.sharedBounds(
            sharedContentState = rememberSharedContentState(NOW_PLAYING_ARTWORK_KEY),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = fadeIn(),
            exit = fadeOut(),
            boundsTransform = { _, _ -> tween(durationMillis = 300) },
            clipInOverlayDuringTransition = OverlayClip(cornerShape),
        )
    }
}
