package one.tunkshif.swirlmusic.data.library

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.cache.CacheGate
import one.tunkshif.swirlmusic.data.cache.RecentAlbumEntity
import one.tunkshif.swirlmusic.data.cache.RoomAccountScopedCache
import one.tunkshif.swirlmusic.data.cache.SwirlDatabase
import one.tunkshif.swirlmusic.data.model.RecentAlbum
import one.tunkshif.swirlmusic.ui.library.FakeLibraryApi
import one.tunkshif.swirlmusic.ui.library.recentAlbum
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecentAlbumsRepositoryTest {

    private lateinit var db: SwirlDatabase
    private lateinit var gate: CacheGate
    private lateinit var repo: RecentAlbumsRepository
    private val api = FakeLibraryApi()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            SwirlDatabase::class.java,
        ).allowMainThreadQueries().build()
        gate = CacheGate(db.trackListDao(), UnconfinedTestDispatcher())
        repo = RecentAlbumsRepository(api, db.trackListDao(), gate, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = db.close()

    private fun seed(albums: List<RecentAlbum>) {
        db.trackListDao().replaceRecentAlbums(albums.mapIndexed { i, a -> a.toEntity(i) })
    }

    private fun RecentAlbum.toEntity(position: Int) = RecentAlbumEntity(
        position = position,
        albumId = album.id,
        name = album.name,
        artistLine = album.artistLine,
        artworkUrl = album.artworkUrl,
        trackCount = album.trackCount,
        playedAtMillis = playedAtMillis,
    )

    private fun ids(albums: List<RecentAlbum>) = albums.map { it.album.id }

    @Test
    fun `cached returns the stored rows newest first`() = runTest {
        seed(listOf(recentAlbum(1), recentAlbum(2)))

        assertThat(repo.cached().map { it.album.id }).containsExactly(101L, 102L).inOrder()
    }

    @Test
    fun `a capped refresh replaces the whole table`() = runTest {
        seed(listOf(recentAlbum(1), recentAlbum(2)))
        api.recentAlbumsResult = ApiResult.Success(listOf(recentAlbum(5), recentAlbum(6)))

        val result = repo.refresh(RecentAlbumsRepository.RECENT_ALBUMS_CAP)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(ids(repo.cached())).containsExactly(105L, 106L).inOrder()
    }

    @Test
    fun `a smaller refresh merges the fresh head with the cached tail`() = runTest {
        seed(listOf(recentAlbum(1), recentAlbum(2), recentAlbum(3), recentAlbum(4)))
        api.recentAlbumsResult = ApiResult.Success(listOf(recentAlbum(7), recentAlbum(8)))

        val result = repo.refresh(10) as ApiResult.Success

        // The call returns just the fresh page; the table keeps the cached tail.
        assertThat(ids(result.value)).containsExactly(107L, 108L).inOrder()
        assertThat(ids(repo.cached()))
            .containsExactly(107L, 108L, 101L, 102L, 103L, 104L).inOrder()
    }

    @Test
    fun `merging deduplicates rows the fresh list covers`() = runTest {
        seed(listOf(recentAlbum(1), recentAlbum(2), recentAlbum(3)))
        // The server re-report album 2 with a newer playedAt — the fresh row wins.
        api.recentAlbumsResult = ApiResult.Success(listOf(recentAlbum(2), recentAlbum(9)))

        repo.refresh(10)

        assertThat(ids(repo.cached())).containsExactly(102L, 109L, 101L, 103L).inOrder()
    }

    @Test
    fun `a merged refresh caps the table at a hundred`() = runTest {
        seed((1L..100L).map(::recentAlbum))
        api.recentAlbumsResult = ApiResult.Success((200L..204L).map(::recentAlbum))

        repo.refresh(10)

        val cached = repo.cached()
        assertThat(cached).hasSize(100)
        assertThat(ids(cached.take(5))).containsExactly(300L, 301L, 302L, 303L, 304L).inOrder()
    }

    @Test
    fun `a failed refresh leaves the cache alone`() = runTest {
        seed(listOf(recentAlbum(1)))
        api.recentAlbumsResult = ApiResult.NetworkError(IOException("offline"))

        val result = repo.refresh(10)

        assertThat(result).isInstanceOf(ApiResult.NetworkError::class.java)
        assertThat(ids(repo.cached())).containsExactly(101L)
    }

    @Test
    fun `the account clear wipes recent albums`() = runTest {
        seed(listOf(recentAlbum(1), recentAlbum(2)))

        RoomAccountScopedCache(gate).clear()

        assertThat(db.trackListDao().recentAlbums()).isEmpty()
        assertThat(repo.cached()).isEmpty()
    }

    @Test
    fun `a refresh that lands after an account clear is dropped`() = runTest {
        val held = CompletableDeferred<Unit>()
        api.recentAlbumsGate = held
        api.recentAlbumsResult = ApiResult.Success(listOf(recentAlbum(1)))

        launch { repo.refresh(10) }
        runCurrent()
        RoomAccountScopedCache(gate).clear()
        held.complete(Unit)
        advanceUntilIdle()

        assertThat(db.trackListDao().recentAlbums()).isEmpty()
    }
}
