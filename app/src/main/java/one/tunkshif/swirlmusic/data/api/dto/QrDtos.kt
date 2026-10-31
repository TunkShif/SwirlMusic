package one.tunkshif.swirlmusic.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class QrKeyResponse(
    val code: Int = 0,
    val data: QrKeyBody = QrKeyBody(),
)

@Serializable
data class QrKeyBody(
    val code: Int = 0,
    val unikey: String = "",
)

@Serializable
data class QrCreateResponse(
    val code: Int = 0,
    val data: QrCreateBody = QrCreateBody(),
)

@Serializable
data class QrCreateBody(
    val qrurl: String = "",
    val qrimg: String? = null,
)

/**
 * `/login/qr/check` reports its state in `code`, not in HTTP status:
 * 800 expired, 801 waiting, 802 scanned, 803 authorized with a `cookie`.
 */
@Serializable
data class QrCheckResponse(
    val code: Int = 0,
    val message: String? = null,
    val cookie: String? = null,
) {
    companion object {
        const val EXPIRED = 800
        const val WAITING = 801
        const val SCANNED = 802
        const val AUTHORIZED = 803
    }
}

@Serializable
data class LogoutResponse(
    val code: Int = 0,
)
