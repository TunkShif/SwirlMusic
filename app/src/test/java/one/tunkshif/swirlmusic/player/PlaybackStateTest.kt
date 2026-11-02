package one.tunkshif.swirlmusic.player

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.Track
import org.junit.Test

class PlaybackStateTest {

    private val trackA = Track(1, "A", "Artist A", null, 0)
    private val trackB = Track(2, "B", "Artist B", null, 0)

    private fun entry(uid: Long, track: Track) = QueueEntry(uid, track)

    @Test
    fun `current prefers the pending track over the queue`() {
        val state = PlaybackState(
            queue = listOf(entry(0, trackA)),
            currentIndex = 0,
            pending = trackB,
        )
        assertThat(state.current).isEqualTo(trackB)
    }

    @Test
    fun `current follows the queue without a pending track`() {
        val state = PlaybackState(queue = listOf(entry(0, trackA), entry(1, trackB)), currentIndex = 1)
        assertThat(state.current).isEqualTo(trackB)
    }

    @Test
    fun `isLoading is true while pending or buffering`() {
        assertThat(PlaybackState(pending = trackA).isLoading).isTrue()
        assertThat(PlaybackState(isBuffering = true).isLoading).isTrue()
        assertThat(PlaybackState(queue = listOf(entry(0, trackA)), currentIndex = 0).isLoading).isFalse()
    }

    @Test
    fun `loopMode cycles through the three modes`() {
        assertThat(LoopMode.ListLoop.next()).isEqualTo(LoopMode.Shuffle)
        assertThat(LoopMode.Shuffle.next()).isEqualTo(LoopMode.RepeatOne)
        assertThat(LoopMode.RepeatOne.next()).isEqualTo(LoopMode.ListLoop)
    }

    @Test
    fun `loopModeOf maps every flag combo to a mode`() {
        assertThat(loopModeOf(Player.REPEAT_MODE_OFF, shuffle = false)).isEqualTo(LoopMode.ListLoop)
        assertThat(loopModeOf(Player.REPEAT_MODE_ALL, shuffle = false)).isEqualTo(LoopMode.ListLoop)
        assertThat(loopModeOf(Player.REPEAT_MODE_ONE, shuffle = false)).isEqualTo(LoopMode.RepeatOne)
        assertThat(loopModeOf(Player.REPEAT_MODE_OFF, shuffle = true)).isEqualTo(LoopMode.Shuffle)
        assertThat(loopModeOf(Player.REPEAT_MODE_ALL, shuffle = true)).isEqualTo(LoopMode.Shuffle)
        assertThat(loopModeOf(Player.REPEAT_MODE_ONE, shuffle = true)).isEqualTo(LoopMode.Shuffle)
    }
}
