package one.tunkshif.swirlmusic.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmArtistApi
import one.tunkshif.swirlmusic.data.library.CollectionRepository
import one.tunkshif.swirlmusic.data.library.ToggleResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import one.tunkshif.swirlmusic.data.library.TrackListRepository
import one.tunkshif.swirlmusic.data.media.ArtworkSeedSource
import one.tunkshif.swirlmusic.ui.player.toAlbumSeeds
import one.tunkshif.swirlmusic.data.model.ArtistDescription
import one.tunkshif.swirlmusic.data.model.ArtistRelease
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState

data class ArtistUiState(
    val name: String = "",
    val avatarUrl: String? = null,
    val briefDesc: String? = null,
    val songCount: Int? = null,
    val albumCount: Int? = null,
    val topSongs: SectionState<List<Track>> = SectionState.Loading,
    /** The fresh `/artist/detail/dynamic` read (0011-D45). */
    val followState: one.tunkshif.swirlmusic.ui.components.CollectionSlotState =
        one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Loading,
    val followed: Boolean = false,
    val followPending: Boolean = false,
    val filter: ReleaseFilter = ReleaseFilter.All,
    val releases: List<ArtistRelease> = emptyList(),
    val releasesLoading: Boolean = false,
    val releasesComplete: Boolean = false,
    val releasesError: LoadError? = null,
    val description: DescriptionState = DescriptionState.Idle,
)

sealed interface DescriptionState {
    data object Idle : DescriptionState
    data object Loading : DescriptionState
    data class Loaded(val value: ArtistDescription) : DescriptionState
    data class Failed(val error: LoadError) : DescriptionState
}

enum class ArtistMessage { Followed, Unfollowed, FollowFailed }

/** The albums-tab type filter (0010-D43/0011-D49). */
enum class ReleaseFilter { All, Albums, Eps, Singles }

private fun ReleaseFilter.kind() = when (this) {
    ReleaseFilter.All -> null
    ReleaseFilter.Albums -> one.tunkshif.swirlmusic.data.model.ReleaseKind.Album
    ReleaseFilter.Eps -> one.tunkshif.swirlmusic.data.model.ReleaseKind.Ep
    ReleaseFilter.Singles -> one.tunkshif.swirlmusic.data.model.ReleaseKind.Single
}

fun ArtistUiState.availableFilters(): List<ReleaseFilter> =
    if (!releasesComplete) {
        ReleaseFilter.entries
    } else {
        ReleaseFilter.entries.filter { f ->
            f == ReleaseFilter.All || releases.any { it.kind == f.kind() }
        }
    }

fun ArtistUiState.filteredReleases() =
    releases.filter { filter.kind() == null || it.kind == filter.kind() }

/**
 * The artist screen (0010-D40): `/artists` paints the header and 50 hot songs
 * stale-while-revalidate on the `artist:<id>` snapshot; the discography pages on demand.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val repository: TrackListRepository,
    private val api: NcmArtistApi,
    private val collection: CollectionRepository,
    artworkSeedSource: ArtworkSeedSource,
    private val currentTrack: one.tunkshif.swirlmusic.player.CurrentTrackSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()

    private val _messages = Channel<ArtistMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadedFor: Long? = null
    private val filterState = savedStateHandle.getStateFlow("filter", ReleaseFilter.All)

    fun selectFilter(f: ReleaseFilter) {
        savedStateHandle["filter"] = f
        _state.value = _state.value.copy(filter = f)
    }

    /** The playing track's id for row selection (0012-D52). Lazy dispatcher gotcha. */
    val nowPlayingId: StateFlow<Long?> by lazy {
        currentTrack.currentTrackId
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** Artwork seed for the screen's colour theme (0011-D47). Lazy so the
     *  view-model scope binds after tests install their dispatcher. */
    val seed: StateFlow<Int?> by lazy {
        state
            .map { it.avatarUrl }
            .toAlbumSeeds(artworkSeedSource)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
    private var artistId = 0L
    private var followJob: Job? = null
    private var toggleGeneration = 0
    private var releasesJob: Job? = null
    private var descriptionJob: Job? = null

    fun load(id: Long) {
        if (loadedFor == id) return
        loadedFor = id
        artistId = id
        followJob?.cancel()
        releasesJob?.cancel()
        descriptionJob?.cancel()
        releasesOffset = 0
        releasePages = 0
        _state.value = ArtistUiState(filter = filterState.value)
        viewModelScope.launch { loadDetail(id) }
        followJob = viewModelScope.launch {
            // Last-known paints immediately (0012-D51); the fresh read only corrects it.
            if (!_state.value.followPending) {
                _state.value = _state.value.copy(
                    followState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready,
                    followed = collection.lastKnownArtistFollowed(id) ?: false,
                )
            }
            val readGen = toggleGeneration
            when (val result = collection.artistFollowed(id)) {
                is ApiResult.Success ->
                    if (readGen == toggleGeneration && !_state.value.followPending) {
                        _state.value = _state.value.copy(
                            followState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready,
                            followed = result.value,
                        )
                    }
                else -> Unit // A failed read keeps what is shown.
            }
        }
    }

    fun retry() {
        loadedFor = null
        val id = artistId
        load(id)
    }

    private suspend fun loadDetail(id: Long) {
        val key = TrackListRepository.artistKey(id)
        val cached = repository.cached(key, limit = Int.MAX_VALUE)
        val hadSnapshot = cached != null
        if (cached != null) {
            _state.value = _state.value.copy(
                name = cached.title,
                avatarUrl = cached.artworkUrl,
                briefDesc = cached.description,
                topSongs = SectionState.Content(cached.firstTracks),
            )
        }
        when (val result = repository.fetchArtist(id)) {
            is ApiResult.Success -> result.value.let {
                _state.value = _state.value.copy(
                    name = it.title,
                    avatarUrl = it.artworkUrl,
                    briefDesc = it.description,
                    songCount = it.songCount,
                    albumCount = it.albumCount,
                    topSongs = SectionState.Content(it.firstTracks),
                )
            }
            is ApiResult.NetworkError -> if (!hadSnapshot) fail(LoadError.Network)
            is ApiResult.HttpError, is ApiResult.Unauthorized -> if (!hadSnapshot) fail(LoadError.Server)
        }
    }

    fun toggleFollow() {
        if (_state.value.followState != one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready) return
        val current = _state.value.followed
        if (_state.value.followPending) return
        toggleGeneration++
        _state.value = _state.value.copy(followed = !current, followPending = true)
        viewModelScope.launch {
            when (collection.setArtistFollowed(artistId, !current)) {
                ToggleResult.Done -> {
                    _state.value = _state.value.copy(followPending = false)
                    _messages.send(
                        if (!current) ArtistMessage.Followed else ArtistMessage.Unfollowed,
                    )
                }
                ToggleResult.Refused, is ToggleResult.Failed -> {
                    _state.value = _state.value.copy(followed = current, followPending = false)
                    _messages.send(ArtistMessage.FollowFailed)
                }
            }
        }
    }

    /** Starts the discography loop once; a failure freezes it until [retryReleases]. */
    fun showReleases() {
        val state = _state.value
        if (state.releasesLoading || state.releasesComplete || state.releasesError != null) return
        if (releasesJob?.isActive == true) return
        releasesJob = viewModelScope.launch { loadReleases(releasesOffset) }
    }

    /** Continues the discography from the failed offset, keeping the loaded items. */
    fun retryReleases() {
        val state = _state.value
        if (state.releasesError == null || state.releasesLoading) return
        releasesJob = viewModelScope.launch { loadReleases(releasesOffset) }
    }

    private var releasesOffset = 0
    private var releasePages = 0

    private suspend fun loadReleases(offset: Int) {
        _state.value = _state.value.copy(releasesLoading = true, releasesError = null)
        var next = offset
        while (true) {
            when (val result = api.artistAlbums(artistId, PAGE_SIZE, next)) {
                is ApiResult.Success -> {
                    val items = result.value.items
                    // Record the page's isSub flags as last-known saves (0012-D51).
                    collection.rememberAlbums(items.associate { it.id to it.saved })
                    _state.value = _state.value.copy(
                        releases = (_state.value.releases + items).distinctBy { it.id },
                    )
                    releasePages++
                    next += PAGE_SIZE
                    releasesOffset = next
                    if (!result.value.hasMore || items.isEmpty() || releasePages >= MAX_PAGES) {
                        _state.value = _state.value.copy(
                            releasesLoading = false,
                            releasesComplete = true,
                        )
                        return
                    }
                }
                is ApiResult.NetworkError -> {
                    _state.value = _state.value.copy(
                        releasesLoading = false,
                        releasesError = LoadError.Network,
                    )
                    return
                }
                is ApiResult.HttpError, is ApiResult.Unauthorized -> {
                    _state.value = _state.value.copy(
                        releasesLoading = false,
                        releasesError = LoadError.Server,
                    )
                    return
                }
            }
        }
    }

    fun loadDescription() {
        val state = _state.value.description
        if (state is DescriptionState.Loading || state is DescriptionState.Loaded) return
        if (descriptionJob?.isActive == true) return
        descriptionJob = viewModelScope.launch {
            _state.value = _state.value.copy(description = DescriptionState.Loading)
            when (val result = api.artistDescription(artistId)) {
                is ApiResult.Success ->
                    _state.value = _state.value.copy(description = DescriptionState.Loaded(result.value))
                is ApiResult.NetworkError ->
                    _state.value = _state.value.copy(description = DescriptionState.Failed(LoadError.Network))
                is ApiResult.HttpError, is ApiResult.Unauthorized ->
                    _state.value = _state.value.copy(description = DescriptionState.Failed(LoadError.Server))
            }
        }
    }

    private fun fail(error: LoadError) {
        _state.value = _state.value.copy(topSongs = SectionState.Failed(error))
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 10
    }
}
