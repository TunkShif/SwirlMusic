package one.tunkshif.swirlmusic.ui.placeholder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmApi

sealed interface ApiCheckState {
    data object Idle : ApiCheckState
    data object Loading : ApiCheckState
    data class Reachable(val code: Int) : ApiCheckState
    data class Failed(val reason: String) : ApiCheckState
}

/**
 * Proves the Phase 0 wiring end to end: Hilt builds the view model, the network layer reaches the
 * deployment, and the result reaches Compose as state.
 *
 * Deleted in Phase 1 when the real navigation shell replaces this screen.
 */
@HiltViewModel
class PlaceholderViewModel @Inject constructor(
    private val api: NcmApi,
) : ViewModel() {

    private val _state = MutableStateFlow<ApiCheckState>(ApiCheckState.Idle)
    val state: StateFlow<ApiCheckState> = _state.asStateFlow()

    fun checkApi() {
        if (_state.value == ApiCheckState.Loading) return
        _state.value = ApiCheckState.Loading
        viewModelScope.launch {
            _state.value = when (val result = api.loginStatus()) {
                is ApiResult.Success -> ApiCheckState.Reachable(result.value.code)
                is ApiResult.Unauthorized -> ApiCheckState.Failed("unauthorized")
                is ApiResult.HttpError -> ApiCheckState.Failed("HTTP ${result.code}")
                is ApiResult.NetworkError -> ApiCheckState.Failed(result.cause.message ?: "network error")
            }
        }
    }
}
