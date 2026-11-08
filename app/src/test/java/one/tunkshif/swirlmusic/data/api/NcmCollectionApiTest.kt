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
 * The collection endpoints against canned bodies. The interceptor captures the request and
 * answers with a fixture; there is no real network.
 */
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NcmCollectionApiTest {

    private var now = 1_000L
    private val timeSource = object : TimeSource { override fun nowMillis() = now }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private var lastRequest: Request? = null
    private var nextStatus = 200
    private var nextBody = ""

    private val api = NcmApi(
        client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    lastRequest = chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(nextStatus)
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

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/ncm/$name")) {
            "missing fixture $name"
        }.readText()

    private fun canned(status: Int, body: String) {
        nextStatus = status
        nextBody = body
    }

    @Test
    fun `albumSaved reads isSub fresh`() = runTest {
        canned(200, fixture("album_detail_dynamic.json"))

        val result = api.albumSaved(1089008)

        assertThat(result).isEqualTo(ApiResult.Success(true))
        val url = lastRequest!!.url
        assertThat(url.encodedPath).isEqualTo("/album/detail/dynamic")
        assertThat(url.queryParameter("id")).isEqualTo("1089008")
        assertThat(url.queryParameter("timestamp")).isEqualTo("1000")
        assertThat(url.queryParameter("cookie")).isNull()
    }

    @Test
    fun `artistFollowed reads followed fresh`() = runTest {
        canned(200, fixture("artist_detail_dynamic.json"))

        val result = api.artistFollowed(9272)

        assertThat(result).isEqualTo(ApiResult.Success(true))
        val url = lastRequest!!.url
        assertThat(url.encodedPath).isEqualTo("/artist/detail/dynamic")
        assertThat(url.queryParameter("timestamp")).isEqualTo("1000")
    }

    @Test
    fun `setAlbumSaved writes t=1 and maps 200 to Done`() = runTest {
        canned(200, fixture("album_sub.json"))

        val result = api.setAlbumSaved(1089008, saved = true)

        assertThat(result).isEqualTo(ApiResult.Success(WriteOutcome.Done))
        val url = lastRequest!!.url
        assertThat(url.encodedPath).isEqualTo("/album/sub")
        assertThat(url.queryParameter("t")).isEqualTo("1")
        assertThat(url.queryParameter("timestamp")).isEqualTo("1000")
        assertThat(url.queryParameter("cookie")).isNull()
    }

    @Test
    fun `setAlbumSaved unsave writes t=0`() = runTest {
        canned(200, fixture("album_sub.json"))

        api.setAlbumSaved(1089008, saved = false)

        assertThat(lastRequest!!.url.queryParameter("t")).isEqualTo("0")
    }

    @Test
    fun `a NetEase 401 refusal body maps to Refused`() = runTest {
        canned(401, fixture("album_sub_locked.json"))

        val result = api.setAlbumSaved(1089008, saved = true)

        assertThat(result).isEqualTo(ApiResult.Success(WriteOutcome.Refused))
    }

    @Test
    fun `the fork's own 401 stays Unauthorized`() = runTest {
        canned(401, """{"code":401,"msg":"unauthorized"}""")

        val result = api.setAlbumSaved(1089008, saved = true)

        assertThat(result).isInstanceOf(ApiResult.Unauthorized::class.java)
    }

    @Test
    fun `unsaving an album that is not saved maps 404 to Done`() = runTest {
        canned(404, fixture("album_unsub_not_saved.json"))

        val result = api.setAlbumSaved(1089008, saved = false)

        assertThat(result).isEqualTo(ApiResult.Success(WriteOutcome.Done))
    }

    @Test
    fun `a 200 body with an error code is an HttpError`() = runTest {
        canned(200, """{"code":400,"message":"bad request"}""")

        val result = api.setAlbumSaved(1089008, saved = true)

        assertThat(result).isEqualTo(ApiResult.HttpError(400, """{"code":400,"message":"bad request"}"""))
    }

    @Test
    fun `a 200 body with code 404 on an unsave is Done`() = runTest {
        canned(200, fixture("album_unsub_not_saved.json"))

        val result = api.setAlbumSaved(1089008, saved = false)

        assertThat(result).isEqualTo(ApiResult.Success(WriteOutcome.Done))
    }

    @Test
    fun `a 200 body with code 404 on a save is an HttpError`() = runTest {
        canned(200, fixture("album_unsub_not_saved.json"))

        val result = api.setAlbumSaved(1089008, saved = true)

        assertThat(result).isInstanceOf(ApiResult.HttpError::class.java)
        assertThat((result as ApiResult.HttpError).code).isEqualTo(404)
    }

    @Test
    fun `a 404 on save stays an HttpError`() = runTest {
        canned(404, fixture("album_unsub_not_saved.json"))

        val result = api.setAlbumSaved(1089008, saved = true)

        assertThat(result).isInstanceOf(ApiResult.HttpError::class.java)
    }

    @Test
    fun `setArtistFollowed maps 200 to Done`() = runTest {
        canned(200, fixture("artist_sub.json"))

        val result = api.setArtistFollowed(9272, followed = true)

        assertThat(result).isEqualTo(ApiResult.Success(WriteOutcome.Done))
        val url = lastRequest!!.url
        assertThat(url.encodedPath).isEqualTo("/artist/sub")
        assertThat(url.queryParameter("t")).isEqualTo("1")
        assertThat(url.queryParameter("timestamp")).isEqualTo("1000")
    }
}
