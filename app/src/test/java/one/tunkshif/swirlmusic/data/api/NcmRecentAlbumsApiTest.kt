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
 * `/record/recent/album` against a trimmed real capture. The interceptor captures the request
 * and answers inline; there is no real network.
 */
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NcmRecentAlbumsApiTest {

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
    fun `recent albums always ask fresh and map every field`() = runTest {
        nextBody = fixture("record_recent_album.json")

        val result = api.recentAlbums(3)

        val url = lastRequest!!.url
        assertThat(url.encodedPath).isEqualTo("/record/recent/album")
        assertThat(url.queryParameter("limit")).isEqualTo("3")
        // fresh = true: the timestamp defeats the server's two-minute URL cache.
        assertThat(url.queryParameter("timestamp")).isNotNull()

        val albums = (result as ApiResult.Success).value
        assertThat(albums).hasSize(3)
        val first = albums.first()
        assertThat(first.album.id).isEqualTo(34720827)
        assertThat(first.album.name).isEqualTo("周杰伦的床边故事")
        assertThat(first.album.artistLine).isEqualTo("周杰伦")
        // http:// artwork is upgraded and sized like every other mapper.
        assertThat(first.album.artworkUrl)
            .isEqualTo(
                "https://p3.music.126.net/cUTk0ewrQtYGP2YpPZoUng==" +
                    "/3265549553028224.jpg?param=300y300",
            )
        assertThat(first.album.trackCount).isEqualTo(10)
        assertThat(first.playedAtMillis).isEqualTo(1794839388651)
    }

    @Test
    fun `several artists are joined into one line`() = runTest {
        nextBody = """
            {"code":200,"data":{"total":1,"list":[
              {"playTime":7,"data":{"id":1,"name":"A","picUrl":"http://h/a.jpg",
               "artists":[{"id":1,"name":"One"},{"id":2,"name":"Two"}],"size":5}}
            ]}}
        """.trimIndent()

        val result = api.recentAlbums(1)

        assertThat((result as ApiResult.Success).value.first().album.artistLine)
            .isEqualTo("One / Two")
    }

    @Test
    fun `the songs payload and other unknown keys are ignored`() = runTest {
        nextBody = """
            {"code":200,"data":{"total":1,"list":[
              {"playTime":9,"os":"pc","resourceId":"55","multiTerminalInfo":{"os":"pc"},
               "data":{"id":55,"name":"Full","picUrl":"http://h/b.jpg","artists":[],
                "size":8,"songs":[{"id":1,"name":"not wanted","ar":[],"al":{}}],
                "lastSong":{"id":2},"company":"Co","alias":["x"]}}
            ]}}
        """.trimIndent()

        val result = api.recentAlbums(1)

        val album = (result as ApiResult.Success).value.first()
        assertThat(album.album.id).isEqualTo(55)
        assertThat(album.playedAtMillis).isEqualTo(9)
    }

    @Test
    fun `a non-200 body code is an http error`() = runTest {
        nextBody = """{"code":400,"data":null}"""

        val result = api.recentAlbums(3)

        assertThat(result).isInstanceOf(ApiResult.HttpError::class.java)
        assertThat((result as ApiResult.HttpError).code).isEqualTo(400)
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/ncm/$name")) {
            "missing fixture $name"
        }.readText()
}
