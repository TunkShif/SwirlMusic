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
import one.tunkshif.swirlmusic.data.api.dto.UserAccountResponse
import one.tunkshif.swirlmusic.di.IoDispatcher
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The NetEase endpoints this app calls. One method per endpoint; DTOs stay behind it.
 *
 * Phase 0 implements the two calls needed to know who is signed in. Later phases add the rest.
 */
class NcmApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: ApiConfig,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun loginStatus(): ApiResult<AccountStatusBody> =
        request("/login/status", LoginStatusResponse.serializer()).map { it.data }

    suspend fun userAccount(): ApiResult<UserAccountResponse> =
        request("/user/account", UserAccountResponse.serializer())

    private suspend fun <T> request(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): ApiResult<T> = withContext(ioDispatcher) {
        val request = Request.Builder().url(config.urlOf(path)).get().build()

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
