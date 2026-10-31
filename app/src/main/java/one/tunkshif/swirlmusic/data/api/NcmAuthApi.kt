package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.api.dto.LogoutResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCheckResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCreateBody
import one.tunkshif.swirlmusic.data.api.dto.QrKeyBody
import one.tunkshif.swirlmusic.data.api.dto.UserAccountResponse

/**
 * The account and sign-in endpoints, as an interface so tests can replace them.
 *
 * [NcmApi] is the only production implementation.
 */
interface NcmAuthApi {
    suspend fun qrKey(): ApiResult<QrKeyBody>

    suspend fun qrCreate(key: String): ApiResult<QrCreateBody>

    suspend fun qrCheck(key: String): ApiResult<QrCheckResponse>

    suspend fun userAccount(): ApiResult<UserAccountResponse>

    suspend fun logout(): ApiResult<LogoutResponse>
}
