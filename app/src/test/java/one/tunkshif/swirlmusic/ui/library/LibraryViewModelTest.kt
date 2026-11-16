package one.tunkshif.swirlmusic.ui.library

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
        api,
        signedInSession(),
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
    fun `the paged sections load while recent stays lazy`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.playlists.items).hasSize(3)
        assertThat(state.albums.items).hasSize(1)
        assertThat(state.artists.items).hasSize(1)
        // The ~3 MB albums call only runs once the Recent tab opens (0013-D56).
        assertThat(api.calls.filter { it == "recent" || it.startsWith("recentAlbums") }).isEmpty()
        assertThat(state.recentAlbums).isInstanceOf(SectionState.Loading::class.java)
        assertThat(state.recent).isInstanceOf(SectionState.Loading::class.java)
    }

    @Test
    fun `the first recent selection loads albums and top songs once`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)
        advanceUntilIdle()

        viewModel.selectTab(LibraryTab.Recent)
        advanceUntilIdle()

        assertThat(api.calls).contains("recentAlbums:100")
        assertThat(api.calls).contains("recent")
        assertThat(viewModel.state.value.recentAlbums)
            .isInstanceOf(SectionState.Content::class.java)
        assertThat(viewModel.state.value.recent)
            .isInstanceOf(SectionState.Content::class.java)
    }

    @Test
    fun `reselecting recent refreshes the albums but keeps the shown list`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)
        advanceUntilIdle()
        viewModel.selectTab(LibraryTab.Recent)
        advanceUntilIdle()

        // Hold the refresh open: the shown list must survive it (no skeleton flash).
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        api.recentAlbumsGate = gate
        api.recentAlbumsResult = ApiResult.Success(listOf(recentAlbum(2)))
        viewModel.selectTab(LibraryTab.Artists)
        viewModel.selectTab(LibraryTab.Recent)
        runCurrent()

        val shown = viewModel.state.value.recentAlbums
        assertThat((shown as SectionState.Content).value.first().album.id).isEqualTo(101)

        gate.complete(Unit)
        advanceUntilIdle()

        val refreshed = viewModel.state.value.recentAlbums
        assertThat((refreshed as SectionState.Content).value.first().album.id).isEqualTo(102)
        // Top songs fetched once per session; the refresh only touches the albums.
        assertThat(api.calls.count { it == "recentAlbums:100" }).isEqualTo(2)
        assertThat(api.calls.count { it == "recent" }).isEqualTo(1)
    }

    @Test
    fun `a recent refresh failure keeps the shown albums`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)
        advanceUntilIdle()
        viewModel.selectTab(LibraryTab.Recent)
        advanceUntilIdle()
        val shown = viewModel.state.value.recentAlbums

        api.recentAlbumsResult = ApiResult.NetworkError(IOException("offline"))
        viewModel.selectTab(LibraryTab.Artists)
        viewModel.selectTab(LibraryTab.Recent)
        advanceUntilIdle()

        assertThat(viewModel.state.value.recentAlbums).isEqualTo(shown)
    }

    @Test
    fun `a first-load failure on recent offers retry`() = runTest {
        useTestMain()
        api.recentAlbumsResult = ApiResult.NetworkError(IOException("offline"))
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)
        advanceUntilIdle()

        viewModel.selectTab(LibraryTab.Recent)
        advanceUntilIdle()

        assertThat(viewModel.state.value.recentAlbums)
            .isEqualTo(SectionState.Failed(LoadError.Network))

        api.recentAlbumsResult = ApiResult.Success(listOf(recentAlbum(1)))
        viewModel.retry(LibraryTab.Recent)
        advanceUntilIdle()

        assertThat(viewModel.state.value.recentAlbums)
            .isInstanceOf(SectionState.Content::class.java)
    }

    @Test
    fun `the recent toggle switches between albums and top songs`() = runTest {
        useTestMain()
        val viewModel = LibraryViewModel(api, signedInSession(), collection, nowPlaying)
        advanceUntilIdle()
        viewModel.selectTab(LibraryTab.Recent)

        assertThat(viewModel.state.value.recentSection).isEqualTo(RecentSection.Albums)

        viewModel.selectRecentSection(RecentSection.TopSongs)

        assertThat(viewModel.state.value.recentSection).isEqualTo(RecentSection.TopSongs)
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
