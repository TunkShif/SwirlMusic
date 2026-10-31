package one.tunkshif.swirlmusic.ui.login

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
import one.tunkshif.swirlmusic.data.auth.LoginError
import one.tunkshif.swirlmusic.data.auth.QrLoginEngine
import one.tunkshif.swirlmusic.data.auth.QrLoginEvent
import one.tunkshif.swirlmusic.data.media.QrImageExporter
import one.tunkshif.swirlmusic.data.session.SessionRepository

enum class LoginPhase {
    /** Welcome copy, no code requested yet. */
    Idle,
    Loading,
    Waiting,
    Scanned,
    Expired,
    Failed,
    /** Cookie received, session is being stored. */
    Completing,
}

data class LoginUiState(
    val phase: LoginPhase = LoginPhase.Idle,
    val qrUrl: String? = null,
    val error: LoginError? = null,
)

sealed interface LoginUiEvent {
    data class QrSaved(val path: String) : LoginUiEvent
    data object QrSaveFailed : LoginUiEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val engine: QrLoginEngine,
    private val session: SessionRepository,
    private val qrExporter: QrImageExporter,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<LoginUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loginJob: Job? = null

    fun start() {
        if (loginJob?.isActive == true) return
        loginJob = viewModelScope.launch {
            engine.login().collect { event ->
                when (event) {
                    QrLoginEvent.LoadingQr ->
                        _state.value = LoginUiState(phase = LoginPhase.Loading)

                    is QrLoginEvent.QrVisible ->
                        _state.value = LoginUiState(phase = LoginPhase.Waiting, qrUrl = event.qrUrl)

                    QrLoginEvent.Scanned ->
                        _state.value = _state.value.copy(phase = LoginPhase.Scanned)

                    QrLoginEvent.Expired ->
                        _state.value = _state.value.copy(phase = LoginPhase.Expired)

                    is QrLoginEvent.Success -> {
                        _state.value = _state.value.copy(phase = LoginPhase.Completing)
                        // The root switches away as soon as the session state changes.
                        session.signIn(event.cookie)
                    }

                    is QrLoginEvent.Failure ->
                        _state.value = _state.value.copy(
                            phase = LoginPhase.Failed,
                            error = event.error,
                        )
                }
            }
        }
    }

    /**
     * Called when the screen appears. After a successful sign-in the root swaps to the shell, but
     * this view model is activity-scoped and survives; without this, signing out again would show
     * the "completing" spinner forever.
     */
    fun onEnter() {
        if (_state.value.phase == LoginPhase.Completing) {
            loginJob?.cancel()
            loginJob = null
            _state.value = LoginUiState()
        }
    }

    /** Back to the welcome copy, cancelling any poll in flight. */
    fun cancel() {
        loginJob?.cancel()
        loginJob = null
        _state.value = LoginUiState()
    }

    fun saveQrToGallery() {
        val url = _state.value.qrUrl ?: return
        viewModelScope.launch {
            qrExporter.save(url).fold(
                onSuccess = { path -> _events.send(LoginUiEvent.QrSaved(path)) },
                onFailure = { _events.send(LoginUiEvent.QrSaveFailed) },
            )
        }
    }

    override fun onCleared() {
        loginJob?.cancel()
    }
}
