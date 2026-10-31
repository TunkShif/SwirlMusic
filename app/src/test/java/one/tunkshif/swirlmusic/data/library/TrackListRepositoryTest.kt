package one.tunkshif.swirlmusic.data.library

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.cache.SwirlDatabase
import one.tunkshif.swirlmusic.ui.library.FakeLibraryApi
import one.tunkshif.swirlmusic.ui.library.track
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackListRepositoryTest {

    private lateinit var db: SwirlDatabase
    private lateinit var repo: TrackListRepository
    private val api = FakeLibraryApi()
    private lateinit var gate: one.tunkshif.swirlmusic.data.cache.CacheGate
    private var now = 1_000L
    private val time = object : TimeSource { override fun nowMillis() = now }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            SwirlDatabase::class.java,
        ).allowMainThreadQueries().build()
        gate = one.tunkshif.swirlmusic.data.cache.CacheGate(db.trackListDao(), kotlinx.coroutines.test.UnconfinedTestDispatcher())
        repo = TrackListRepository(api, db.trackListDao(), time, kotlinx.coroutines.test.UnconfinedTestDispatcher(), gate)
    }

    @After
    fun tearDown() = db.close()

    private fun playlist(ids: LongRange, first: Int = 50, version: Long = 1) {
        api.contentsTrackIds = ids.toList()
        api.contentsFirstTracks = ids.take(first).map { track(it) }
        api.contentsTrackUpdateTime = version
    }

    @Test
    fun `a cold fetch writes the snapshot`() = runTest {
        playlist(1L..220L)
        val fetched = repo.fetchPlaylist(7) as ApiResult.Success
        assertThat(fetched.value.orderedIds).hasSize(220)
        assertThat(fetched.value.orderChanged).isTrue()

        val cached = repo.cached(TrackListRepository.playlistKey(7))!!
        assertThat(cached.orderedIds).hasSize(220)
        assertThat(cached.firstTracks).hasSize(50)
        assertThat(cached.title).isEqualTo("Late night drive")
    }

    @Test
    fun `cached rows come back before the detail answers`() = runTest {
        playlist(1L..220L)
        repo.fetchPlaylist(7)

        // With the network gated, the cache still serves the head rows.
        api.detailGate = CompletableDeferred()
        var answered = false
        val job = launch { repo.fetchPlaylist(7); answered = true }

        val cached = repo.cached(TrackListRepository.playlistKey(7))!!
        assertThat(cached.firstTracks.map { it.id }).containsExactlyElementsIn(1L..50L).inOrder()
        assertThat(answered).isFalse()
        // The cache path never calls songsByIds for ids already in the track table.
        assertThat(api.calls.filter { it.startsWith("songs:") }).isEmpty()

        api.detailGate!!.complete(Unit)
        job.join()
    }

    @Test
    fun `the same version keeps the id order`() = runTest {
        playlist(1L..150L, version = 9)
        repo.fetchPlaylist(7)
        api.contentsTrackIds = listOf(99L) // would rewrite if honoured — must not be

        val fetched = repo.fetchPlaylist(7) as ApiResult.Success
        assertThat(fetched.value.orderChanged).isFalse()
        assertThat(repo.cached(TrackListRepository.playlistKey(7))!!.orderedIds).hasSize(150)
    }

    @Test
    fun `a changed version rewrites ids`() = runTest {
        playlist(1L..150L, version = 1)
        repo.fetchPlaylist(7)

        playlist(1L..250L, version = 2)
        val fetched = repo.fetchPlaylist(7) as ApiResult.Success
        assertThat(fetched.value.orderChanged).isTrue()
        assertThat(repo.cached(TrackListRepository.playlistKey(7))!!.orderedIds).hasSize(250)
    }

    @Test
    fun `tracksByIds takes DB hits first and fetches only the misses`() = runTest {
        playlist(1L..150L)
        repo.fetchPlaylist(7)
        api.calls.clear()

        val slice = (1L..120L).toList()
        val result = repo.tracksByIds(slice) as ApiResult.Success
        assertThat(result.value.map { it.id }).containsExactlyElementsIn(slice).inOrder()
        // 1..50 were in the table; only 51..120 went to the network.
        assertThat(api.calls.filter { it.startsWith("songs:") }).hasSize(1)
        assertThat(api.calls.last { it.startsWith("songs:") })
            .isEqualTo("songs:" + (51L..120L).joinToString(","))
    }

    @Test
    fun `a clear during a fetch drops its writes`() = runTest {
        playlist(1L..220L)
        api.detailGate = kotlinx.coroutines.CompletableDeferred()
        var done = false
        val job = launch { repo.fetchPlaylist(7); done = true }

        runCurrent()
        gate.clearAll()
        api.detailGate!!.complete(Unit)
        job.join()
        assertThat(done).isTrue()

        assertThat(repo.cached(TrackListRepository.playlistKey(7))).isNull()
        assertThat(db.trackListDao().tracksByIds(listOf(1L, 2L))).isEmpty()
    }

    @Test
    fun `the playlist subtitle stores only the creator`() = runTest {
        playlist(1L..150L)
        repo.fetchPlaylist(7)
        assertThat(repo.cached(TrackListRepository.playlistKey(7))!!.subtitle).isEqualTo("tunkshif")
    }

    @Test
    fun `an album fetch replaces its snapshot`() = runTest {
        repo.fetchAlbum(5)
        val cached = repo.cached(TrackListRepository.albumKey(5))!!
        assertThat(cached.title).isEqualTo("OutRun")
        assertThat(cached.orderedIds).hasSize(1)
        assertThat(cached.firstTracks).hasSize(1)
    }
}
