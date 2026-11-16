package one.tunkshif.swirlmusic.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import one.tunkshif.swirlmusic.data.model.CommentPageKey
import one.tunkshif.swirlmusic.data.model.CommentSort
import one.tunkshif.swirlmusic.data.model.CommentTarget
import one.tunkshif.swirlmusic.data.model.CommentTargetKind
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

/**
 * `/comment/new` against canned bodies. The interceptor captures the request and answers
 * with a fixture; there is no real network.
 */
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NcmCommentsApiTest {

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
    fun `hot first page asks for pageNo 1 with no cursor`() = runTest {
        canned(200, fixture("comment_new_song_hot.json"))

        val result = api.comments(
            CommentTarget(CommentTargetKind.Song, 186016),
            CommentSort.Hot,
            key = null,
        )

        val url = lastRequest!!.url
        assertThat(url.encodedPath).isEqualTo("/comment/new")
        assertThat(url.queryParameter("type")).isEqualTo("0")
        assertThat(url.queryParameter("id")).isEqualTo("186016")
        assertThat(url.queryParameter("sortType")).isEqualTo("2")
        assertThat(url.queryParameter("pageSize")).isEqualTo("20")
        assertThat(url.queryParameter("pageNo")).isEqualTo("1")
        assertThat(url.queryParameter("cursor")).isNull()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val page = (result as ApiResult.Success).value
        assertThat(page.comments).hasSize(3)
        assertThat(page.totalCount).isEqualTo(1969866)
        assertThat(page.next).isEqualTo(CommentPageKey(pageNo = 2, cursor = "normalHot#20"))

        val first = page.comments.first()
        assertThat(first.id).isEqualTo(4956438)
        assertThat(first.userName).isEqualTo("Listener 1")
        assertThat(first.likedCount).isEqualTo(823213)
        assertThat(first.replyCount).isEqualTo(14)
        assertThat(first.location).isNull()
        assertThat(first.replyTo).isNull()
    }

    @Test
    fun `hot with a key sends its pageNo and still no cursor`() = runTest {
        canned(200, fixture("comment_new_song_hot.json"))

        api.comments(
            CommentTarget(CommentTargetKind.Song, 186016),
            CommentSort.Hot,
            key = CommentPageKey(pageNo = 2, cursor = "normalHot#20"),
        )

        val url = lastRequest!!.url
        assertThat(url.queryParameter("pageNo")).isEqualTo("2")
        assertThat(url.queryParameter("cursor")).isNull()
    }

    @Test
    fun `newest first page sends cursor 0`() = runTest {
        canned(200, fixture("comment_new_album_newest.json"))

        val result = api.comments(
            CommentTarget(CommentTargetKind.Album, 18905),
            CommentSort.Newest,
            key = null,
        )

        val url = lastRequest!!.url
        assertThat(url.queryParameter("type")).isEqualTo("3")
        assertThat(url.queryParameter("id")).isEqualTo("18905")
        assertThat(url.queryParameter("sortType")).isEqualTo("3")
        assertThat(url.queryParameter("pageNo")).isEqualTo("1")
        assertThat(url.queryParameter("cursor")).isEqualTo("0")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val page = (result as ApiResult.Success).value
        assertThat(page.comments).hasSize(4)
        assertThat(page.totalCount).isEqualTo(5110)
        assertThat(page.next).isEqualTo(CommentPageKey(pageNo = 2, cursor = "1522551232881"))

        val quoted = page.comments.single { it.replyTo != null }
        assertThat(quoted.replyTo!!.userName).isEqualTo("Listener 11")
        assertThat(page.comments.map { it.replyCount }).contains(3)
    }

    @Test
    fun `newest with a key passes its cursor back`() = runTest {
        canned(200, fixture("comment_new_album_newest.json"))

        api.comments(
            CommentTarget(CommentTargetKind.Album, 18905),
            CommentSort.Newest,
            key = CommentPageKey(pageNo = 2, cursor = "1522551232881"),
        )

        val url = lastRequest!!.url
        assertThat(url.queryParameter("cursor")).isEqualTo("1522551232881")
        assertThat(url.queryParameter("pageNo")).isEqualTo("2")
    }

    @Test
    fun `hasMore false ends paging`() = runTest {
        canned(200, fixture("comment_new_song_recommended_page2.json"))

        val result = api.comments(
            CommentTarget(CommentTargetKind.Song, 186016),
            CommentSort.Hot,
            key = CommentPageKey(pageNo = 2, cursor = "normalHot#20"),
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val page = (result as ApiResult.Success).value
        assertThat(page.comments).isNotEmpty()
        assertThat(page.next).isNull()
    }

    @Test
    fun `an empty page has no next key`() = runTest {
        canned(200, fixture("comment_new_empty.json"))

        val result = api.comments(
            CommentTarget(CommentTargetKind.Song, 1),
            CommentSort.Hot,
            key = null,
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val page = (result as ApiResult.Success).value
        assertThat(page.comments).isEmpty()
        assertThat(page.totalCount).isEqualTo(0)
        assertThat(page.next).isNull()
    }

    @Test
    fun `a 200 refusal body without data is an HttpError`() = runTest {
        canned(200, fixture("comment_new_bad_resource.json"))

        val result = api.comments(
            CommentTarget(CommentTargetKind.Playlist, 1),
            CommentSort.Hot,
            key = null,
        )

        assertThat(result).isEqualTo(
            ApiResult.HttpError(400, fixture("comment_new_bad_resource.json")),
        )
    }

    @Test
    fun `emoji tokens in content are mapped`() = runTest {
        canned(200, fixture("comment_new_song_recommended.json"))

        val result = api.comments(
            CommentTarget(CommentTargetKind.Song, 186016),
            CommentSort.Hot,
            key = null,
        )

        val page = (result as ApiResult.Success).value
        assertThat(page.comments.map { it.text }).contains("打卡❤️")
    }
}
