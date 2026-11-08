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
    private val dao: one.tunkshif.swirlmusic.data.cache.TrackListDao,
    private val gate: one.tunkshif.swirlmusic.data.cache.CacheGate,
    private val timeSource: one.tunkshif.swirlmusic.data.api.TimeSource,
    @one.tunkshif.swirlmusic.di.IoDispatcher private val io: kotlinx.coroutines.CoroutineDispatcher,
) {

    private val _changes = MutableSharedFlow<LibraryChange>(extraBufferCapacity = 8)
    val changes: SharedFlow<LibraryChange> = _changes.asSharedFlow()

    suspend fun albumSaved(id: Long): ApiResult<Boolean> =
        api.albumSaved(id).also { result ->
            (result as? ApiResult.Success)?.let { rememberAlbums(mapOf(id to it.value)) }
        }

    /** The cached state, or null when unknown (0012-D51). */
    suspend fun lastKnownAlbumSaved(id: Long): Boolean? =
        kotlinx.coroutines.withContext(io) { dao.collectionState("album", id) }

    suspend fun lastKnownArtistFollowed(id: Long): Boolean? =
        kotlinx.coroutines.withContext(io) { dao.collectionState("artist", id) }

    suspend fun rememberAlbums(states: Map<Long, Boolean>) = remember("album", states)

    suspend fun rememberArtists(states: Map<Long, Boolean>) = remember("artist", states)

    private suspend fun remember(kind: String, states: Map<Long, Boolean>) {
        if (states.isEmpty()) return
        val now = timeSource.nowMillis()
        val generation = gate.generation
        gate.writeIfCurrent(generation) {
            upsertCollectionStates(
                states.map { (id, active) ->
                    one.tunkshif.swirlmusic.data.cache.CollectionStateEntity(kind, id, active, now)
                },
            )
        }
    }

    suspend fun setAlbumSaved(id: Long, saved: Boolean): ToggleResult =
        write(LibraryChange.Albums) { api.setAlbumSaved(id, saved) }.also { result ->
            if (result == ToggleResult.Done) rememberAlbums(mapOf(id to saved))
        }

    suspend fun artistFollowed(id: Long): ApiResult<Boolean> =
        api.artistFollowed(id).also { result ->
            (result as? ApiResult.Success)?.let { rememberArtists(mapOf(id to it.value)) }
        }

    suspend fun setArtistFollowed(id: Long, followed: Boolean): ToggleResult =
        write(LibraryChange.Artists) { api.setArtistFollowed(id, followed) }.also { result ->
            if (result == ToggleResult.Done) rememberArtists(mapOf(id to followed))
        }

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
