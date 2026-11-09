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
import one.tunkshif.swirlmusic.data.model.SearchType
import org.junit.Test

/**
 * Search goes through `/cloudsearch` — `/search` songs come back without `album.picUrl`.
 * Canned bodies; no real network.
 */
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NcmSearchApiTest {

    private var now = 1_000L
    private val timeSource = object : TimeSource { override fun nowMillis() = now }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private var lastRequest: Request? = null
    private var nextBody = ""

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

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/ncm/$name")) {
            "missing fixture $name"
        }.readText()

    @Test
    fun `search hits cloudsearch and maps song covers`() = runTest {
        nextBody = fixture("cloudsearch_songs.json")

        val result = api.search("magdalena bay", SearchType.Songs, limit = 3, offset = 0)

        val url = lastRequest!!.url
        assertThat(url.encodedPath).isEqualTo("/cloudsearch")
        assertThat(url.queryParameter("type")).isEqualTo("1")
        assertThat(url.queryParameter("cookie")).isNull()

        val results = (result as ApiResult.Success).value
        assertThat(results.songs).hasSize(3)
        results.songs.forEach { song ->
            assertThat(song.artworkUrl).isNotNull()
            assertThat(song.artistLine).isEqualTo("Magdalena Bay")
            assertThat(song.albumName).isEqualTo("Imaginal Disk")
        }
    }
}
