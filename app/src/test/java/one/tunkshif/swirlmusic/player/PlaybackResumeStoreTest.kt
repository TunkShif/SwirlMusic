package one.tunkshif.swirlmusic.player

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.Track
import org.junit.Test

class PlaybackResumeStoreTest {

    private fun track(id: Long) = Track(id, "t$id", "a", null, 1000)

    @Test
    fun `missing metadata drops ids and shifts the saved index`() {
        val metadata = mapOf(1L to track(1), 3L to track(3), 4L to track(4))
        val (tracks, index, _) = restoreQueue(listOf(1, 2, 3, 4), metadata, savedIndex = 2)!!
        assertThat(tracks.map { it.id }).containsExactly(1L, 3L, 4L).inOrder()
        assertThat(index).isEqualTo(1) // 2 was dropped; saved index 2 lands on 3
    }

    @Test
    fun `a saved index beyond a dropped head stays in range`() {
        val metadata = mapOf(5L to track(5))
        val (tracks, index, _) = restoreQueue(listOf(1, 5), metadata, savedIndex = 1)!!
        assertThat(tracks).hasSize(1)
        assertThat(index).isEqualTo(0)
    }

    @Test
    fun `nothing usable returns null`() {
        assertThat(restoreQueue(listOf(1, 2), emptyMap(), 0)).isNull()
        assertThat(restoreQueue(emptyList(), mapOf(1L to track(1)), 0)).isNull()
    }

    @Test
    fun `restored state exposes its saved progress`() {
        val state = PlaybackState(
            queue = listOf(track(1), track(3)),
            currentIndex = 1,
            positionMs = 500,
            durationMs = 1000,
            restored = true,
        )
        assertThat(state.current).isEqualTo(track(3))
        assertThat(state.progress).isWithin(0.01f).of(0.5f)
    }
}
