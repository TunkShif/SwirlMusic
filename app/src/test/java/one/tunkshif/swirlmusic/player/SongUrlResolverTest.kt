package one.tunkshif.swirlmusic.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmPlaybackApi
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.model.PlayableUrl
import org.junit.Test

class SongUrlResolverTest {

    private var now = 1_000_000L
    private val time = object : TimeSource {
        override fun nowMillis(): Long = now
    }
    private val api = FakePlaybackApi()
    private val resolver = SongUrlResolver(api, time)

    @Test
    fun `a whole queue costs one request`() = runTest {
        val resolved = resolver.resolve(listOf(1, 2, 3))

        assertThat(api.requests).hasSize(1)
        assertThat(api.requests.single()).containsExactly(1L, 2L, 3L)
        assertThat(resolved.keys).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `a still valid url is served from the cache`() = runTest {
        resolver.resolve(listOf(1))
        now += 60_000

        resolver.resolve(listOf(1))

        assertThat(api.requests).hasSize(1)
    }

    @Test
    fun `a url close to expiry is refetched`() = runTest {
        resolver.resolve(listOf(1))
        // The resolver keeps a one minute safety margin.
        now += 1_200_000 - 30_000

        resolver.resolve(listOf(1))

        assertThat(api.requests).hasSize(2)
    }

    @Test
    fun `force refresh ignores the cache`() = runTest {
        resolver.resolve(listOf(1))

        resolver.resolve(listOf(1), forceRefresh = true)

        assertThat(api.requests).hasSize(2)
    }

    @Test
    fun `only the missing ids are requested`() = runTest {
        resolver.resolve(listOf(1, 2))

        resolver.resolve(listOf(2, 3))

        assertThat(api.requests).hasSize(2)
        assertThat(api.requests.last()).containsExactly(3L)
    }

    @Test
    fun `a track without a url is not cached and is asked for again`() = runTest {
        api.unplayable += 2L

        resolver.resolve(listOf(1, 2))
        resolver.resolve(listOf(2))

        assertThat(api.requests).hasSize(2)
        assertThat(api.requests.last()).containsExactly(2L)
    }

    @Test
    fun `a failed request leaves the other ids usable`() = runTest {
        api.dropIds += 2L

        val resolved = resolver.resolve(listOf(1, 2))

        assertThat(resolved.keys).containsExactly(1L)
    }

    @Test
    fun `an empty list asks the server nothing`() = runTest {
        assertThat(resolver.resolve(emptyList())).isEmpty()
        assertThat(api.requests).isEmpty()
    }

    private inner class FakePlaybackApi : NcmPlaybackApi {
        val requests = mutableListOf<List<Long>>()
        val unplayable = mutableSetOf<Long>()
        val dropIds = mutableSetOf<Long>()
        var failure: ApiResult<List<PlayableUrl>>? = null

        override suspend fun songUrls(
            ids: List<Long>,
            level: String,
        ): ApiResult<List<PlayableUrl>> {
            requests += ids
            failure?.let { return it }
            return ApiResult.Success(
                ids.filterNot { it in dropIds }.map { id ->
                    PlayableUrl(
                        trackId = id,
                        url = if (id in unplayable) null else "https://cdn/$id.mp3",
                        expiresAtMillis = now + 1_200_000,
                        isTrial = false,
                        level = level,
                        bitrate = 320_000,
                    )
                },
            )
        }

        override suspend fun scrobble(trackId: Long, playedSeconds: Int): ApiResult<Unit> =
            ApiResult.Success(Unit)
    }
}
