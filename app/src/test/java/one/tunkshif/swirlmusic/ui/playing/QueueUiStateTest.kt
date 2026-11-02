package one.tunkshif.swirlmusic.ui.playing

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.player.LoopMode
import one.tunkshif.swirlmusic.player.PlaybackState
import one.tunkshif.swirlmusic.player.QueueEntry
import org.junit.Test

class QueueUiStateTest {

    private fun track(id: Long) = Track(id, "t$id", "a$id", null, 0)

    private fun state(
        count: Int,
        currentIndex: Int = 0,
        loopMode: LoopMode = LoopMode.ListLoop,
        playOrder: List<Int>? = null,
    ) = PlaybackState(
        queue = (0 until count).map { QueueEntry(it.toLong(), track(it.toLong())) },
        currentIndex = currentIndex,
        loopMode = loopMode,
        playOrder = playOrder ?: List(count) { it },
    )

    @Test
    fun `identity order maps the queue in order`() {
        val ui = state(3).toQueueUiState()

        assertThat(ui.items.map { it.uid }).containsExactly(0L, 1L, 2L).inOrder()
        assertThat(ui.canReorder).isTrue()
    }

    @Test
    fun `a shuffled play order reorders the items and blocks reorder`() {
        val ui = state(3, loopMode = LoopMode.Shuffle, playOrder = listOf(2, 0, 1)).toQueueUiState()

        assertThat(ui.items.map { it.uid }).containsExactly(2L, 0L, 1L).inOrder()
        assertThat(ui.canReorder).isFalse()
    }

    @Test
    fun `currentUid follows the current index`() {
        assertThat(state(3, currentIndex = 1).toQueueUiState().currentUid).isEqualTo(1L)
        assertThat(state(3, currentIndex = -1).toQueueUiState().currentUid).isNull()
    }

    @Test
    fun `an out-of-range play order index is skipped`() {
        val ui = state(2, playOrder = listOf(0, 5, 1)).toQueueUiState()

        assertThat(ui.items.map { it.uid }).containsExactly(0L, 1L).inOrder()
    }

    @Test
    fun `a single item cannot reorder`() {
        assertThat(state(1).toQueueUiState().canReorder).isFalse()
    }
}
