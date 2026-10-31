package one.tunkshif.swirlmusic.ui.login

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmAuthApi
import one.tunkshif.swirlmusic.data.api.SystemTimeSource
import one.tunkshif.swirlmusic.data.api.dto.LogoutResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCheckResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCreateBody
import one.tunkshif.swirlmusic.data.api.dto.QrKeyBody
import one.tunkshif.swirlmusic.data.api.dto.UserAccountResponse
import one.tunkshif.swirlmusic.data.auth.LoginTuning
import one.tunkshif.swirlmusic.data.auth.QrLoginEngine
import one.tunkshif.swirlmusic.data.media.QrImageExporter
import one.tunkshif.swirlmusic.data.session.SessionCache
import one.tunkshif.swirlmusic.data.session.SessionCipher
import one.tunkshif.swirlmusic.data.session.SessionRepository
import one.tunkshif.swirlmusic.data.session.SessionState
import one.tunkshif.swirlmusic.data.session.SessionStorage
import one.tunkshif.swirlmusic.data.session.SessionStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Mapping between engine events and screen phases. Robolectric is only here because the exporter
 * needs a Context; no UI is rendered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LoginViewModelTest {

    private val api = FakeAuthApi()
    private val storage = FakeStorage()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Share the test scheduler with `Dispatchers.Main`, so virtual time drives the view model. */
    private fun TestScope.useTestMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    }

    @Test
    fun `a successful scan stores the session`() = runTest {
        useTestMain()
        api.checks += listOf(
            ApiResult.Success(QrCheckResponse(QrCheckResponse.WAITING)),
            ApiResult.Success(QrCheckResponse(QrCheckResponse.AUTHORIZED, cookie = "MUSIC_U=abc")),
        )
        val viewModel = viewModel()

        viewModel.start()
        advanceUntilIdle()

        assertThat(storage.value).isNotNull()
        assertThat(session.state.value).isInstanceOf(SessionState.SignedIn::class.java)
    }

    @Test
    fun `the screen starts on the welcome copy`() {
        val viewModel = viewModel()

        assertThat(viewModel.state.value.phase).isEqualTo(LoginPhase.Idle)
    }

    @Test
    fun `entering the screen after a completed login resets to welcome`() = runTest {
        useTestMain()
        api.checks += ApiResult.Success(
            QrCheckResponse(QrCheckResponse.AUTHORIZED, cookie = "MUSIC_U=abc"),
        )
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()
        assertThat(viewModel.state.value.phase).isEqualTo(LoginPhase.Completing)

        viewModel.onEnter()

        assertThat(viewModel.state.value).isEqualTo(LoginUiState())
    }

    @Test
    fun `a network failure moves to the failed phase`() = runTest {
        useTestMain()
        api.checks.clear()
        api.defaultCheck = ApiResult.NetworkError(IOException("no route"))
        val viewModel = viewModel()

        viewModel.start()
        advanceUntilIdle()

        assertThat(viewModel.state.value.phase).isEqualTo(LoginPhase.Failed)
    }

    private lateinit var session: SessionRepository

    private fun viewModel(): LoginViewModel {
        session = SessionRepository(api, SessionStore(storage, PlainCipher()), SessionCache())
        val engine = QrLoginEngine(
            api,
            LoginTuning(pollIntervalMillis = 1, timeoutMillis = 3, maxNetworkRetries = 1),
        )
        val exporter = QrImageExporter(
            ApplicationProvider.getApplicationContext(),
            SystemTimeSource,
            Dispatchers.Unconfined,
        )
        return LoginViewModel(engine, session, exporter)
    }

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
        val checks = ArrayDeque<ApiResult<QrCheckResponse>>()
        var defaultCheck: ApiResult<QrCheckResponse> =
            ApiResult.Success(QrCheckResponse(QrCheckResponse.WAITING))

        override suspend fun qrKey(): ApiResult<QrKeyBody> =
            ApiResult.Success(QrKeyBody(unikey = "key-1"))

        override suspend fun qrCreate(key: String): ApiResult<QrCreateBody> =
            ApiResult.Success(QrCreateBody(qrurl = "https://music.163.com/login?codekey=key-1"))

        override suspend fun qrCheck(key: String): ApiResult<QrCheckResponse> =
            if (checks.isEmpty()) defaultCheck else checks.removeFirst()

        override suspend fun userAccount(): ApiResult<UserAccountResponse> =
            ApiResult.Success(UserAccountResponse(code = 200))

        override suspend fun logout(): ApiResult<LogoutResponse> =
            ApiResult.Success(LogoutResponse(code = 200))
    }
}
