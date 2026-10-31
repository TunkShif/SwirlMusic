package one.tunkshif.swirlmusic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmLibraryApi
import one.tunkshif.swirlmusic.data.local.SearchHistory
import one.tunkshif.swirlmusic.data.model.SearchResults
import one.tunkshif.swirlmusic.data.model.SearchType
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState

data class SearchUiState(
    val query: String = "",
    val type: SearchType = SearchType.Songs,
    /** Empty content with no query is the prompt state, not an empty result. */
    val results: SectionState<SearchResults> = SectionState.Content(SearchResults()),
    val searching: Boolean = false,
    val history: List<String> = emptyList(),
    val submitted: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: NcmLibraryApi,
    private val history: SearchHistory,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch { _state.value = _state.value.copy(history = history.recent()) }
    }

    fun onQueryChange(text: String) {
        _state.value = _state.value.copy(query = text)
    }

    fun selectType(type: SearchType) {
        _state.value = _state.value.copy(type = type)
        if (_state.value.query.isNotBlank()) search()
    }

    fun selectHistory(query: String) {
        _state.value = _state.value.copy(query = query)
        search()
    }

    fun submit() {
        if (_state.value.query.isBlank()) return
        search()
    }

    fun clearHistory() {
        viewModelScope.launch {
            history.clear()
            _state.value = _state.value.copy(history = emptyList())
        }
    }

    private fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return

        searchJob?.cancel()
        _state.value = _state.value.copy(searching = true, submitted = true)

        searchJob = viewModelScope.launch {
            val result = api.search(query, _state.value.type, LIMIT, 0)
            _state.value = _state.value.copy(
                searching = false,
                results = when (result) {
                    is ApiResult.Success -> SectionState.Content(result.value)
                    is ApiResult.NetworkError -> SectionState.Failed(LoadError.Network)
                    is ApiResult.HttpError, is ApiResult.Unauthorized ->
                        SectionState.Failed(LoadError.Server)
                },
            )
            history.add(query)
            _state.value = _state.value.copy(history = history.recent())
        }
    }

    private companion object {
        const val LIMIT = 30
    }
}
