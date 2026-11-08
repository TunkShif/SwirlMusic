package one.tunkshif.swirlmusic.data.library

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.WriteOutcome
import one.tunkshif.swirlmusic.ui.common.LoadError
import org.junit.Test

/** Changes flow only on Done; Refused and failures stay silent. */
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36])
class CollectionRepositoryTest {

    private val api = FakeCollectionApi()
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(
        org.robolectric.RuntimeEnvironment.getApplication(),
        one.tunkshif.swirlmusic.data.cache.SwirlDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
    private val repository = CollectionRepository(
        api,
        db.trackListDao(),
        one.tunkshif.swirlmusic.data.cache.CacheGate(db.trackListDao(), dispatcher),
        object : one.tunkshif.swirlmusic.data.api.TimeSource { override fun nowMillis() = 1_000L },
        dispatcher,
    )

    @Test
    fun `a Done write emits its tab change`() = runTest {
        val received = mutableListOf<LibraryChange>()
        backgroundScope.launch { repository.changes.toList(received) }
        runCurrent()

        assertThat(repository.setAlbumSaved(1, saved = true)).isEqualTo(ToggleResult.Done)
        assertThat(repository.setArtistFollowed(2, followed = true)).isEqualTo(ToggleResult.Done)
        runCurrent()

        assertThat(received).containsExactly(LibraryChange.Albums, LibraryChange.Artists).inOrder()
    }

    @Test
    fun `a Refused write emits nothing`() = runTest {
        val received = mutableListOf<LibraryChange>()
        backgroundScope.launch { repository.changes.toList(received) }
        runCurrent()
        api.setAlbumSavedResult = ApiResult.Success(WriteOutcome.Refused)

        assertThat(repository.setAlbumSaved(1, saved = true)).isEqualTo(ToggleResult.Refused)
        runCurrent()

        assertThat(received).isEmpty()
    }

    @Test
    fun `a network failure maps to LoadError Network and emits nothing`() = runTest {
        val received = mutableListOf<LibraryChange>()
        backgroundScope.launch { repository.changes.toList(received) }
        runCurrent()
        api.setAlbumSavedResult = ApiResult.NetworkError(IOException("offline"))

        val result = repository.setAlbumSaved(1, saved = true)
        runCurrent()

        assertThat(result).isEqualTo(ToggleResult.Failed(LoadError.Network))
        assertThat(received).isEmpty()
    }

    @Test
    fun `an http failure maps to LoadError Server`() = runTest {
        api.setArtistFollowedResult = ApiResult.HttpError(500, "{}")

        assertThat(repository.setArtistFollowed(2, followed = true))
            .isEqualTo(ToggleResult.Failed(LoadError.Server))
    }

    @Test
    fun `an unauthorized write maps to LoadError Server`() = runTest {
        api.setArtistFollowedResult = ApiResult.Unauthorized("{}")

        assertThat(repository.setArtistFollowed(2, followed = true))
            .isEqualTo(ToggleResult.Failed(LoadError.Server))
    }

    @Test
    fun `state reads pass through`() = runTest {
        api.albumSavedResult = ApiResult.Success(true)
        api.artistFollowedResult = ApiResult.Success(false)

        assertThat(repository.albumSaved(1)).isEqualTo(ApiResult.Success(true))
        assertThat(repository.artistFollowed(2)).isEqualTo(ApiResult.Success(false))
    }
    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `last-known state round trips`() = runTest {
        assertThat(repository.lastKnownAlbumSaved(1)).isNull()

        repository.rememberAlbums(mapOf(1L to true, 2L to false))
        repository.rememberArtists(mapOf(3L to true))

        assertThat(repository.lastKnownAlbumSaved(1)).isTrue()
        assertThat(repository.lastKnownAlbumSaved(2)).isFalse()
        assertThat(repository.lastKnownArtistFollowed(3)).isTrue()
    }

    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `reads and Done writes are recorded and Refused and Failed are not`() = runTest {
        api.albumSavedResult = ApiResult.Success(true)
        api.artistFollowedResult = ApiResult.Success(false)
        repository.albumSaved(5)
        repository.artistFollowed(6)
        assertThat(repository.lastKnownAlbumSaved(5)).isTrue()
        assertThat(repository.lastKnownArtistFollowed(6)).isFalse()

        repository.setAlbumSaved(5, false)
        assertThat(repository.lastKnownAlbumSaved(5)).isFalse()

        api.setAlbumSavedResult = ApiResult.Success(WriteOutcome.Refused)
        repository.setAlbumSaved(7, true)
        assertThat(repository.lastKnownAlbumSaved(7)).isNull()

        api.setArtistFollowedResult = ApiResult.NetworkError(IOException("offline"))
        repository.setArtistFollowed(8, true)
        assertThat(repository.lastKnownArtistFollowed(8)).isNull()
    }

    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `the account cache wipe clears remembered state`() = runTest {
        repository.rememberAlbums(mapOf(1L to true))
        assertThat(repository.lastKnownAlbumSaved(1)).isTrue()

        db.trackListDao().clearAll()

        assertThat(repository.lastKnownAlbumSaved(1)).isNull()
    }

}
