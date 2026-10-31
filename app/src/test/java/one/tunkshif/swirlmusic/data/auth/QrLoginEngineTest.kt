package one.tunkshif.swirlmusic.data.auth

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.flow.toList
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

/**
 * Runs in virtual time: a three-minute login window costs nothing.
 */
class QrLoginEngineTest {

    private val api = FakeAuthApi()
    private val tuning = LoginTuning(
        pollIntervalMillis = 1_000,
        timeoutMillis = 5_000,
        maxNetworkRetries = 3,
    )
    private val engine = QrLoginEngine(api, tuning)

    @Test
    fun `scan then confirm yields the cookie`() = runTest {
        api.checks += listOf(
            waiting(),
            scanned(),
            authorized("MUSIC_U=abc; __csrf=def"),
        )

        val events = engine.login().toList()

        assertThat(events).containsExactly(
            QrLoginEvent.LoadingQr,
            QrLoginEvent.QrVisible(QR_URL),
            QrLoginEvent.Scanned,
            QrLoginEvent.Success("MUSIC_U=abc; __csrf=def"),
        ).inOrder()
    }

    @Test
    fun `a repeated scanned status is announced once`() = runTest {
        api.checks += listOf(scanned(), scanned(), authorized("MUSIC_U=abc"))

        val events = engine.login().toList()

        assertThat(events.count { it == QrLoginEvent.Scanned }).isEqualTo(1)
    }

    @Test
    fun `an expired code stops the flow`() = runTest {
        api.checks += listOf(waiting(), success(QrCheckResponse(QrCheckResponse.EXPIRED)))

        val events = engine.login().toList()

        assertThat(events.last()).isEqualTo(QrLoginEvent.Expired)
    }

    @Test
    fun `our own timeout is reported as an expiry`() = runTest {
        api.checks.clear()
        api.defaultCheck = waiting()

        val events = engine.login().toList()

        assertThat(events.last()).isEqualTo(QrLoginEvent.Expired)
        // Five one-second polls before the five-second window closes.
        assertThat(api.checkCalls).isEqualTo(5)
    }

    @Test
    fun `a short network outage is tolerated`() = runTest {
        api.checks += listOf(networkError(), networkError(), authorized("MUSIC_U=abc"))

        val events = engine.login().toList()

        assertThat(events.last()).isEqualTo(QrLoginEvent.Success("MUSIC_U=abc"))
    }

    @Test
    fun `too many network failures stop the flow`() = runTest {
        api.checks.clear()
        api.defaultCheck = networkError()

        val events = engine.login().toList()

        assertThat(events.last()).isEqualTo(QrLoginEvent.Failure(LoginError.Network))
    }

    @Test
    fun `a rejected key request fails before showing a code`() = runTest {
        api.keyResult = ApiResult.HttpError(503, "busy")

        val events = engine.login().toList()

        assertThat(events).containsExactly(
            QrLoginEvent.LoadingQr,
            QrLoginEvent.Failure(LoginError.Server),
        ).inOrder()
    }

    @Test
    fun `a missing cookie on authorization is a server failure`() = runTest {
        api.checks += authorized("")

        val events = engine.login().toList()

        assertThat(events.last()).isEqualTo(QrLoginEvent.Failure(LoginError.Server))
    }

    private fun waiting() = success(QrCheckResponse(QrCheckResponse.WAITING))

    private fun scanned() = success(QrCheckResponse(QrCheckResponse.SCANNED))

    private fun authorized(cookie: String) =
        success(QrCheckResponse(QrCheckResponse.AUTHORIZED, cookie = cookie))

    private fun success(body: QrCheckResponse) = ApiResult.Success(body)

    private fun networkError() = ApiResult.NetworkError(IOException("no route"))

    private companion object {
        const val QR_URL = "https://music.163.com/login?codekey=key-1"
    }

    private class FakeAuthApi : NcmAuthApi {
        var keyResult: ApiResult<QrKeyBody> =
            ApiResult.Success(QrKeyBody(code = 200, unikey = "key-1"))
        var createResult: ApiResult<QrCreateBody> =
            ApiResult.Success(QrCreateBody(qrurl = QR_URL))

        val checks = ArrayDeque<ApiResult<QrCheckResponse>>()
        var defaultCheck: ApiResult<QrCheckResponse> =
            ApiResult.Success(QrCheckResponse(QrCheckResponse.WAITING))
        var checkCalls = 0

        override suspend fun qrKey(): ApiResult<QrKeyBody> = keyResult

        override suspend fun qrCreate(key: String): ApiResult<QrCreateBody> = createResult

        override suspend fun qrCheck(key: String): ApiResult<QrCheckResponse> {
            checkCalls += 1
            return if (checks.isEmpty()) defaultCheck else checks.removeFirst()
        }

        override suspend fun userAccount(): ApiResult<UserAccountResponse> =
            ApiResult.Success(
                UserAccountResponse(
                    code = 200,
                    account = AccountDto(id = 1),
                    profile = ProfileDto(userId = 1, nickname = "tester"),
                ),
            )

        override suspend fun logout(): ApiResult<LogoutResponse> =
            ApiResult.Success(LogoutResponse(code = 200))
    }
}
