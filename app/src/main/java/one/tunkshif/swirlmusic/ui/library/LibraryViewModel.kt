package one.tunkshif.swirlmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmLibraryApi
import one.tunkshif.swirlmusic.data.library.CollectionRepository
import one.tunkshif.swirlmusic.data.library.LibraryChange
import one.tunkshif.swirlmusic.data.model.AlbumSummary
import one.tunkshif.swirlmusic.data.model.ArtistSummary
import one.tunkshif.swirlmusic.data.model.Page
import one.tunkshif.swirlmusic.data.model.PlayedEntry
import one.tunkshif.swirlmusic.data.model.PlaylistSummary
import one.tunkshif.swirlmusic.data.session.SessionRepository
import one.tunkshif.swirlmusic.data.session.SessionState
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.PagedList
import one.tunkshif.swirlmusic.ui.common.SectionState

enum class LibraryTab { Playlists, Albums, Artists, Recent }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.Playlists,
    val playlists: PagedList<PlaylistSummary> = PagedList(),
    val albums: PagedList<AlbumSummary> = PagedList(),
    val artists: PagedList<ArtistSummary> = PagedList(),
    val recent: SectionState<List<PlayedEntry>> = SectionState.Loading,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val api: NcmLibraryApi,
    session: SessionRepository,
    collection: CollectionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val userId: Long? =
        (session.state.value as? SessionState.SignedIn)?.profile?.userId

    init {
        loadPlaylists(reset = true)
        loadAlbums(reset = true)
        loadArtists(reset = true)
        loadRecent()
        // A save/follow elsewhere reloads exactly that tab, bypassing the server cache.
        viewModelScope.launch {
            collection.changes.collect { change ->
                when (change) {
                    LibraryChange.Albums -> loadAlbums(reset = true, fresh = true)
                    LibraryChange.Artists -> loadArtists(reset = true, fresh = true)
                }
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun retry(tab: LibraryTab) {
        when (tab) {
            LibraryTab.Playlists -> loadPlaylists(reset = true)
            LibraryTab.Albums -> loadAlbums(reset = true)
            LibraryTab.Artists -> loadArtists(reset = true)
            LibraryTab.Recent -> loadRecent()
        }
    }

    fun loadMore(tab: LibraryTab) {
        when (tab) {
            LibraryTab.Playlists -> loadPlaylists(reset = false)
            LibraryTab.Albums -> loadAlbums(reset = false)
            LibraryTab.Artists -> loadArtists(reset = false)
            LibraryTab.Recent -> Unit
        }
    }

    private fun loadPlaylists(reset: Boolean) = loadPage(
        reset = reset,
        current = { _state.value.playlists },
        update = { paged -> _state.update { it.copy(playlists = paged) } },
        fetch = { uid, offset -> api.userPlaylists(uid, PAGE_SIZE, offset) },
    )

    private fun loadAlbums(reset: Boolean, fresh: Boolean = false) = loadPage(
        reset = reset,
        current = { _state.value.albums },
        update = { paged -> _state.update { it.copy(albums = paged) } },
        fetch = { _, offset -> api.followedAlbums(PAGE_SIZE, offset, fresh = fresh) },
    )

    private fun loadArtists(reset: Boolean, fresh: Boolean = false) = loadPage(
        reset = reset,
        current = { _state.value.artists },
        update = { paged -> _state.update { it.copy(artists = paged) } },
        fetch = { _, offset -> api.followedArtists(PAGE_SIZE, offset, fresh = fresh) },
    )

    private fun <T> loadPage(
        reset: Boolean,
        current: () -> PagedList<T>,
        update: (PagedList<T>) -> Unit,
        fetch: suspend (uid: Long, offset: Int) -> ApiResult<Page<T>>,
    ) {
        val uid = userId
        if (uid == null) {
            update(PagedList(state = SectionState.Failed(LoadError.Server)))
            return
        }

        val existing = current()
        if (!reset && (existing.appending || !existing.hasMore)) return

        val offset = if (reset) 0 else existing.items.size
        update(
            if (reset) {
                PagedList(state = SectionState.Loading)
            } else {
                existing.copy(appending = true)
            },
        )

        viewModelScope.launch {
            when (val result = fetch(uid, offset)) {
                is ApiResult.Success -> update(
                    PagedList(
                        items = if (reset) result.value.items else existing.items + result.value.items,
                        state = SectionState.Content(Unit),
                        hasMore = result.value.hasMore,
                    ),
                )

                is ApiResult.Unauthorized -> update(
                    PagedList(items = existing.items, state = SectionState.Failed(LoadError.Server)),
                )

                is ApiResult.NetworkError -> update(
                    PagedList(items = existing.items, state = SectionState.Failed(LoadError.Network)),
                )

                is ApiResult.HttpError -> update(
                    PagedList(items = existing.items, state = SectionState.Failed(LoadError.Server)),
                )
            }
        }
    }

    private fun loadRecent() {
        val uid = userId
        if (uid == null) {
            _state.update { it.copy(recent = SectionState.Failed(LoadError.Server)) }
            return
        }
        viewModelScope.launch {
            val result = api.recentlyPlayed(uid, RECENT_SIZE)
            _state.update {
                it.copy(
                    recent = when (result) {
                        is ApiResult.Success -> SectionState.Content(result.value)
                        is ApiResult.NetworkError -> SectionState.Failed(LoadError.Network)
                        is ApiResult.HttpError, is ApiResult.Unauthorized ->
                            SectionState.Failed(LoadError.Server)
                    },
                )
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val RECENT_SIZE = 50
    }
}
