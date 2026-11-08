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
import one.tunkshif.swirlmusic.data.library.CollectionRepository
import one.tunkshif.swirlmusic.data.library.FakeCollectionApi
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36])
class LibraryViewModelTest {

    private val api = FakeLibraryApi()
    private val collectionApi = FakeCollectionApi()
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(
        org.robolectric.RuntimeEnvironment.getApplication(),
        one.tunkshif.swirlmusic.data.cache.SwirlDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
    private val nowPlaying = object : one.tunkshif.swirlmusic.player.CurrentTrackSource {
        override val currentTrackId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    }
    private val collection = CollectionRepository(
        collectionApi,
        db.trackListDao(),
        one.tunkshif.swirlmusic.data.cache.CacheGate(db.trackListDao(), dispatcher),
        object : one.tunkshif.swirlmusic.data.api.TimeSource { override fun nowMillis() = 1_000L },
        dispatcher,
    )

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
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)

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
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)

        advanceUntilIdle()

        assertThat(viewModel.state.value.playlists.state)
            .isEqualTo(SectionState.Failed(LoadError.Network))
    }

    @Test
    fun `load more asks for the next offset and appends`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)
        advanceUntilIdle()
        api.playlists = listOf(playlist(4))

        viewModel.loadMore(LibraryTab.Playlists)
        advanceUntilIdle()

        val items = viewModel.state.value.playlists.items
        assertThat(items.map { it.id }).containsExactly(1L, 2L, 3L, 4L).inOrder()
        assertThat(api.calls).contains("playlists:3")
    }

    @Test
    fun `a collection change reloads only that tab, fresh`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)
        advanceUntilIdle()
        api.calls.clear()

        collection.setAlbumSaved(9, saved = true)
        advanceUntilIdle()

        assertThat(api.calls).contains("albums:0")
        assertThat(api.freshCalls).containsExactly("albums:0")
        assertThat(api.calls.filter { it.startsWith("artists:") }).isEmpty()

        api.calls.clear()
        api.freshCalls.clear()

        collection.setArtistFollowed(9, followed = true)
        advanceUntilIdle()

        assertThat(api.freshCalls).containsExactly("artists:0")
        assertThat(api.calls.filter { it.startsWith("albums:") }).isEmpty()
    }

    @Test
    fun `load more does nothing when there is no next page`() = runTest {
        useTestMain()
        api.hasMorePlaylists = false
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)
        advanceUntilIdle()

        viewModel.loadMore(LibraryTab.Playlists)
        advanceUntilIdle()

        assertThat(api.calls.count { it.startsWith("playlists:") }).isEqualTo(1)
    }

    @Test
    fun `switching tabs does not reload a loaded tab`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)
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
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)
        advanceUntilIdle()
        api.failWith = null

        viewModel.retry(LibraryTab.Playlists)
        advanceUntilIdle()

        assertThat(viewModel.state.value.playlists.items).hasSize(3)
    }
}
