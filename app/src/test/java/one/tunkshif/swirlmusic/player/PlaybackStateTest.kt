package one.tunkshif.swirlmusic.player

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.Track
import org.junit.Test

class PlaybackStateTest {

    private val trackA = Track(1, "A", "Artist A", null, 0)
    private val trackB = Track(2, "B", "Artist B", null, 0)

    @Test
    fun `current prefers the pending track over the queue`() {
        val state = PlaybackState(
            queue = listOf(trackA),
            currentIndex = 0,
            pending = trackB,
        )
        assertThat(state.current).isEqualTo(trackB)
    }

    @Test
    fun `current follows the queue without a pending track`() {
        val state = PlaybackState(queue = listOf(trackA, trackB), currentIndex = 1)
        assertThat(state.current).isEqualTo(trackB)
    }

    @Test
    fun `isLoading is true while pending or buffering`() {
        assertThat(PlaybackState(pending = trackA).isLoading).isTrue()
        assertThat(PlaybackState(isBuffering = true).isLoading).isTrue()
        assertThat(PlaybackState(queue = listOf(trackA), currentIndex = 0).isLoading).isFalse()
    }
}
