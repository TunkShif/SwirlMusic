package one.tunkshif.swirlmusic.ui.home

import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmHomeApi
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.library.RecentAlbumsRepository
import one.tunkshif.swirlmusic.data.library.TrackListRepository
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.data.session.SessionState
import one.tunkshif.swirlmusic.player.PlayerController
import one.tunkshif.swirlmusic.ui.library.FakeLibraryApi
import one.tunkshif.swirlmusic.ui.library.recentAlbum
import one.tunkshif.swirlmusic.ui.library.signedInSession
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeViewModelTest {

    private val api = FakeHomeApi()
    private val libraryApi = FakeLibraryApi().apply {
        likedIdsResult = ApiResult.Success(listOf(1L, 2L, 3L))
    }
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(
        org.robolectric.RuntimeEnvironment.getApplication(),
        one.tunkshif.swirlmusic.data.cache.SwirlDatabase::class.java,
    ).allowMainThreadQueries().build()
    private var now = 1_000_000L
    private val timeSource = object : TimeSource { override fun nowMillis() = now }
    private val gate = one.tunkshif.swirlmusic.data.cache.CacheGate(
        db.trackListDao(),
        UnconfinedTestDispatcher(),
    )
    private val repository = TrackListRepository(
        libraryApi,
        db.trackListDao(),
        timeSource,
        UnconfinedTestDispatcher(),
        gate,
    )
    private val recentAlbums = RecentAlbumsRepository(
        libraryApi,
        db.trackListDao(),
        gate,
        UnconfinedTestDispatcher(),
    )
    private val session = signedInSession()
    private val random = Random(7)

    private fun viewModel() = HomeViewModel(api, libraryApi, recentAlbums, repository, session, timeSource, random)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.useTestMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @Test
    fun `daily, recent albums, and liked ids load in parallel`() = runTest {
        useTestMain()
        val viewModel = viewModel()

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.daily).isInstanceOf(SectionState.Content::class.java)
        assertThat(state.recent).isInstanceOf(SectionState.Content::class.java)
        assertThat(state.likedIds).containsExactly(1L, 2L, 3L)
        assertThat(api.dailyCalls).isEqualTo(1)
        assertThat(libraryApi.calls).contains("recentAlbums:10")
        assertThat(libraryApi.calls).contains("likedIds:42")
    }

    @Test
    fun `a failing section leaves the others readable`() = runTest {
        useTestMain()
        api.dailyResult = ApiResult.NetworkError(IOException("offline"))
        val viewModel = viewModel()

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.daily).isEqualTo(SectionState.Failed(LoadError.Network))
        assertThat(state.recent).isInstanceOf(SectionState.Content::class.java)
    }

    @Test
    fun `a server error is told apart from a network error`() = runTest {
        useTestMain()
        libraryApi.recentAlbumsResult = ApiResult.HttpError(503, "busy")
        val viewModel = viewModel()

        advanceUntilIdle()

        assertThat(viewModel.state.value.recent).isEqualTo(SectionState.Failed(LoadError.Server))
    }

    @Test
    fun `an expired session signs out instead of showing errors`() = runTest {
        useTestMain()
        api.dailyResult = ApiResult.Unauthorized("{\"code\":301}")
        libraryApi.recentAlbumsResult = ApiResult.Unauthorized("{\"code\":301}")
        libraryApi.likedIdsResult = ApiResult.Unauthorized("{\"code\":301}")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(session.state.value).isEqualTo(SessionState.SignedOut)
    }

    @Test
    fun `refresh flags the pull to refresh indicator and clears it`() = runTest {
        useTestMain()
        val viewModel = viewModel()
        advanceUntilIdle()

        // Hold the next load open so the refreshing flag can be observed.
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        viewModel.refresh()
        runCurrent()
        assertThat(viewModel.state.value.isRefreshing).isTrue()

        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isRefreshing).isFalse()
        assertThat(api.dailyCalls).isEqualTo(2)
        assertThat(libraryApi.calls.count { it == "recentAlbums:10" }).isEqualTo(2)
    }

    @Test
    fun `retry reloads after a failure`() = runTest {
        useTestMain()
        api.dailyResult = ApiResult.NetworkError(IOException("offline"))
        val viewModel = viewModel()
        advanceUntilIdle()

        api.dailyResult = ApiResult.Success(listOf(sampleTrack))
        viewModel.retry()
        advanceUntilIdle()

        assertThat(viewModel.state.value.daily).isInstanceOf(SectionState.Content::class.java)
    }

    @Test
    fun `onShown leaves the recent row alone inside thirty seconds`() = runTest {
        useTestMain()
        val viewModel = viewModel()
        advanceUntilIdle()
        libraryApi.calls.clear()

        now += 29_000L
        viewModel.onShown()
        advanceUntilIdle()

        assertThat(libraryApi.calls.filter { it.startsWith("recentAlbums") }).isEmpty()
    }

    @Test
    fun `onShown refreshes the recent row once it is stale`() = runTest {
        useTestMain()
        val viewModel = viewModel()
        advanceUntilIdle()
        libraryApi.calls.clear()

        now += 31_000L
        viewModel.onShown()
        advanceUntilIdle()

        assertThat(libraryApi.calls).containsExactly("recentAlbums:10")
        assertThat(viewModel.state.value.recent).isInstanceOf(SectionState.Content::class.java)
    }

    @Test
    fun `a failed silent refresh keeps the shown albums`() = runTest {
        useTestMain()
        val viewModel = viewModel()
        advanceUntilIdle()
        val shown = viewModel.state.value.recent

        libraryApi.recentAlbumsResult = ApiResult.NetworkError(IOException("offline"))
        now += 31_000L
        viewModel.onShown()
        advanceUntilIdle()

        assertThat(viewModel.state.value.recent).isEqualTo(shown)
    }

    @Test
    fun `feeling lucky shuffles the liked ids and plays the first window`() = runTest {
        useTestMain()
        val ids = (1L..250L).toList()
        libraryApi.likedIdsResult = ApiResult.Success(ids)
        val viewModel = viewModel()
        advanceUntilIdle()

        var played: List<Track>? = null
        var index = -1
        var pager: PlayerController.TrackPager? = null
        viewModel.feelingLucky { tracks, i, p ->
            played = tracks
            index = i
            pager = p
        }
        advanceUntilIdle()

        val shuffled = ids.shuffled(Random(7))
        assertThat(played!!.map { it.id }).isEqualTo(shuffled.take(100))
        assertThat(index).isEqualTo(0)
        assertThat(pager!!.next()!!.map { it.id }).isEqualTo(shuffled.drop(100).take(100))
        assertThat(pager!!.next()!!.map { it.id }).isEqualTo(shuffled.drop(200))
        assertThat(pager!!.next()).isNull()
        assertThat(viewModel.state.value.feelingLucky).isFalse()
    }

    @Test
    fun `feeling lucky reports a failure as one transient message`() = runTest {
        useTestMain()
        val viewModel = viewModel()
        advanceUntilIdle()

        libraryApi.likedIdsResult = ApiResult.NetworkError(IOException("offline"))
        val message = async { viewModel.messages.first() }
        viewModel.feelingLucky { _, _, _ -> }
        advanceUntilIdle()

        assertThat(message.await()).isEqualTo(HomeMessage.LuckyFailed)
        assertThat(viewModel.state.value.feelingLucky).isFalse()
    }

    @Test
    fun `feeling lucky ignores a second tap while the window loads`() = runTest {
        useTestMain()
        val viewModel = viewModel()
        advanceUntilIdle()

        libraryApi.likedIdsGate = CompletableDeferred()
        viewModel.feelingLucky { _, _, _ -> }
        runCurrent()
        viewModel.feelingLucky { _, _, _ -> }
        advanceUntilIdle()

        assertThat(libraryApi.calls.count { it.startsWith("likedIds") }).isEqualTo(2)
        // One during load(), one for the tap — the second tap was ignored.
    }

    @Test
    fun `an empty liked list keeps the button hidden`() = runTest {
        useTestMain()
        libraryApi.likedIdsResult = ApiResult.Success(emptyList())
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.likedIds).isEmpty()
    }

    private class FakeHomeApi : NcmHomeApi {
        var dailyResult: ApiResult<List<Track>> = ApiResult.Success(listOf(sampleTrack, secondTrack))
        var dailyCalls = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun dailySongs(): ApiResult<List<Track>> {
            dailyCalls += 1
            gate?.await()
            return dailyResult
        }
    }

    private companion object {
        val sampleTrack = Track(1, "Nightcall", "Kavinsky", null, 258_000)
        val secondTrack = Track(2, "Midnight City", "M83", null, 244_000)
    }

    @Test
    fun `cached albums show before the refresh answers, then are replaced`() = runTest {
        // Twelve rows persisted by a previous session; the row shows at most ten.
        libraryApi.recentAlbumsResult = ApiResult.Success((1L..12L).map(::recentAlbum))
        recentAlbums.refresh(RecentAlbumsRepository.RECENT_ALBUMS_CAP)
        libraryApi.calls.clear()

        val held = kotlinx.coroutines.CompletableDeferred<Unit>()
        libraryApi.recentAlbumsGate = held
        libraryApi.recentAlbumsResult = ApiResult.Success(listOf(recentAlbum(42)))

        val viewModel = viewModel()
        advanceUntilIdle()

        val shown = viewModel.state.value.recent
        assertThat((shown as SectionState.Content).value.map { it.album.id })
            .containsExactly(*(1L..10L).map { 100L + it }.toTypedArray()).inOrder()
        assertThat(libraryApi.calls).contains("recentAlbums:10")

        held.complete(Unit)
        advanceUntilIdle()

        val fresh = viewModel.state.value.recent
        assertThat((fresh as SectionState.Content).value.map { it.album.id }).containsExactly(142L)
    }

    @Test
    fun `an empty cache keeps the recent row loading until the refresh answers`() = runTest {
        val held = kotlinx.coroutines.CompletableDeferred<Unit>()
        libraryApi.recentAlbumsGate = held
        libraryApi.recentAlbumsResult = ApiResult.Success(listOf(recentAlbum(5)))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.recent).isInstanceOf(SectionState.Loading::class.java)

        held.complete(Unit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.recent)
            .isEqualTo(SectionState.Content(listOf(recentAlbum(5))))
    }
}
