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
class CollectionRepositoryTest {

    private val api = FakeCollectionApi()
    private val repository = CollectionRepository(api)

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
}
