package one.tunkshif.swirlmusic.ui.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmLibraryApi
import one.tunkshif.swirlmusic.data.model.Track
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
        val detail = api.playlistDetail(id)
        if (detail is ApiResult.Success) {
            val playlist = detail.value
            _state.value = TrackListUiState(
                title = playlist.name,
                artworkUrl = playlist.artworkUrl,
                subtitle = listOfNotNull(
                    playlist.creatorName,
                    playlist.trackCount.toString() + " tracks",
                ).joinToString(" · "),
            )
        }
        val tracks = api.playlistTracks(id, limit = PLAYLIST_TRACK_LIMIT, offset = 0)
        applyTracks(tracks)
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
        const val PLAYLIST_TRACK_LIMIT = 500
    }
}
