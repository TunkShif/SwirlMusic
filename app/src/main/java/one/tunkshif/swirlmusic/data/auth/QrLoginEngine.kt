package one.tunkshif.swirlmusic.data.auth

import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmAuthApi
import one.tunkshif.swirlmusic.data.api.dto.QrCheckResponse

/** Why sign-in stopped. The UI owns the copy; the engine only names the case. */
enum class LoginError {
    Network,
    Server,
}

sealed interface QrLoginEvent {
    data object LoadingQr : QrLoginEvent

    /** The code is on screen and valid. Covers both "ready" and "waiting for scan". */
    data class QrVisible(val qrUrl: String) : QrLoginEvent

    /** The phone scanned the code and the user has not confirmed yet. */
    data object Scanned : QrLoginEvent

    /** The code expired, or the polling window closed. A new code will work. */
    data object Expired : QrLoginEvent

    data class Success(val cookie: String) : QrLoginEvent

    data class Failure(val error: LoginError) : QrLoginEvent
}

/**
 * QR sign-in, as a stream of events.
 *
 * The API reports its state in the body `code`: 801 waiting, 802 scanned, 803 authorized with a
 * cookie, 800 expired. Polling runs on the caller's dispatcher, so tests drive it with virtual
 * time.
 */
class QrLoginEngine @Inject constructor(
    private val api: NcmAuthApi,
    private val tuning: LoginTuning,
) {

    fun login(): Flow<QrLoginEvent> = flow {
        emit(QrLoginEvent.LoadingQr)

        val key = api.qrKey().orNull()?.unikey?.takeIf { it.isNotBlank() }
        if (key == null) {
            emit(QrLoginEvent.Failure(LoginError.Server))
            return@flow
        }

        val qrUrl = api.qrCreate(key).orNull()?.qrurl?.takeIf { it.isNotBlank() }
        if (qrUrl == null) {
            emit(QrLoginEvent.Failure(LoginError.Server))
            return@flow
        }
        emit(QrLoginEvent.QrVisible(qrUrl))

        var elapsed = 0L
        var networkFailures = 0
        var announcedScan = false

        while (elapsed < tuning.timeoutMillis) {
            delay(tuning.pollIntervalMillis)
            elapsed += tuning.pollIntervalMillis

            when (val result = api.qrCheck(key)) {
                is ApiResult.Success -> {
                    networkFailures = 0
                    when (result.value.code) {
                        QrCheckResponse.AUTHORIZED -> {
                            val cookie = result.value.cookie.orEmpty()
                            if (cookie.isBlank()) {
                                emit(QrLoginEvent.Failure(LoginError.Server))
                            } else {
                                emit(QrLoginEvent.Success(cookie))
                            }
                            return@flow
                        }

                        QrCheckResponse.SCANNED -> if (!announcedScan) {
                            announcedScan = true
                            emit(QrLoginEvent.Scanned)
                        }

                        QrCheckResponse.EXPIRED -> {
                            emit(QrLoginEvent.Expired)
                            return@flow
                        }

                        else -> Unit // 801 and unknown codes: keep waiting.
                    }
                }

                is ApiResult.NetworkError -> {
                    networkFailures += 1
                    if (networkFailures > tuning.maxNetworkRetries) {
                        emit(QrLoginEvent.Failure(LoginError.Network))
                        return@flow
                    }
                }

                is ApiResult.Unauthorized, is ApiResult.HttpError -> {
                    emit(QrLoginEvent.Failure(LoginError.Server))
                    return@flow
                }
            }
        }

        // Our own window closed before NetEase said anything. Same recovery as an expiry.
        emit(QrLoginEvent.Expired)
    }
}

private fun <T> ApiResult<T>.orNull(): T? = (this as? ApiResult.Success)?.value
