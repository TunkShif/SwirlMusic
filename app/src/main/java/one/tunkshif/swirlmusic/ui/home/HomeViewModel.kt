package one.tunkshif.swirlmusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmHomeApi
import one.tunkshif.swirlmusic.data.api.NcmLibraryApi
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.library.RecentAlbumsRepository
import one.tunkshif.swirlmusic.data.library.TrackListRepository
import one.tunkshif.swirlmusic.data.model.RecentAlbum
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.data.session.SessionRepository
import one.tunkshif.swirlmusic.data.session.SessionState
import one.tunkshif.swirlmusic.player.PlayerController
import one.tunkshif.swirlmusic.player.idTrackPager
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState

data class HomeUiState(
    val isRefreshing: Boolean = false,
    val daily: SectionState<List<Track>> = SectionState.Loading,
    val recent: SectionState<List<RecentAlbum>> = SectionState.Loading,
    /** The liked-song ids; "Feeling lucky" stays hidden while this is empty. */
    val likedIds: List<Long> = emptyList(),
    /** First lucky window is loading: the button shows a spinner and ignores taps. */
    val feelingLucky: Boolean = false,
)

/** One-shot UI events surfaced as a ToastPill. */
sealed interface HomeMessage {
    data object LuckyFailed : HomeMessage
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: NcmHomeApi,
    private val libraryApi: NcmLibraryApi,
    private val recentAlbums: RecentAlbumsRepository,
    private val tracks: TrackListRepository,
    private val session: SessionRepository,
    private val timeSource: TimeSource,
    private val random: Random,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<HomeMessage>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private val userId: Long? =
        (session.state.value as? SessionState.SignedIn)?.profile?.userId

    /** Last successful recent-albums fetch; drives the stale-on-show refresh. */
    private var recentLoadedAt = 0L
    private var loadJob: Job? = null

    init {
        load(refreshing = false)
    }

    fun refresh() = load(refreshing = true)

    fun retry() = load(refreshing = false)

    /**
     * Home became visible again. Refresh the recent row only — silently, keeping the shown
     * list — when the last successful fetch is older than [RECENT_STALE_AFTER_MS].
     */
    fun onShown() {
        if (recentLoadedAt == 0L) return
        if (timeSource.nowMillis() - recentLoadedAt < RECENT_STALE_AFTER_MS) return
        viewModelScope.launch { fetchRecent() }
    }

    /**
     * Shuffle the liked-song ids, resolve the first page through the cache-first list
     * repository, then hand playback a pager for the remaining shuffled ids. A failure or an
     * empty result surfaces one transient message; taps are ignored while a run is in flight.
     */
    fun feelingLucky(
        onReady: (List<Track>, Int, PlayerController.TrackPager?) -> Unit,
    ) {
        if (_state.value.feelingLucky) return
        val uid = userId ?: return
        _state.update { it.copy(feelingLucky = true) }
        viewModelScope.launch {
            try {
                when (val result = libraryApi.likedIds(uid)) {
                    is ApiResult.Success -> startLucky(result.value, onReady)
                    is ApiResult.Unauthorized -> signOut()
                    is ApiResult.HttpError,
                    is ApiResult.NetworkError -> _messages.emit(HomeMessage.LuckyFailed)
                }
            } finally {
                _state.update { it.copy(feelingLucky = false) }
            }
        }
    }

    private suspend fun startLucky(
        ids: List<Long>,
        onReady: (List<Track>, Int, PlayerController.TrackPager?) -> Unit,
    ) {
        if (ids.isEmpty()) {
            _state.update { it.copy(likedIds = emptyList()) }
            return
        }
        val shuffled = ids.shuffled(random)
        when (val first = tracks.tracksByIds(shuffled.take(LUCKY_PAGE_SIZE))) {
            is ApiResult.Success -> {
                val rest = shuffled.drop(LUCKY_PAGE_SIZE)
                val pager = if (rest.isEmpty()) null
                else idTrackPager(rest, LUCKY_PAGE_SIZE, tracks::tracksByIds)
                onReady(first.value, 0, pager)
            }
            is ApiResult.Unauthorized -> signOut()
            is ApiResult.HttpError,
            is ApiResult.NetworkError -> _messages.emit(HomeMessage.LuckyFailed)
        }
    }

    /**
     * Loads the sections in parallel. The previous content stays visible while a refresh runs,
     * so pull-to-refresh does not flash the skeletons.
     */
    private fun load(refreshing: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (refreshing) _state.update { it.copy(isRefreshing = true) }
            try {
                coroutineScope {
                    launch { fetchDaily() }
                    launch { fetchRecent() }
                    launch { fetchLiked() }
                }
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun fetchDaily() {
        when (val result = api.dailySongs()) {
            is ApiResult.Success ->
                _state.update { it.copy(daily = SectionState.Content(result.value)) }
            is ApiResult.Unauthorized -> signOut()
            is ApiResult.HttpError, is ApiResult.NetworkError ->
                _state.update { it.copy(daily = result.toSection()) }
        }
    }

    /** Stale-while-revalidate: a refresh failure keeps the row it would have replaced. */
    private suspend fun fetchRecent() {
        if (_state.value.recent !is SectionState.Content) {
            val cached = recentAlbums.cached()
            if (cached.isNotEmpty()) {
                _state.update { it.copy(recent = SectionState.Content(cached.take(RECENT_ALBUM_LIMIT))) }
            }
        }
        when (val result = recentAlbums.refresh(RECENT_ALBUM_LIMIT)) {
            is ApiResult.Success -> {
                recentLoadedAt = timeSource.nowMillis()
                _state.update { it.copy(recent = SectionState.Content(result.value)) }
            }
            is ApiResult.Unauthorized -> signOut()
            is ApiResult.HttpError, is ApiResult.NetworkError -> {
                if (_state.value.recent !is SectionState.Content) {
                    _state.update { it.copy(recent = result.toSection()) }
                }
            }
        }
    }

    /** Feeds "Feeling lucky" visibility only; a failure keeps the button as it was. */
    private suspend fun fetchLiked() {
        val uid = userId ?: return
        when (val result = libraryApi.likedIds(uid)) {
            is ApiResult.Success -> _state.update { it.copy(likedIds = result.value) }
            is ApiResult.Unauthorized -> signOut()
            is ApiResult.HttpError, is ApiResult.NetworkError -> Unit
        }
    }

    private suspend fun signOut() {
        session.signOut()
        _state.update { HomeUiState() }
    }

    private companion object {
        /** The row shows at most ten; the server sorts newest first. */
        const val RECENT_ALBUM_LIMIT = 10
        /** First lucky window; same chunk the liked-list play-all uses. */
        const val LUCKY_PAGE_SIZE = TrackListRepository.PAGE_SIZE
        const val RECENT_STALE_AFTER_MS = 30_000L
    }
}

private fun <T> ApiResult<T>.toSection(): SectionState<T> = when (this) {
    is ApiResult.Success -> SectionState.Content(value)
    is ApiResult.NetworkError -> SectionState.Failed(LoadError.Network)
    is ApiResult.HttpError, is ApiResult.Unauthorized -> SectionState.Failed(LoadError.Server)
}
