package one.tunkshif.swirlmusic.ui.tracks

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.library.FakeLibraryApi
import one.tunkshif.swirlmusic.ui.library.track
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.cache.SwirlDatabase
import one.tunkshif.swirlmusic.data.library.TrackListRepository
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackListViewModelTest {

    private val db = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        SwirlDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val api = FakeLibraryApi()
    private val repository = TrackListRepository(api, db.trackListDao(), object : TimeSource { override fun nowMillis() = 1_000L }, kotlinx.coroutines.test.UnconfinedTestDispatcher(), one.tunkshif.swirlmusic.data.cache.CacheGate(db.trackListDao(), kotlinx.coroutines.test.UnconfinedTestDispatcher()))
    private val viewModel = TrackListViewModel(repository)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.useTestMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @Test
    fun `a playlist shows its header and all its tracks`() = runTest {
        useTestMain()

        viewModel.load(TrackListSource.Playlist(1))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.title).isEqualTo("Late night drive")
        assertThat(state.subtitle).contains("tunkshif")
        assertThat((state.tracks as SectionState.Content).value).hasSize(2)
        // One detail call paints the header and the bundled tracks; no chunk call is needed.
        assertThat(api.calls).contains("playlistDetail:1")
        assertThat(api.calls.filter { it.startsWith("songs:") }).isEmpty()
    }

    @Test
    fun `loadMore fetches id slices of 100 in order, never past the end`() = runTest {
        useTestMain()
        api.contentsFirstTracks = (1L..50L).map { track(it) }
        api.contentsTrackIds = (1L..220L).toList()

        viewModel.load(TrackListSource.Playlist(1))
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(api.calls).contains("songs:" + (51L..150L).joinToString(","))
        assertThat((viewModel.state.value.tracks as SectionState.Content).value).hasSize(150)
        assertThat(viewModel.state.value.canLoadMore).isTrue()

        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(api.calls).contains("songs:" + (151L..220L).joinToString(","))
        assertThat(viewModel.state.value.canLoadMore).isFalse()

        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(api.calls.filter { it.startsWith("songs:") }).hasSize(2)
    }

    @Test
    fun `a failed chunk keeps rows and retries the same slice`() = runTest {
        useTestMain()
        api.contentsFirstTracks = (1L..50L).map { track(it) }
        api.contentsTrackIds = (1L..200L).toList()
        viewModel.load(TrackListSource.Playlist(1))
        advanceUntilIdle()

        api.failSongsWith = ApiResult.NetworkError(java.io.IOException("offline"))
        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(viewModel.state.value.moreError).isEqualTo(LoadError.Network)
        assertThat((viewModel.state.value.tracks as SectionState.Content).value).hasSize(50)
        assertThat(viewModel.state.value.canLoadMore).isTrue()

        api.failSongsWith = null
        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(api.calls).contains("songs:" + (51L..150L).joinToString(","))
        assertThat(viewModel.state.value.moreError).isNull()
    }

    @Test
    fun `concurrent loadMore calls make one request`() = runTest {
        useTestMain()
        api.contentsFirstTracks = (1L..50L).map { track(it) }
        api.contentsTrackIds = (1L..300L).toList()
        viewModel.load(TrackListSource.Playlist(1))
        advanceUntilIdle()

        viewModel.loadMore()
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(api.calls.filter { it.startsWith("songs:") }).hasSize(1)
    }

    @Test
    fun `out-of-order and missing songs come back in playlist order`() = runTest {
        useTestMain()
        api.contentsFirstTracks = listOf(track(1))
        api.contentsTrackIds = listOf(1L, 2L, 3L, 4L)
        api.songsOverride = { ids -> listOf(track(ids[2]), track(ids[0])) } // drop 4, reorder

        viewModel.load(TrackListSource.Playlist(1))
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        val tracks = (viewModel.state.value.tracks as SectionState.Content).value
        assertThat(tracks.map { it.id }).containsExactly(1L, 2L, 4L).inOrder()
        assertThat(viewModel.state.value.canLoadMore).isFalse()
    }

    @Test
    fun `an album loads through the album endpoint`() = runTest {
        useTestMain()

        viewModel.load(TrackListSource.Album(1))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.title).isEqualTo("OutRun")
        assertThat(state.subtitle).isEqualTo("Kavinsky")
        assertThat((state.tracks as SectionState.Content).value).hasSize(1)
        assertThat(api.calls).contains("albumDetail:1")
    }

    @Test
    fun `an artist loads the hot songs`() = runTest {
        useTestMain()

        viewModel.load(TrackListSource.Artist(1))
        advanceUntilIdle()

        assertThat(viewModel.state.value.title).isEqualTo("Kavinsky")
        assertThat((viewModel.state.value.tracks as SectionState.Content).value).hasSize(1)
    }

    @Test
    fun `loading a different list clears the previous header`() = runTest {
        useTestMain()

        viewModel.load(TrackListSource.Playlist(1))
        advanceUntilIdle()
        assertThat(viewModel.state.value.title).isEqualTo("Late night drive")

        // The second fetch suspends on the test dispatcher; until it lands the screen must
        // show a blank header and Loading, not playlist 1's title and tracks.
        viewModel.load(TrackListSource.Album(2))

        val state = viewModel.state.value
        assertThat(state.title).isEmpty()
        assertThat(state.tracks).isInstanceOf(SectionState.Loading::class.java)

        advanceUntilIdle()
    }

    @Test
    fun `the play pager skips empty slices and stops on failure`() = runTest {
        useTestMain()
        api.contentsFirstTracks = (1L..50L).map { track(it) }
        api.contentsTrackIds = (1L..300L).toList()
        viewModel.load(TrackListSource.Playlist(1))
        advanceUntilIdle()

        // The slice 51..150 comes back with nothing playable — the pager must skip it and
        // serve 151..250 next, not treat empty as the end.
        api.songsOverride = { ids -> if (ids.first() == 51L) emptyList() else ids.map { track(it) } }
        val pager = viewModel.playPager()!!

        val skipped = pager.next()
        assertThat(skipped!!.map { it.id }).containsExactlyElementsIn(151L..250L).inOrder()
        assertThat(pager.next()!!.map { it.id }).containsExactlyElementsIn(251L..300L).inOrder()

        api.failSongsWith = ApiResult.NetworkError(java.io.IOException("offline"))
        api.songsOverride = null
        assertThat(viewModel.playPager()!!.next()).isNull()
    }

    @Test
    fun `an in-flight loadMore is cancelled by a reload`() = runTest {
        useTestMain()
        api.contentsFirstTracks = (1L..50L).map { track(it) }
        api.contentsTrackIds = (1L..200L).toList()
        viewModel.load(TrackListSource.Playlist(1))
        advanceUntilIdle()

        api.songGate = kotlinx.coroutines.CompletableDeferred()
        viewModel.loadMore()
        runCurrent()

        viewModel.retry()
        api.songGate!!.complete(Unit)
        advanceUntilIdle()

        // The reload paints the same first tracks; the stale slice must not append.
        val state = viewModel.state.value
        assertThat((state.tracks as SectionState.Content).value).hasSize(50)
        assertThat(state.title).isEqualTo("Late night drive")
    }

    @Test
    fun `a network failure is reported and retry recovers`() = runTest {
        useTestMain()
        api.failWith = ApiResult.NetworkError(IOException("offline"))

        viewModel.load(TrackListSource.Album(1))
        advanceUntilIdle()
        assertThat(viewModel.state.value.tracks)
            .isEqualTo(SectionState.Failed(LoadError.Network))

        api.failWith = null
        viewModel.retry()
        advanceUntilIdle()
        assertThat(viewModel.state.value.tracks).isInstanceOf(SectionState.Content::class.java)
    }

    @Test
    fun `loading the same source twice does not refetch`() = runTest {
        useTestMain()

        viewModel.load(TrackListSource.Album(1))
        advanceUntilIdle()
        viewModel.load(TrackListSource.Album(1))
        advanceUntilIdle()

        assertThat(api.calls.count { it == "albumDetail:1" }).isEqualTo(1)
    }
}
