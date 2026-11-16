package one.tunkshif.swirlmusic.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

/**
 * The playback endpoints against canned bodies. The interceptor captures the request and
 * answers inline; there is no real network.
 */
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NcmPlaybackApiTest {

    private val timeSource = object : TimeSource { override fun nowMillis() = 1_000L }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private var lastRequest: Request? = null
    private var nextBody = "{}"

    private val api = NcmApi(
        client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    lastRequest = chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("canned")
                        .body(nextBody.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build(),
        json = json,
        config = ApiConfig("https://api.example.invalid", "test-token"),
        timeSource = timeSource,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `scrobble without a source sends only id and time`() = runTest {
        nextBody = """{"code":200}"""

        val result = api.scrobble(347230, 240, sourceAlbumId = null)

        assertThat(result).isEqualTo(ApiResult.Success(Unit))
        val url = lastRequest!!.url
        assertThat(url.encodedPath).isEqualTo("/scrobble/v1")
        assertThat(url.queryParameter("id")).isEqualTo("347230")
        assertThat(url.queryParameter("time")).isEqualTo("240")
        assertThat(url.queryParameter("source")).isNull()
        assertThat(url.queryParameter("sourcetype")).isNull()
        assertThat(url.queryParameter("sourceid")).isNull()
    }

    @Test
    fun `scrobble from an album sends all three source params`() = runTest {
        nextBody = """{"code":200}"""

        val result = api.scrobble(347230, 240, sourceAlbumId = 1089008)

        assertThat(result).isEqualTo(ApiResult.Success(Unit))
        val url = lastRequest!!.url
        assertThat(url.encodedPath).isEqualTo("/scrobble/v1")
        assertThat(url.queryParameter("id")).isEqualTo("347230")
        assertThat(url.queryParameter("time")).isEqualTo("240")
        // NetEase records the album only when all three are present (measured).
        assertThat(url.queryParameter("source")).isEqualTo("album")
        assertThat(url.queryParameter("sourcetype")).isEqualTo("album")
        assertThat(url.queryParameter("sourceid")).isEqualTo("1089008")
    }
}
