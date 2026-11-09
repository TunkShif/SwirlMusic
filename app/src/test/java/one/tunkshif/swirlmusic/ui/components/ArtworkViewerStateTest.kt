package one.tunkshif.swirlmusic.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The viewer's zoom/pan/dismiss maths, straight off the state holder. */
@RunWith(RobolectricTestRunner::class)
class ArtworkViewerStateTest {

    private fun state(w: Int = 1000, h: Int = 1000) = ArtworkViewerState().also {
        it.onSize(IntSize(w, h))
    }

    @Test
    fun `pinch zooms between 1x and 4x`() {
        val state = state()

        state.onTransform(Offset.Zero, 2f)
        assertThat(state.scale).isEqualTo(2f)
        assertThat(state.zoomed).isTrue()

        state.onTransform(Offset.Zero, 8f)
        assertThat(state.scale).isEqualTo(4f)

        state.onTransform(Offset.Zero, 0.01f)
        assertThat(state.scale).isEqualTo(1f)
        assertThat(state.zoomed).isFalse()
    }

    @Test
    fun `pan clamps to the scaled image bounds`() {
        val state = state()

        state.onTransform(Offset.Zero, 2f)
        state.onTransform(Offset(10_000f, 10_000f), 1f)

        // At 2x the half-extent of the overflow is (2-1)*size/2 = 500.
        assertThat(state.pan.x).isEqualTo(500f)
        assertThat(state.pan.y).isEqualTo(500f)
    }

    @Test
    fun `a 1x drag pulls down instead of panning`() {
        val state = state()

        state.onTransform(Offset(40f, 300f), 1f)

        assertThat(state.dismissOffset).isEqualTo(300f)
        assertThat(state.pan).isEqualTo(Offset.Zero)
    }

    @Test
    fun `double tap zooms to 2_5x around the tap and back`() {
        val state = state()

        state.onDoubleTap(Offset(750f, 250f))
        assertThat(state.scale).isEqualTo(2.5f)
        // The tap point is pulled toward the centre: (c-t)*(s-1) = (±250)*1.5.
        assertThat(state.pan).isEqualTo(Offset(-375f, 375f))

        state.onDoubleTap(Offset(500f, 500f))
        assertThat(state.scale).isEqualTo(1f)
        assertThat(state.pan).isEqualTo(Offset.Zero)
    }

    @Test
    fun `dismiss needs a fifth of the height or a hard fling`() {
        val state = state()

        state.onTransform(Offset(0f, 100f), 1f)
        assertThat(state.shouldDismiss(0f)).isFalse()

        state.onTransform(Offset(0f, 150f), 1f)
        assertThat(state.shouldDismiss(0f)).isTrue()

        val fling = state()
        fling.onTransform(Offset(0f, 10f), 1f)
        assertThat(fling.shouldDismiss(1_100f)).isTrue()
        assertThat(fling.shouldDismiss(500f)).isFalse()
    }
}
