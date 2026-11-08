package one.tunkshif.swirlmusic.ui.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.library.ArtistAvatarRepository
import one.tunkshif.swirlmusic.data.library.CollectionRepository
import one.tunkshif.swirlmusic.data.library.ToggleResult
import one.tunkshif.swirlmusic.data.library.TrackListRepository
import one.tunkshif.swirlmusic.data.model.ArtistRef
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
    /** Playlists show their real count in the subtitle (localized plural); null elsewhere. */
    val trackCount: Int? = null,
    val description: String? = null,
    val dateMillis: Long? = null,
    val dateKind: DateKind? = null,
    val company: String? = null,
    val albumType: String? = null,
    val tags: List<String> = emptyList(),
    /** More ids wait beyond the loaded rows (playlists only). */
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    /** A failed chunk keeps its rows; the footer shows retry. */
    val moreError: LoadError? = null,
    /** Albums only: null until the fresh `/album/detail/dynamic` read lands (or fails). */
    val saved: Boolean? = null,
    val savePending: Boolean = false,
    /** Album header artists (0010-D39); a menu at 2+, a direct link at 1. */
    val artists: List<ArtistRef> = emptyList(),
    val artistAvatars: Map<Long, String?> = emptyMap(),
)

/** One-shot messages for the toast pill. */
enum class TrackListMessage { Saved, Removed, AlbumLocked, SaveFailed }

@HiltViewModel
class TrackListViewModel @Inject constructor(
    private val repository: TrackListRepository,
    private val collection: CollectionRepository,
    private val artistAvatars: ArtistAvatarRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackListUiState())
    val state: StateFlow<TrackListUiState> = _state.asStateFlow()

    private val _messages = Channel<TrackListMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

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
        savedJob?.cancel()
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
        val key = TrackListRepository.playlistKey(id)
        // Stale-while-revalidate: paint the snapshot now, refresh over the network after.
        val cached = repository.cached(key)
        val hadSnapshot = cached != null
        if (cached != null) {
            emitList(cached.title, cached.artworkUrl, cached.subtitle, cached.firstTracks, cached.trackCount,
                HeaderExtras(cached.description, cached.dateMillis,
                    if (cached.company == null) DateKind.Created else DateKind.Released,
                    cached.company, cached.albumType, cached.tags))
            setPending(cached.orderedIds, cached.firstTracks)
        }
        when (val result = repository.fetchPlaylist(id)) {
            is ApiResult.Success -> {
                val fetched = result.value
                val current = _state.value
                _state.value = current.copy(
                    title = fetched.title,
                    artworkUrl = fetched.artworkUrl,
                    subtitle = fetched.subtitle,
                    trackCount = fetched.trackCount,
                    description = fetched.description,
                    dateMillis = fetched.dateMillis,
                    dateKind = if (fetched.company == null) DateKind.Created else DateKind.Released,
                    company = fetched.company,
                    albumType = fetched.albumType,
                    tags = fetched.tags,
                )
                if (fetched.orderChanged || !hadSnapshot) {
                    // Rebuild the rows in the new order, keeping the loaded count where possible.
                    val keep = if (hadSnapshot) {
                        (current.tracks as? SectionState.Content)?.value?.size ?: 0
                    } else {
                        0
                    }
                    val count = keep.coerceAtLeast(fetched.firstTracks.size)
                        .coerceAtMost(fetched.orderedIds.size)
                    val rows = when (val resolved = repository.tracksByIds(fetched.orderedIds.take(count))) {
                        is ApiResult.Success -> resolved.value
                        else -> fetched.firstTracks
                    }
                    _state.value = _state.value.copy(tracks = SectionState.Content(rows))
                    setPending(fetched.orderedIds, rows)
                }
                if ((_state.value.tracks as? SectionState.Content)?.value.isNullOrEmpty() &&
                    fetched.orderedIds.isNotEmpty()
                ) {
                    loadMore()
                }
            }

            is ApiResult.NetworkError -> if (!hadSnapshot) fail(LoadError.Network)
            is ApiResult.HttpError, is ApiResult.Unauthorized -> if (!hadSnapshot) fail(LoadError.Server)
        }
    }

    private fun emitList(
        title: String,
        artworkUrl: String?,
        subtitle: String?,
        tracks: List<Track>,
        trackCount: Int? = null,
        extras: HeaderExtras = HeaderExtras(),
        artists: List<ArtistRef> = emptyList(),
    ) {
        // A fresh list resets the content fields (footer state must not linger); the album
        // collection fields live outside it and carry over.
        _state.value = TrackListUiState(
            title = title,
            artworkUrl = artworkUrl,
            subtitle = subtitle,
            trackCount = trackCount,
            tracks = SectionState.Content(tracks),
            description = extras.description,
            dateMillis = extras.dateMillis,
            dateKind = extras.dateKind,
            company = extras.company,
            albumType = extras.albumType,
            tags = extras.tags,
            artists = artists,
            saved = _state.value.saved,
            savePending = _state.value.savePending,
            artistAvatars = _state.value.artistAvatars,
        )
    }

    private data class HeaderExtras(
        val description: String? = null,
        val dateMillis: Long? = null,
        val dateKind: DateKind? = null,
        val company: String? = null,
        val albumType: String? = null,
        val tags: List<String> = emptyList(),
    )

    private fun setPending(orderedIds: List<Long>, loaded: List<Track>) {
        val covered = loaded.map { it.id }.toSet()
        pendingIds = orderedIds.filter { it !in covered }
        _state.value = _state.value.copy(canLoadMore = pendingIds.isNotEmpty())
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
            when (val result = repository.tracksByIds(slice)) {
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
                when (val result = repository.tracksByIds(slice)) {
                    is ApiResult.Success -> if (result.value.isNotEmpty()) return@TrackPager result.value
                    else -> return@TrackPager null
                }
            }
            null
        }
    }

    /**
     * Albums read their saved state fresh alongside the content (0010-D36); a failure leaves
     * [TrackListUiState.saved] null and the button stays hidden. No retry, no cache.
     */
    private var savedJob: Job? = null

    fun toggleSave() {
        val current = _state.value.saved ?: return
        if (_state.value.savePending) return
        val albumId = (source as? TrackListSource.Album)?.id ?: return
        _state.value = _state.value.copy(saved = !current, savePending = true)
        viewModelScope.launch {
            when (val result = collection.setAlbumSaved(albumId, !current)) {
                ToggleResult.Done -> {
                    _state.value = _state.value.copy(savePending = false)
                    _messages.send(
                        if (!current) TrackListMessage.Saved else TrackListMessage.Removed,
                    )
                }
                ToggleResult.Refused -> {
                    _state.value = _state.value.copy(saved = current, savePending = false)
                    _messages.send(TrackListMessage.AlbumLocked)
                }
                is ToggleResult.Failed -> {
                    _state.value = _state.value.copy(saved = current, savePending = false)
                    _messages.send(TrackListMessage.SaveFailed)
                }
            }
        }
    }

    private suspend fun loadAlbum(id: Long) {
        savedJob?.cancel()
        savedJob = viewModelScope.launch {
            when (val result = collection.albumSaved(id)) {
                // A toggle already in flight owns `saved` now; don't clobber it.
                is ApiResult.Success ->
                    if (!_state.value.savePending) {
                        _state.value = _state.value.copy(saved = result.value)
                    }
                else -> Unit
            }
        }
        val hadSnapshot = emitCached(TrackListRepository.albumKey(id))
        when (val result = repository.fetchAlbum(id)) {
            is ApiResult.Success -> result.value.let {
                emitList(it.title, it.artworkUrl, it.subtitle, it.firstTracks, it.trackCount,
                    HeaderExtras(it.description, it.dateMillis, it.albumType?.let { DateKind.Released } ?: if (it.dateMillis != null) DateKind.Released else null, it.company, it.albumType, it.tags),
                    artists = it.artists)
                loadArtistAvatars(it.artists)
            }

            is ApiResult.NetworkError -> if (!hadSnapshot) fail(LoadError.Network)
            is ApiResult.HttpError, is ApiResult.Unauthorized -> if (!hadSnapshot) fail(LoadError.Server)
        }
    }

    /** Menu avatars are only needed when the header can show the choose-artist menu. */
    private fun loadArtistAvatars(artists: List<ArtistRef>) {
        if (artists.size < 2) return
        viewModelScope.launch {
            val fetched = artistAvatars.avatars(artists.map { it.id })
            _state.value = _state.value.copy(
                artistAvatars = artists.associate { it.id to fetched[it.id] },
            )
        }
    }

    private suspend fun loadArtist(id: Long) {
        val hadSnapshot = emitCached(TrackListRepository.artistKey(id))
        when (val result = repository.fetchArtist(id)) {
            is ApiResult.Success -> result.value.let {
                emitList(it.title, it.artworkUrl, it.subtitle, it.firstTracks, it.trackCount,
                    HeaderExtras(it.description, it.dateMillis, it.albumType?.let { DateKind.Released } ?: if (it.dateMillis != null) DateKind.Released else null, it.company, it.albumType, it.tags))
            }

            is ApiResult.NetworkError -> if (!hadSnapshot) fail(LoadError.Network)
            is ApiResult.HttpError, is ApiResult.Unauthorized -> if (!hadSnapshot) fail(LoadError.Server)
        }
    }

    /** Emit the cached snapshot (albums/artists resolve all ids). True when one existed. */
    private suspend fun emitCached(key: String): Boolean {
        val cached = repository.cached(key, limit = Int.MAX_VALUE) ?: return false
        emitList(cached.title, cached.artworkUrl, cached.subtitle, cached.firstTracks, cached.trackCount,
                HeaderExtras(cached.description, cached.dateMillis,
                    if (cached.company == null) DateKind.Created else DateKind.Released,
                    cached.company, cached.albumType, cached.tags),
                artists = cached.artists)
        return true
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
