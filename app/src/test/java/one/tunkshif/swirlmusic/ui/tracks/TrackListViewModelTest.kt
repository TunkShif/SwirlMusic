package one.tunkshif.swirlmusic.ui.tracks

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.library.FakeLibraryApi
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackListViewModelTest {

    private val api = FakeLibraryApi()
    private val viewModel = TrackListViewModel(api)

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
        assertThat(api.calls).containsAtLeast("playlistDetail:1", "playlistTracks:1")
    }

    @Test
    fun `an album loads through the album endpoint`() = runTest {
        useTestMain()

        viewModel.load(TrackListSource.Album(1))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.title).isEqualTo("OutRun")
        assertThat(state.subtitle).contains("2013")
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
