package one.tunkshif.swirlmusic.data.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import one.tunkshif.swirlmusic.data.cache.AccountScopedCache
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmAuthApi
import one.tunkshif.swirlmusic.data.api.dto.ProfileDto
import one.tunkshif.swirlmusic.data.model.UserProfile

sealed interface SessionState {
    /** Not read from storage yet. The app shows a splash in this state. */
    data object Unknown : SessionState

    data object SignedOut : SessionState

    /** Signed in. [profile] is null when the profile could not be fetched, e.g. offline. */
    data class SignedIn(val profile: UserProfile?) : SessionState
}

/**
 * Owns the session: loads it at startup, hands the cookie to [SessionCache] for the interceptor,
 * verifies it against the API, and clears it on sign-out.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val api: NcmAuthApi,
    private val store: SessionStore,
    private val cache: SessionCache,
    private val accountCache: AccountScopedCache,
) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /**
     * Reads the stored cookie and verifies it.
     *
     * A stored session is only dropped when the server says it is gone. A network failure keeps
     * it: starting the app on a train must not sign the user out.
     */
    suspend fun restore() {
        val session = store.load()
        if (session == null) {
            cache.update(null)
            _state.value = SessionState.SignedOut
            return
        }

        cache.update(session.cookie)

        when (val result = api.userAccount()) {
            is ApiResult.Success -> {
                val profile = result.value.profile?.toDomain()
                if (result.value.account == null && profile == null) {
                    // The cookie is accepted but no account is attached: effectively signed out.
                    clearSession()
                } else {
                    _state.value = SessionState.SignedIn(profile)
                }
            }

            is ApiResult.Unauthorized -> clearSession()

            // Server trouble or no network: keep the session and try again later.
            is ApiResult.HttpError, is ApiResult.NetworkError ->
                _state.value = SessionState.SignedIn(profile = null)
        }
    }

    suspend fun signIn(cookie: String) {
        store.save(Session(cookie))
        cache.update(cookie)
        val profile = (api.userAccount() as? ApiResult.Success)?.value?.profile?.toDomain()
        _state.value = SessionState.SignedIn(profile)
    }

    /**
     * Drops the session. Best effort on the server: a failed logout still signs the app out.
     */
    suspend fun signOut() {
        runCatching { api.logout() }
        clearSession()
    }

    private suspend fun clearSession() {
        store.clear()
        accountCache.clear()
        cache.update(null)
        _state.value = SessionState.SignedOut
    }
}

private fun ProfileDto.toDomain(): UserProfile = UserProfile(
    userId = userId,
    nickname = nickname,
    avatarUrl = avatarUrl,
    vipType = vipType,
)
