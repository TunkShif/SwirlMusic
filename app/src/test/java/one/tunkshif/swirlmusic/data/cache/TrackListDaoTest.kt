package one.tunkshif.swirlmusic.data.cache

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackListDaoTest {

    private lateinit var db: SwirlDatabase
    private lateinit var dao: TrackListDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            SwirlDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.trackListDao()
    }

    @After
    fun tearDown() = db.close()

    private fun snapshot(key: String, openedAt: Long = 0, version: Long? = null) =
        SnapshotEntity(key, "title-$key", "sub", null, 3, version, 0, openedAt)

    private fun track(id: Long) = TrackEntity(id, "t$id", "a", null, 0, 0)

    @Test
    fun `ordered ids round trip`() = runTest {
        dao.replaceSnapshot(snapshot("playlist:1"), listOf(9L, 7L, 5L))
        assertThat(dao.orderedIds("playlist:1")).containsExactly(9L, 7L, 5L).inOrder()
    }

    @Test
    fun `replaceSnapshot swaps the order atomically`() = runTest {
        dao.replaceSnapshot(snapshot("playlist:1"), listOf(1L, 2L, 3L))
        dao.replaceSnapshot(snapshot("playlist:1", version = 2), listOf(4L, 5L))
        assertThat(dao.orderedIds("playlist:1")).containsExactly(4L, 5L).inOrder()
    }

    @Test
    fun `evict keeps the newest snapshots and drops only unreferenced tracks`() = runTest {
        repeat(35) { dao.replaceSnapshot(snapshot("k:$it", openedAt = it.toLong()), listOf(1000L + it)) }
        // track 42 is referenced by a kept snapshot and must survive eviction.
        dao.replaceSnapshot(snapshot("k:shared", openedAt = 999), listOf(42L))
        dao.upsertTracks((0L until 40L).map { track(1000 + it) } + track(42) + track(7))

        dao.evict(keep = 30)

        assertThat(dao.snapshot("k:0")).isNull()
        assertThat(dao.snapshot("k:34")).isNotNull()
        // Evicted snapshot ids are gone; kept and shared tracks survive.
        assertThat(dao.tracksByIds(listOf(42L, 1034L, 1000L, 7L)).map { it.id })
            .containsExactly(42L, 1034L)
    }

    @Test
    fun `queue and resume rows round trip`() = runTest {
        dao.replaceQueue(listOf(5L, 9L, 3L))
        dao.saveResume(PlaybackResumeEntity(0, 1, 42_000, 2, 1000))

        assertThat(dao.queueIds()).containsExactly(5L, 9L, 3L).inOrder()
        dao.resume()!!.let {
            assertThat(it.currentIndex).isEqualTo(1)
            assertThat(it.positionMs).isEqualTo(42_000)
            assertThat(it.loopMode).isEqualTo(2)
        }
    }

    @Test
    fun `track metadata round trips the album fields`() = runTest {
        dao.upsertTracks(listOf(track(7).copy(albumId = 61, albumName = "OutRun")))

        dao.tracksByIds(listOf(7L)).single().let {
            assertThat(it.albumId).isEqualTo(61)
            assertThat(it.albumName).isEqualTo("OutRun")
        }
    }

    @Test
    fun `clearQueue and clearResume wipe the persisted playback state`() = runTest {
        dao.replaceQueue(listOf(5L))
        dao.saveResume(PlaybackResumeEntity(0, 0, 0, 0, 0))

        dao.clearQueue()
        dao.clearResume()

        assertThat(dao.queueIds()).isEmpty()
        assertThat(dao.resume()).isNull()
    }

    @Test
    fun `evict keeps tracks referenced by the queue`() = runTest {
        dao.replaceQueue(listOf(55L))
        dao.upsertTracks(listOf(track(55), track(77)))
        dao.replaceSnapshot(snapshot("old", openedAt = -1), listOf(77L))

        dao.evict(keep = 0)

        assertThat(dao.tracksByIds(listOf(55L, 77L)).map { it.id }).containsExactly(55L)
    }

    @Test
    fun `clearAll removes everything`() = runTest {
        dao.replaceSnapshot(snapshot("k"), listOf(1L, 2L))
        dao.upsertTracks(listOf(track(1), track(2)))

        dao.clearAll()

        assertThat(dao.snapshot("k")).isNull()
        assertThat(dao.orderedIds("k")).isEmpty()
        assertThat(dao.tracksByIds(listOf(1L, 2L))).isEmpty()
    }
}
