package one.tunkshif.swirlmusic.data.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionState {
    /** Not read from storage yet. */
    data object Unknown : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val session: Session) : SessionState
}

/**
 * Owns the session: loads it at startup, hands it to [SessionCache] for the interceptor,
 * and clears it on sign-out.
 *
 * Profile data and QR login arrive in Phase 2.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val store: SessionStore,
    private val cache: SessionCache,
) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    suspend fun restore() {
        val session = store.load()
        cache.update(session?.cookie)
        _state.value = if (session == null) SessionState.SignedOut else SessionState.SignedIn(session)
    }

    suspend fun signIn(cookie: String) {
        val session = Session(cookie)
        store.save(session)
        cache.update(session.cookie)
        _state.value = SessionState.SignedIn(session)
    }

    suspend fun signOut() {
        store.clear()
        cache.update(null)
        _state.value = SessionState.SignedOut
    }
}
