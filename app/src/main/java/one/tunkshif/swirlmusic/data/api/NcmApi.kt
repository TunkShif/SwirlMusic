package one.tunkshif.swirlmusic.data.api

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.tunkshif.swirlmusic.data.api.dto.AccountStatusBody
import one.tunkshif.swirlmusic.data.api.dto.LoginStatusResponse
import one.tunkshif.swirlmusic.data.api.dto.LogoutResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCheckResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCreateBody
import one.tunkshif.swirlmusic.data.api.dto.QrCreateResponse
import one.tunkshif.swirlmusic.data.api.dto.QrKeyBody
import one.tunkshif.swirlmusic.data.api.dto.QrKeyResponse
import one.tunkshif.swirlmusic.data.api.dto.UserAccountResponse
import one.tunkshif.swirlmusic.di.IoDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The NetEase endpoints this app calls. One method per endpoint; DTOs stay behind it.
 */
class NcmApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: ApiConfig,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NcmAuthApi {

    suspend fun loginStatus(): ApiResult<AccountStatusBody> =
        request("/login/status", LoginStatusResponse.serializer()).map { it.data }

    override suspend fun userAccount(): ApiResult<UserAccountResponse> =
        request("/user/account", UserAccountResponse.serializer())

    override suspend fun qrKey(): ApiResult<QrKeyBody> =
        request("/login/qr/key", QrKeyResponse.serializer()).map { it.data }

    override suspend fun qrCreate(key: String): ApiResult<QrCreateBody> =
        request("/login/qr/create", QrCreateResponse.serializer(), mapOf("key" to key))
            .map { it.data }

    override suspend fun qrCheck(key: String): ApiResult<QrCheckResponse> =
        request("/login/qr/check", QrCheckResponse.serializer(), mapOf("key" to key))

    override suspend fun logout(): ApiResult<LogoutResponse> =
        request("/logout", LogoutResponse.serializer())

    private suspend fun <T> request(
        path: String,
        deserializer: DeserializationStrategy<T>,
        params: Map<String, String> = emptyMap(),
    ): ApiResult<T> = withContext(ioDispatcher) {
        val url = config.urlOf(path).toHttpUrl().newBuilder()
            .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()

                when {
                    response.code == 401 -> ApiResult.Unauthorized(body)
                    !response.isSuccessful -> ApiResult.HttpError(response.code, body)

                    runCatching { json.parseToJsonElement(body) }
                        .getOrNull()
                        ?.isExpiredSession() == true -> ApiResult.Unauthorized(body)

                    else -> runCatching { json.decodeFromString(deserializer, body) }
                        .fold(
                            onSuccess = { ApiResult.Success(it) },
                            onFailure = { ApiResult.NetworkError(it) },
                        )
                }
            }
        } catch (error: IOException) {
            ApiResult.NetworkError(error)
        }
    }
}

/**
 * NetEase reports an expired session as `code: 301` inside a 200 response. `/login/status` nests
 * the same body one level deeper under `data`.
 */
private fun JsonElement.isExpiredSession(): Boolean {
    val top = this as? JsonObject ?: return false
    val code = top["code"].asIntOrNull()
        ?: (top["data"] as? JsonObject)?.get("code").asIntOrNull()
    return code == 301
}

private fun JsonElement?.asIntOrNull(): Int? =
    (this as? JsonPrimitive)?.content?.toIntOrNull()
