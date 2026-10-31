package one.tunkshif.swirlmusic.ui.home

import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmAuthApi
import one.tunkshif.swirlmusic.data.api.NcmHomeApi
import one.tunkshif.swirlmusic.data.api.dto.LogoutResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCheckResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCreateBody
import one.tunkshif.swirlmusic.data.api.dto.QrKeyBody
import one.tunkshif.swirlmusic.data.api.dto.UserAccountResponse
import one.tunkshif.swirlmusic.data.model.ChartSummary
import one.tunkshif.swirlmusic.data.model.PlaylistSummary
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.data.session.SessionCache
import one.tunkshif.swirlmusic.data.session.SessionCipher
import one.tunkshif.swirlmusic.data.session.SessionRepository
import one.tunkshif.swirlmusic.data.session.SessionState
import one.tunkshif.swirlmusic.data.session.SessionStorage
import one.tunkshif.swirlmusic.data.session.SessionStore
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val api = FakeHomeApi()
    private val storage = FakeStorage()
    private val session = SessionRepository(
        FakeAuthApi(),
        SessionStore(storage, PlainCipher()),
        SessionCache(),
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.useTestMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @Test
    fun `all three sections load in parallel`() = runTest {
        useTestMain()
        val viewModel = HomeViewModel(api, session)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.daily).isInstanceOf(SectionState.Content::class.java)
        assertThat(state.playlists).isInstanceOf(SectionState.Content::class.java)
        assertThat(state.charts).isInstanceOf(SectionState.Content::class.java)
        assertThat((state.daily as SectionState.Content).value).hasSize(2)
        assertThat(api.dailyCalls).isEqualTo(1)
    }

    @Test
    fun `a failing section leaves the others readable`() = runTest {
        useTestMain()
        api.dailyResult = ApiResult.NetworkError(IOException("offline"))
        val viewModel = HomeViewModel(api, session)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.daily).isEqualTo(SectionState.Failed(LoadError.Network))
        assertThat(state.playlists).isInstanceOf(SectionState.Content::class.java)
    }

    @Test
    fun `a server error is told apart from a network error`() = runTest {
        useTestMain()
        api.chartsResult = ApiResult.HttpError(503, "busy")
        val viewModel = HomeViewModel(api, session)

        advanceUntilIdle()

        assertThat(viewModel.state.value.charts).isEqualTo(SectionState.Failed(LoadError.Server))
    }

    @Test
    fun `an expired session signs out instead of showing three errors`() = runTest {
        useTestMain()
        storage.value = PlainCipher().encrypt("MUSIC_U=stale")
        api.playlistsResult = ApiResult.Unauthorized("{\"code\":301}")
        api.dailyResult = ApiResult.Unauthorized("{\"code\":301}")
        api.chartsResult = ApiResult.Unauthorized("{\"code\":301}")

        val viewModel = HomeViewModel(api, session)
        advanceUntilIdle()

        assertThat(session.state.value).isEqualTo(SessionState.SignedOut)
        assertThat(viewModel.state.value.daily).isEqualTo(SectionState.Loading)
    }

    @Test
    fun `refresh flags the pull to refresh indicator and clears it`() = runTest {
        useTestMain()
        val viewModel = HomeViewModel(api, session)
        advanceUntilIdle()

        // Hold the next load open so the refreshing flag can be observed.
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        viewModel.refresh()
        runCurrent()
        assertThat(viewModel.state.value.isRefreshing).isTrue()

        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isRefreshing).isFalse()
        assertThat(api.dailyCalls).isEqualTo(2)
    }

    @Test
    fun `retry reloads after a failure`() = runTest {
        useTestMain()
        api.dailyResult = ApiResult.NetworkError(IOException("offline"))
        val viewModel = HomeViewModel(api, session)
        advanceUntilIdle()

        api.dailyResult = ApiResult.Success(listOf(sampleTrack))
        viewModel.retry()
        advanceUntilIdle()

        assertThat(viewModel.state.value.daily).isInstanceOf(SectionState.Content::class.java)
    }

    private class FakeHomeApi : NcmHomeApi {
        var dailyResult: ApiResult<List<Track>> = ApiResult.Success(listOf(sampleTrack, secondTrack))
        var playlistsResult: ApiResult<List<PlaylistSummary>> = ApiResult.Success(emptyList())
        var chartsResult: ApiResult<List<ChartSummary>> = ApiResult.Success(emptyList())
        var dailyCalls = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun dailySongs(): ApiResult<List<Track>> {
            dailyCalls += 1
            gate?.await()
            return dailyResult
        }

        override suspend fun recommendedPlaylists(): ApiResult<List<PlaylistSummary>> =
            playlistsResult

        override suspend fun charts(): ApiResult<List<ChartSummary>> = chartsResult
    }

    private class FakeAuthApi : NcmAuthApi {
        override suspend fun qrKey(): ApiResult<QrKeyBody> = ApiResult.Success(QrKeyBody())
        override suspend fun qrCreate(key: String): ApiResult<QrCreateBody> =
            ApiResult.Success(QrCreateBody())
        override suspend fun qrCheck(key: String): ApiResult<QrCheckResponse> =
            ApiResult.Success(QrCheckResponse())
        override suspend fun userAccount(): ApiResult<UserAccountResponse> =
            ApiResult.Success(UserAccountResponse())
        override suspend fun logout(): ApiResult<LogoutResponse> =
            ApiResult.Success(LogoutResponse())
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

    private companion object {
        val sampleTrack = Track(1, "Nightcall", "Kavinsky", null, 258_000)
        val secondTrack = Track(2, "Midnight City", "M83", null, 244_000)
    }
}
