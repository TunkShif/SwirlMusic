package one.tunkshif.swirlmusic.ui.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmLibraryApi
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.player.PlayerController
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState

/** What a track list is showing. The navigation keys map onto this. */
sealed interface TrackListSource {
    data class Playlist(val id: Long) : TrackListSource
    data class Album(val id: Long) : TrackListSource
    data class Artist(val id: Long) : TrackListSource
}

data class TrackListUiState(
    val title: String = "",
    val artworkUrl: String? = null,
    val subtitle: String? = null,
    val tracks: SectionState<List<Track>> = SectionState.Loading,
    /** More ids wait beyond the loaded rows (playlists only). */
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    /** A failed chunk keeps its rows; the footer shows retry. */
    val moreError: LoadError? = null,
)

@HiltViewModel
class TrackListViewModel @Inject constructor(
    private val api: NcmLibraryApi,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackListUiState())
    val state: StateFlow<TrackListUiState> = _state.asStateFlow()

    private var loadedFor: TrackListSource? = null
    private var source: TrackListSource = TrackListSource.Playlist(0)

    /** The destination is a parameter, so the view model stays injectable. */
    fun load(source: TrackListSource) {
        if (loadedFor == source) return
        loadedFor = source
        this.source = source
        // A new list starts blank: the previous list's title and artwork must not linger.
        _state.value = TrackListUiState()
        fetch()
    }

    fun retry() {
        loadedFor = null
        fetch()
    }

    private fun fetch() {
        if (source is TrackListSource.Playlist && (source as TrackListSource.Playlist).id == 0L) return
        // A reload invalidates any in-flight chunk: it would append to the wrong list.
        loadGeneration++
        moreJob?.cancel()
        val source = this.source
        _state.value = _state.value.copy(tracks = SectionState.Loading)
        viewModelScope.launch {
            when (source) {
                is TrackListSource.Playlist -> loadPlaylist(source.id)
                is TrackListSource.Album -> loadAlbum(source.id)
                is TrackListSource.Artist -> loadArtist(source.id)
            }
        }
    }

    private suspend fun loadPlaylist(id: Long) {
        when (val result = api.playlistDetail(id)) {
            is ApiResult.Success -> {
                val contents = result.value
                val playlist = contents.detail
                _state.value = TrackListUiState(
                    title = playlist.name,
                    artworkUrl = playlist.artworkUrl,
                    subtitle = listOfNotNull(
                        playlist.creatorName,
                        playlist.trackCount.toString() + " tracks",
                    ).joinToString(" · "),
                    tracks = SectionState.Content(contents.firstTracks),
                )
                // Everything beyond the bundled tracks is paged in by id slice.
                val covered = contents.firstTracks.map { it.id }.toSet()
                pendingIds = contents.trackIds.filter { it !in covered }
                _state.value = _state.value.copy(canLoadMore = pendingIds.isNotEmpty())
                if (contents.firstTracks.isEmpty() && contents.trackIds.isNotEmpty()) {
                    loadMore()
                }
            }

            is ApiResult.NetworkError -> fail(LoadError.Network)
            is ApiResult.HttpError, is ApiResult.Unauthorized -> fail(LoadError.Server)
        }
    }

    /**
     * Playlist paging: ids not covered by the detail response, in list order. `songsByIds`
     * may reorder or drop ids, so appends keep requested order and dropped ids still count
     * as consumed.
     */
    private var pendingIds: List<Long> = emptyList()
    private var moreJob: Job? = null
    private var loadGeneration = 0

    fun loadMore() {
        if (source !is TrackListSource.Playlist) return
        if (moreJob?.isActive == true || pendingIds.isEmpty()) return
        val generation = loadGeneration
        _state.value = _state.value.copy(loadingMore = true, moreError = null)
        moreJob = viewModelScope.launch {
            val slice = pendingIds.take(PAGE_SIZE)
            when (val result = api.songsByIds(slice)) {
                is ApiResult.Success -> {
                    // A retry/reload may have replaced the list while this call was in flight.
                    if (generation != loadGeneration) return@launch
                    pendingIds = pendingIds.drop(slice.size)
                    val current = (_state.value.tracks as? SectionState.Content)?.value.orEmpty()
                    _state.value = _state.value.copy(
                        tracks = SectionState.Content(current + result.value),
                        canLoadMore = pendingIds.isNotEmpty(),
                        loadingMore = false,
                    )
                }

                is ApiResult.NetworkError -> {
                    if (generation != loadGeneration) return@launch
                    _state.value = _state.value.copy(loadingMore = false, moreError = LoadError.Network)
                }

                is ApiResult.HttpError, is ApiResult.Unauthorized -> {
                    if (generation != loadGeneration) return@launch
                    _state.value = _state.value.copy(loadingMore = false, moreError = LoadError.Server)
                }
            }
        }
    }

    /**
     * A pager over the ids not yet loaded, for "Play all" and tap-to-play on a partial list.
     * Snapshots the pending ids so it never touches VM state or viewModelScope. Empty
     * successful slices are skipped; null means the snapshot is exhausted or a call failed.
     */
    fun playPager(): PlayerController.TrackPager? {
        if (source !is TrackListSource.Playlist) return null
        val snapshot = pendingIds.toList()
        if (snapshot.isEmpty()) return null
        var offset = 0
        return PlayerController.TrackPager {
            while (offset < snapshot.size) {
                val slice = snapshot.drop(offset).take(PAGE_SIZE)
                offset += slice.size
                when (val result = api.songsByIds(slice)) {
                    is ApiResult.Success -> if (result.value.isNotEmpty()) return@TrackPager result.value
                    else -> return@TrackPager null
                }
            }
            null
        }
    }

    private suspend fun loadAlbum(id: Long) {
        when (val detail = api.albumDetail(id)) {
            is ApiResult.Success -> _state.value = TrackListUiState(
                title = detail.value.name,
                artworkUrl = detail.value.artworkUrl,
                subtitle = listOfNotNull(
                    detail.value.artistLine.takeIf { it.isNotBlank() },
                    detail.value.publishYear?.toString(),
                ).joinToString(" · "),
                tracks = SectionState.Content(detail.value.tracks),
            )

            is ApiResult.NetworkError -> fail(LoadError.Network)
            is ApiResult.HttpError, is ApiResult.Unauthorized -> fail(LoadError.Server)
        }
    }

    private suspend fun loadArtist(id: Long) {
        when (val detail = api.artistDetail(id)) {
            is ApiResult.Success -> _state.value = TrackListUiState(
                title = detail.value.name,
                artworkUrl = detail.value.artworkUrl,
                subtitle = null,
                tracks = SectionState.Content(detail.value.tracks),
            )

            is ApiResult.NetworkError -> fail(LoadError.Network)
            is ApiResult.HttpError, is ApiResult.Unauthorized -> fail(LoadError.Server)
        }
    }

    private fun applyTracks(result: ApiResult<List<Track>>) {
        _state.value = when (result) {
            is ApiResult.Success -> _state.value.copy(tracks = SectionState.Content(result.value))
            is ApiResult.NetworkError -> {
                fail(LoadError.Network)
                _state.value
            }

            is ApiResult.HttpError, is ApiResult.Unauthorized -> {
                fail(LoadError.Server)
                _state.value
            }
        }
    }

    private fun fail(error: LoadError) {
        _state.value = _state.value.copy(tracks = SectionState.Failed(error))
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
