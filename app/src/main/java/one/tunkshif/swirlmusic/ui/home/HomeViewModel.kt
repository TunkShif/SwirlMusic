package one.tunkshif.swirlmusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmHomeApi
import one.tunkshif.swirlmusic.data.model.ChartSummary
import one.tunkshif.swirlmusic.data.model.PlaylistSummary
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.data.session.SessionRepository

enum class LoadError { Network, Server }

/**
 * One section, loaded on its own. A failing section must not blank the whole screen.
 */
sealed interface SectionState<out T> {
    data object Loading : SectionState<Nothing>

    data class Content<T>(val value: T) : SectionState<T>

    data class Failed(val error: LoadError) : SectionState<Nothing>
}

data class HomeUiState(
    val isRefreshing: Boolean = false,
    val daily: SectionState<List<Track>> = SectionState.Loading,
    val playlists: SectionState<List<PlaylistSummary>> = SectionState.Loading,
    val charts: SectionState<List<ChartSummary>> = SectionState.Loading,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: NcmHomeApi,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(refreshing = false)
    }

    fun refresh() = load(refreshing = true)

    fun retry() = load(refreshing = false)

    /**
     * Loads the three sections in parallel. The previous content stays visible while a refresh
     * runs, so pull-to-refresh does not flash the skeletons.
     */
    private fun load(refreshing: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (refreshing) _state.update { it.copy(isRefreshing = true) }

            val results = coroutineScope {
                val daily = async { api.dailySongs() }
                val playlists = async { api.recommendedPlaylists() }
                val charts = async { api.charts() }
                Triple(daily.await(), playlists.await(), charts.await())
            }
            val (daily, playlists, charts) = results

            // A dead session is one state, not three errors: sign out and let the root react.
            if (listOf(daily, playlists, charts).any { it is ApiResult.Unauthorized }) {
                session.signOut()
                return@launch
            }

            _state.value = HomeUiState(
                isRefreshing = false,
                daily = daily.toSection(),
                playlists = playlists.toSection(),
                charts = charts.toSection(),
            )
        }
    }
}

private fun <T> ApiResult<T>.toSection(): SectionState<T> = when (this) {
    is ApiResult.Success -> SectionState.Content(value)
    is ApiResult.NetworkError -> SectionState.Failed(LoadError.Network)
    is ApiResult.HttpError, is ApiResult.Unauthorized -> SectionState.Failed(LoadError.Server)
}
