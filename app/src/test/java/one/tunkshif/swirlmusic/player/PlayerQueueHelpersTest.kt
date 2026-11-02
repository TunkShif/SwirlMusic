package one.tunkshif.swirlmusic.player

import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

/** Pure queue math extracted from [PlayerController] so it runs on the JVM. */
class PlayerQueueHelpersTest {

    @Test
    fun `shuffledWithFirst puts the current index first and keeps a permutation`() {
        val order = shuffledWithFirst(count = 6, first = 2, Random(42))

        assertThat(order.first()).isEqualTo(2)
        assertThat(order.toList()).containsExactly(0, 1, 2, 3, 4, 5)
    }

    @Test
    fun `shuffledWithFirst is deterministic for a seeded random`() {
        assertThat(shuffledWithFirst(10, 4, Random(7)).toList())
            .isEqualTo(shuffledWithFirst(10, 4, Random(7)).toList())
    }

    @Test
    fun `shuffledWithFirst of one element is just that element`() {
        assertThat(shuffledWithFirst(1, 0, Random(1)).toList()).containsExactly(0)
    }

    @Test
    fun `playbackOrder walks the next chain from the first window`() {
        // Simulated shuffle order 2 -> 0 -> 1 -> end.
        val next = mapOf(2 to 0, 0 to 1, 1 to C.INDEX_UNSET)
        val order = playbackOrder(3, { 2 }, { next.getValue(it) })

        assertThat(order).containsExactly(2, 0, 1).inOrder()
    }

    @Test
    fun `playbackOrder falls back to identity when there is no first window`() {
        assertThat(playbackOrder(3, { C.INDEX_UNSET }, { C.INDEX_UNSET }))
            .containsExactly(0, 1, 2).inOrder()
        assertThat(playbackOrder(0, { C.INDEX_UNSET }, { C.INDEX_UNSET })).isEmpty()
    }

    @Test
    fun `indexAfterRemoval keeps the index on the same entry`() {
        // Removing an entry before the current one shifts the index down.
        assertThat(indexAfterRemoval(current = 3, removed = 1, sizeAfter = 4)).isEqualTo(2)
        // Removing the current entry lands on the next one, now in its slot.
        assertThat(indexAfterRemoval(current = 1, removed = 1, sizeAfter = 4)).isEqualTo(1)
        // Removing after the current one leaves the index alone.
        assertThat(indexAfterRemoval(current = 1, removed = 3, sizeAfter = 4)).isEqualTo(1)
        // Removing the last entry when it is current clamps into range.
        assertThat(indexAfterRemoval(current = 4, removed = 4, sizeAfter = 4)).isEqualTo(3)
    }

    @Test
    fun `indexAfterMove keeps the index on the same entry`() {
        // The current entry itself moves.
        assertThat(indexAfterMove(current = 1, from = 1, to = 3)).isEqualTo(3)
        // An entry moved across the current one shifts the index the other way.
        assertThat(indexAfterMove(current = 2, from = 0, to = 3)).isEqualTo(1)
        assertThat(indexAfterMove(current = 1, from = 3, to = 0)).isEqualTo(2)
        // Moves that do not cross the current index leave it alone.
        assertThat(indexAfterMove(current = 0, from = 2, to = 3)).isEqualTo(0)
        assertThat(indexAfterMove(current = 4, from = 0, to = 2)).isEqualTo(4)
    }
}
