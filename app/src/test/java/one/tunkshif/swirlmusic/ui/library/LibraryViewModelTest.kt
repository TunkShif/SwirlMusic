package one.tunkshif.swirlmusic.ui.library

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
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val api = FakeLibraryApi()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.useTestMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @Test
    fun `all four sections load`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession())

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.playlists.items).hasSize(3)
        assertThat(state.albums.items).hasSize(1)
        assertThat(state.artists.items).hasSize(1)
        assertThat(state.recent).isInstanceOf(SectionState.Content::class.java)
    }

    @Test
    fun `a first page failure is shown for that tab only`() = runTest {
        useTestMain()
        api.failWith = ApiResult.NetworkError(IOException("offline"))
        val viewModel = LibraryViewModel(api, signedInSession())

        advanceUntilIdle()

        assertThat(viewModel.state.value.playlists.state)
            .isEqualTo(SectionState.Failed(LoadError.Network))
    }

    @Test
    fun `load more asks for the next offset and appends`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession())
        advanceUntilIdle()
        api.playlists = listOf(playlist(4))

        viewModel.loadMore(LibraryTab.Playlists)
        advanceUntilIdle()

        val items = viewModel.state.value.playlists.items
        assertThat(items.map { it.id }).containsExactly(1L, 2L, 3L, 4L).inOrder()
        assertThat(api.calls).contains("playlists:3")
    }

    @Test
    fun `load more does nothing when there is no next page`() = runTest {
        useTestMain()
        api.hasMorePlaylists = false
        val viewModel = LibraryViewModel(api, signedInSession())
        advanceUntilIdle()

        viewModel.loadMore(LibraryTab.Playlists)
        advanceUntilIdle()

        assertThat(api.calls.count { it.startsWith("playlists:") }).isEqualTo(1)
    }

    @Test
    fun `switching tabs does not reload a loaded tab`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession())
        advanceUntilIdle()

        viewModel.selectTab(LibraryTab.Albums)
        viewModel.selectTab(LibraryTab.Playlists)
        advanceUntilIdle()

        assertThat(viewModel.state.value.tab).isEqualTo(LibraryTab.Playlists)
        assertThat(api.calls.count { it.startsWith("playlists:") }).isEqualTo(1)
    }

    @Test
    fun `retry reloads the failed tab`() = runTest {
        useTestMain()
        api.failWith = ApiResult.HttpError(500, "boom")
        val viewModel = LibraryViewModel(api, signedInSession())
        advanceUntilIdle()
        api.failWith = null

        viewModel.retry(LibraryTab.Playlists)
        advanceUntilIdle()

        assertThat(viewModel.state.value.playlists.items).hasSize(3)
    }
}
