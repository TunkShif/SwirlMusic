package one.tunkshif.swirlmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.session.SessionRepository
import one.tunkshif.swirlmusic.data.session.SessionState

/**
 * Owns the one decision the whole app depends on: is there a session.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val session: SessionRepository,
) : ViewModel() {

    val state = session.state

    init {
        viewModelScope.launch { session.restore() }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    /** For Phase 3: a data call that returns [one.tunkshif.swirlmusic.data.api.ApiResult.Unauthorized]. */
    internal fun onUnauthorized() {
        viewModelScope.launch { session.signOut() }
    }
}

/** Re-exported so composables can branch on the sealed state without importing the data layer. */
typealias AppSessionState = SessionState
