package one.tunkshif.swirlmusic.ui.artist

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.api.WriteOutcome
import one.tunkshif.swirlmusic.data.cache.CacheGate
import one.tunkshif.swirlmusic.data.cache.SwirlDatabase
import one.tunkshif.swirlmusic.data.library.CollectionRepository
import one.tunkshif.swirlmusic.data.library.FakeArtistApi
import one.tunkshif.swirlmusic.data.library.FakeCollectionApi
import one.tunkshif.swirlmusic.data.library.TrackListRepository
import one.tunkshif.swirlmusic.data.model.ArtistDescription
import one.tunkshif.swirlmusic.data.model.ArtistRelease
import one.tunkshif.swirlmusic.data.model.DescriptionSection
import one.tunkshif.swirlmusic.data.model.Page
import one.tunkshif.swirlmusic.data.model.ReleaseKind
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.library.FakeLibraryApi
import one.tunkshif.swirlmusic.ui.library.track
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
class ArtistViewModelTest {

    private val db = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        SwirlDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val libraryApi = FakeLibraryApi()
    private val artistApi = FakeArtistApi()
    private val collectionApi = FakeCollectionApi()
    private val repository = TrackListRepository(
        libraryApi, db.trackListDao(),
        object : TimeSource { override fun nowMillis() = 1_000L },
        UnconfinedTestDispatcher(),
        CacheGate(db.trackListDao(), UnconfinedTestDispatcher()),
    )
    private val viewModel = ArtistViewModel(repository, artistApi, CollectionRepository(collectionApi))

    @Before
    fun setUp() {
        libraryApi.artistDetail = libraryApi.artistDetail.copy(
            briefDesc = "Taiwanese singer-songwriter.",
            songCount = 387,
            albumCount = 48,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.useTestMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    private fun releases(count: Int, from: Int = 0) = (from until from + count).map {
        ArtistRelease(it.toLong(), "Release $it", null, 1_600_000_000_000L, ReleaseKind.Album, 10)
    }

    @Test
    fun `the snapshot paints then the network replaces it`() = runTest {
        useTestMain()
        // Seed the snapshot with a stale name via a first fetch.
        libraryApi.artistDetail = libraryApi.artistDetail.copy(name = "Old Name")
        repository.fetchArtist(1)
        libraryApi.artistDetail = libraryApi.artistDetail.copy(name = "Kavinsky")

        viewModel.load(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.name).isEqualTo("Kavinsky")
        assertThat(viewModel.state.value.briefDesc).isEqualTo("Taiwanese singer-songwriter.")
        assertThat(viewModel.state.value.songCount).isEqualTo(387)
        assertThat(viewModel.state.value.albumCount).isEqualTo(48)
        assertThat(viewModel.state.value.topSongs).isInstanceOf(SectionState.Content::class.java)
    }

    @Test
    fun `a failure without a snapshot fails the songs section`() = runTest {
        useTestMain()
        libraryApi.failWith = ApiResult.NetworkError(IOException("offline"))

        viewModel.load(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.topSongs)
            .isEqualTo(SectionState.Failed(LoadError.Network))
    }

    @Test
    fun `a failed follow read leaves followed null`() = runTest {
        useTestMain()
        collectionApi.artistFollowedResult = ApiResult.NetworkError(IOException("offline"))

        viewModel.load(1)
        advanceUntilIdle()

        assertThat(viewModel.state.value.followed).isNull()
    }

    @Test
    fun `toggleFollow flips optimistically and reports Followed`() = runTest {
        useTestMain()
        collectionApi.artistFollowedResult = ApiResult.Success(false)
        val received = mutableListOf<ArtistMessage>()
        backgroundScope.launch { viewModel.messages.toList(received) }
        runCurrent()
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.toggleFollow()
        assertThat(viewModel.state.value.followed).isTrue()
        assertThat(viewModel.state.value.followPending).isTrue()

        advanceUntilIdle()
        runCurrent()

        assertThat(viewModel.state.value.followPending).isFalse()
        assertThat(collectionApi.calls).contains("setArtistFollowed:1:1")
        assertThat(received).containsExactly(ArtistMessage.Followed)
    }

    @Test
    fun `unfollow reports Unfollowed`() = runTest {
        useTestMain()
        collectionApi.artistFollowedResult = ApiResult.Success(true)
        val received = mutableListOf<ArtistMessage>()
        backgroundScope.launch { viewModel.messages.toList(received) }
        runCurrent()
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.toggleFollow()
        advanceUntilIdle()
        runCurrent()

        assertThat(viewModel.state.value.followed).isFalse()
        assertThat(received).containsExactly(ArtistMessage.Unfollowed)
    }

    @Test
    fun `a refused or failed follow reverts and reports the failure`() = runTest {
        useTestMain()
        collectionApi.artistFollowedResult = ApiResult.Success(false)
        collectionApi.setArtistFollowedResult = ApiResult.Success(WriteOutcome.Refused)
        val received = mutableListOf<ArtistMessage>()
        backgroundScope.launch { viewModel.messages.toList(received) }
        runCurrent()
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.toggleFollow()
        advanceUntilIdle()
        runCurrent()

        assertThat(viewModel.state.value.followed).isFalse()
        assertThat(viewModel.state.value.followPending).isFalse()
        assertThat(received).containsExactly(ArtistMessage.FollowFailed)
    }

    @Test
    fun `a second toggle while pending is ignored`() = runTest {
        useTestMain()
        collectionApi.artistFollowedResult = ApiResult.Success(false)
        collectionApi.writeGate = kotlinx.coroutines.CompletableDeferred()
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.toggleFollow()
        runCurrent()
        viewModel.toggleFollow()
        runCurrent()

        assertThat(collectionApi.calls.filter { it.startsWith("setArtistFollowed") }).hasSize(1)

        collectionApi.writeGate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `the discography pages sequentially until more is false`() = runTest {
        useTestMain()
        artistApi.setAlbums(
            listOf(0 to releases(100), 100 to releases(100, 100), 200 to releases(14, 200)),
        )
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.showReleases()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.releases).hasSize(214)
        assertThat(state.releasesComplete).isTrue()
        assertThat(artistApi.calls.filter { it.startsWith("albums:") })
            .containsExactly("albums:1:0", "albums:1:100", "albums:1:200").inOrder()
    }

    @Test
    fun `the discography stops after ten pages`() = runTest {
        useTestMain()
        // Every page says more: true.
        repeat(12) { page ->
            artistApi.albumPages[page * 100] =
                ApiResult.Success(Page(releases(100, page * 100), hasMore = true))
        }
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.showReleases()
        advanceUntilIdle()

        assertThat(viewModel.state.value.releases).hasSize(1_000)
        assertThat(viewModel.state.value.releasesComplete).isTrue()
        assertThat(artistApi.calls.filter { it.startsWith("albums:") }).hasSize(10)
    }

    @Test
    fun `a failed page keeps the loaded items and retry resumes`() = runTest {
        useTestMain()
        artistApi.albumPages[0] = ApiResult.Success(Page(releases(100), hasMore = true))
        artistApi.albumPages[100] = ApiResult.NetworkError(IOException("offline"))
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.showReleases()
        advanceUntilIdle()

        assertThat(viewModel.state.value.releases).hasSize(100)
        assertThat(viewModel.state.value.releasesError).isEqualTo(LoadError.Network)

        artistApi.albumPages[100] = ApiResult.Success(Page(releases(20, 100), hasMore = false))
        viewModel.retryReleases()
        advanceUntilIdle()

        assertThat(viewModel.state.value.releases).hasSize(120)
        assertThat(viewModel.state.value.releasesComplete).isTrue()
        assertThat(artistApi.calls.last { it.startsWith("albums:") }).isEqualTo("albums:1:100")
    }

    @Test
    fun `showReleases twice starts one loop`() = runTest {
        useTestMain()
        artistApi.setAlbums(listOf(0 to releases(5)))
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.showReleases()
        viewModel.showReleases()
        advanceUntilIdle()

        assertThat(artistApi.calls.filter { it.startsWith("albums:") }).hasSize(1)
    }

    @Test
    fun `the description loads on demand and is not refetched`() = runTest {
        useTestMain()
        artistApi.descriptionResult = ApiResult.Success(
            ArtistDescription(
                brief = "Bio",
                sections = listOf(DescriptionSection("Early", "Born in 1979.")),
            ),
        )
        viewModel.load(1)
        advanceUntilIdle()
        assertThat(viewModel.state.value.description).isEqualTo(DescriptionState.Idle)

        viewModel.loadDescription()
        advanceUntilIdle()

        val loaded = viewModel.state.value.description as DescriptionState.Loaded
        assertThat(loaded.value.sections).hasSize(1)

        viewModel.loadDescription()
        advanceUntilIdle()
        assertThat(artistApi.calls.filter { it == "desc:1" }).hasSize(1)
    }

    @Test
    fun `a failed description retries`() = runTest {
        useTestMain()
        artistApi.descriptionResult = ApiResult.NetworkError(IOException("offline"))
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.loadDescription()
        advanceUntilIdle()
        assertThat(viewModel.state.value.description)
            .isEqualTo(DescriptionState.Failed(LoadError.Network))

        artistApi.descriptionResult = ApiResult.Success(ArtistDescription("Bio", emptyList()))
        viewModel.loadDescription()
        advanceUntilIdle()
        assertThat(viewModel.state.value.description)
            .isInstanceOf(DescriptionState.Loaded::class.java)
    }
}
