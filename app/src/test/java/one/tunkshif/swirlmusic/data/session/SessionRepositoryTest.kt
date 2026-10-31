package one.tunkshif.swirlmusic.data.session

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmAuthApi
import one.tunkshif.swirlmusic.data.api.dto.AccountDto
import one.tunkshif.swirlmusic.data.api.dto.LogoutResponse
import one.tunkshif.swirlmusic.data.api.dto.ProfileDto
import one.tunkshif.swirlmusic.data.api.dto.QrCheckResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCreateBody
import one.tunkshif.swirlmusic.data.api.dto.QrKeyBody
import one.tunkshif.swirlmusic.data.api.dto.UserAccountResponse
import org.junit.Test

class SessionRepositoryTest {

    private val storage = FakeStorage()
    private val cache = SessionCache()
    private val api = FakeAuthApi()
    private val repository = SessionRepository(api, SessionStore(storage, PlainCipher()), cache)

    @Test
    fun `with nothing stored the app is signed out`() = runTest {
        repository.restore()

        assertThat(repository.state.value).isEqualTo(SessionState.SignedOut)
        assertThat(cache.cookie).isNull()
    }

    @Test
    fun `a stored cookie is verified and yields the profile`() = runTest {
        storage.value = PlainCipher().encrypt("MUSIC_U=abc")
        api.account = signedIn("tunkshif")

        repository.restore()

        val state = repository.state.value as SessionState.SignedIn
        assertThat(state.profile?.nickname).isEqualTo("tunkshif")
        assertThat(cache.cookie).isEqualTo("MUSIC_U=abc")
    }

    @Test
    fun `a cookie the server does not recognise is dropped`() = runTest {
        storage.value = PlainCipher().encrypt("MUSIC_U=stale")
        api.account = ApiResult.Success(UserAccountResponse(code = 200))

        repository.restore()

        assertThat(repository.state.value).isEqualTo(SessionState.SignedOut)
        assertThat(storage.value).isNull()
        assertThat(cache.cookie).isNull()
    }

    @Test
    fun `an explicit unauthorized response drops the session`() = runTest {
        storage.value = PlainCipher().encrypt("MUSIC_U=stale")
        api.account = ApiResult.Unauthorized("{\"code\":301}")

        repository.restore()

        assertThat(repository.state.value).isEqualTo(SessionState.SignedOut)
        assertThat(storage.value).isNull()
    }

    @Test
    fun `starting offline keeps the session`() = runTest {
        storage.value = PlainCipher().encrypt("MUSIC_U=abc")
        api.account = ApiResult.NetworkError(IOException("offline"))

        repository.restore()

        val state = repository.state.value as SessionState.SignedIn
        assertThat(state.profile).isNull()
        assertThat(cache.cookie).isEqualTo("MUSIC_U=abc")
    }

    @Test
    fun `signing in stores the cookie and fetches the profile`() = runTest {
        api.account = signedIn("newcomer")

        repository.signIn("MUSIC_U=new")

        assertThat(storage.value).isNotNull()
        assertThat(cache.cookie).isEqualTo("MUSIC_U=new")
        assertThat((repository.state.value as SessionState.SignedIn).profile?.nickname)
            .isEqualTo("newcomer")
    }

    @Test
    fun `signing out clears storage, the cache, and calls the server`() = runTest {
        repository.signIn("MUSIC_U=new")

        repository.signOut()

        assertThat(repository.state.value).isEqualTo(SessionState.SignedOut)
        assertThat(storage.value).isNull()
        assertThat(cache.cookie).isNull()
        assertThat(api.logoutCalls).isEqualTo(1)
    }

    @Test
    fun `a failing logout still signs the app out`() = runTest {
        repository.signIn("MUSIC_U=new")
        api.logoutFailure = IOException("server gone")

        repository.signOut()

        assertThat(repository.state.value).isEqualTo(SessionState.SignedOut)
        assertThat(cache.cookie).isNull()
    }

    private fun signedIn(nickname: String) = ApiResult.Success(
        UserAccountResponse(
            code = 200,
            account = AccountDto(id = 7),
            profile = ProfileDto(userId = 7, nickname = nickname),
        ),
    )

    private class FakeStorage(var value: String? = null) : SessionStorage {
        override suspend fun read(): String? = value
        override suspend fun write(value: String?) {
            this.value = value
        }
    }

    private class PlainCipher : SessionCipher {
        override fun encrypt(plaintext: String): String = "plain:$plaintext"
        override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("plain:")
    }

    private class FakeAuthApi : NcmAuthApi {
        var account: ApiResult<UserAccountResponse> =
            ApiResult.Success(UserAccountResponse(code = 200))
        var logoutFailure: Throwable? = null
        var logoutCalls = 0

        override suspend fun qrKey(): ApiResult<QrKeyBody> = ApiResult.Success(QrKeyBody())
        override suspend fun qrCreate(key: String): ApiResult<QrCreateBody> =
            ApiResult.Success(QrCreateBody())
        override suspend fun qrCheck(key: String): ApiResult<QrCheckResponse> =
            ApiResult.Success(QrCheckResponse())

        override suspend fun userAccount(): ApiResult<UserAccountResponse> = account

        override suspend fun logout(): ApiResult<LogoutResponse> {
            logoutCalls += 1
            logoutFailure?.let { return ApiResult.NetworkError(it) }
            return ApiResult.Success(LogoutResponse(code = 200))
        }
    }
}
