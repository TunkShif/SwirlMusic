package one.tunkshif.swirlmusic.player

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.cache.CacheGate
import one.tunkshif.swirlmusic.data.cache.SwirlDatabase
import one.tunkshif.swirlmusic.data.model.Track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackResumeStoreTest {

    private fun track(id: Long) = Track(id, "t$id", "a", null, 1000)

    private val db = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        SwirlDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val store = PlaybackResumeStore(
        db.trackListDao(),
        CacheGate(db.trackListDao(), UnconfinedTestDispatcher()),
        object : TimeSource { override fun nowMillis() = 1_000L },
        UnconfinedTestDispatcher(),
    )

    @After
    fun tearDown() = db.close()

    @Test
    fun `missing metadata drops ids and shifts the saved index`() {
        val metadata = mapOf(1L to track(1), 3L to track(3), 4L to track(4))
        val (tracks, index, _) = restoreQueue(
            listOf(1L to null, 2L to null, 3L to null, 4L to null),
            metadata,
            savedIndex = 2,
        )!!
        assertThat(tracks.map { it.first.id }).containsExactly(1L, 3L, 4L).inOrder()
        assertThat(index).isEqualTo(1) // 2 was dropped; saved index 2 lands on 3
    }

    @Test
    fun `a saved index beyond a dropped head stays in range`() {
        val metadata = mapOf(5L to track(5))
        val (tracks, index, _) = restoreQueue(listOf(1L to null, 5L to null), metadata, savedIndex = 1)!!
        assertThat(tracks).hasSize(1)
        assertThat(index).isEqualTo(0)
    }

    @Test
    fun `sources survive alongside their tracks`() {
        val metadata = mapOf(1L to track(1), 2L to track(2))
        val (tracks, _, _) = restoreQueue(
            listOf(1L to 7L, 9L to 3L, 2L to null),
            metadata,
            savedIndex = 0,
        )!!
        assertThat(tracks.map { it.first.id to it.second }).containsExactly(1L to 7L, 2L to null).inOrder()
    }

    @Test
    fun `nothing usable returns null`() {
        assertThat(restoreQueue(listOf(1L to null, 2L to null), emptyMap(), 0)).isNull()
        assertThat(restoreQueue(emptyList(), mapOf(1L to track(1)), 0)).isNull()
    }

    @Test
    fun `restored state exposes its saved progress`() {
        val state = PlaybackState(
            queue = listOf(QueueEntry(0, track(1)), QueueEntry(1, track(3))),
            currentIndex = 1,
            positionMs = 500,
            durationMs = 1000,
            restored = true,
        )
        assertThat(state.current).isEqualTo(track(3))
        assertThat(state.progress).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `save and load round trip the queue entries and resume spot`() = runTest {
        store.save(
            queue = listOf(track(1) to 42L, track(2) to null, track(3) to 42L),
            currentIndex = 1,
            positionMs = 5_000,
            loopMode = LoopMode.RepeatOne,
        )

        val (entries, restored) = store.load()!!
        assertThat(entries.map { it.first.id to it.second })
            .containsExactly(1L to 42L, 2L to null, 3L to 42L).inOrder()
        assertThat(restored.currentIndex).isEqualTo(1)
        assertThat(restored.positionMs).isEqualTo(5_000)
        assertThat(restored.loopMode).isEqualTo(LoopMode.RepeatOne)
    }

    @Test
    fun `load is null when nothing was playing`() = runTest {
        assertThat(store.load()).isNull()
    }
}
