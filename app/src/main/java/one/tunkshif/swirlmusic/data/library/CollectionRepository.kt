package one.tunkshif.swirlmusic.data.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmCollectionApi
import one.tunkshif.swirlmusic.data.api.WriteOutcome
import one.tunkshif.swirlmusic.ui.common.LoadError

/** Which library tab a successful write changed. */
enum class LibraryChange { Albums, Artists }

sealed interface ToggleResult {
    data object Done : ToggleResult
    data object Refused : ToggleResult
    data class Failed(val error: LoadError) : ToggleResult
}

/**
 * App-scoped save/follow writes (decision 0010-D37): optimistic callers toggle at once and
 * revert on [ToggleResult.Refused]/[ToggleResult.Failed]. A successful write emits its
 * [LibraryChange] so the Library reloads that tab.
 */
@Singleton
class CollectionRepository @Inject constructor(
    private val api: NcmCollectionApi,
) {

    private val _changes = MutableSharedFlow<LibraryChange>(extraBufferCapacity = 8)
    val changes: SharedFlow<LibraryChange> = _changes.asSharedFlow()

    suspend fun albumSaved(id: Long): ApiResult<Boolean> = api.albumSaved(id)

    suspend fun setAlbumSaved(id: Long, saved: Boolean): ToggleResult =
        write(LibraryChange.Albums) { api.setAlbumSaved(id, saved) }

    suspend fun artistFollowed(id: Long): ApiResult<Boolean> = api.artistFollowed(id)

    suspend fun setArtistFollowed(id: Long, followed: Boolean): ToggleResult =
        write(LibraryChange.Artists) { api.setArtistFollowed(id, followed) }

    private suspend fun write(
        change: LibraryChange,
        call: suspend () -> ApiResult<WriteOutcome>,
    ): ToggleResult = when (val result = call()) {
        is ApiResult.Success -> when (result.value) {
            WriteOutcome.Done -> {
                _changes.tryEmit(change)
                ToggleResult.Done
            }
            WriteOutcome.Refused -> ToggleResult.Refused
        }
        is ApiResult.NetworkError -> ToggleResult.Failed(LoadError.Network)
        is ApiResult.HttpError, is ApiResult.Unauthorized -> ToggleResult.Failed(LoadError.Server)
    }
}
