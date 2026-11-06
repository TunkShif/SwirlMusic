package one.tunkshif.swirlmusic.data.lyrics

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmLyricsApi
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.cache.LyricsDao
import one.tunkshif.swirlmusic.data.cache.SwirlDatabase
import one.tunkshif.swirlmusic.data.cache.toEntity
import one.tunkshif.swirlmusic.data.model.Lyrics
import one.tunkshif.swirlmusic.data.model.LyricsSource
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private fun testSource(marker: String) = LyricsSource(
    lrc = "[00:01.00]$marker line",
    translation = null,
    romanization = null,
    wordTimed = null,
    pureMusic = false,
    noLyric = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyricsRepositoryTest {

    private lateinit var db: SwirlDatabase
    private lateinit var dao: LyricsDao
    private lateinit var repo: LyricsRepository
    private val api = FakeLyricsApi()
    private var now = 10_000L
    private val time = object : TimeSource { override fun nowMillis() = now }

    private class FakeLyricsApi : NcmLyricsApi {
        var calls = 0
        var result: ApiResult<LyricsSource> = ApiResult.Success(testSource("fresh"))
        override suspend fun lyrics(trackId: Long): ApiResult<LyricsSource> {
            calls++
            return result
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            SwirlDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.lyricsDao()
        repo = LyricsRepository(api, dao, time, UnconfinedTestDispatcher())
        api.calls = 0
        api.result = ApiResult.Success(testSource("fresh"))
    }

    @After
    fun tearDown() = db.close()



    private fun textOf(lyrics: Lyrics): String =
        ((lyrics as Lyrics.Synced).lines.first().text)

    @Test
    fun `a fresh cache hit never calls the api`() = runTest {
        dao.upsert(testSource("cached").toEntity(trackId = 1, fetchedAt = now - 1000))

        val result = repo.lyrics(1)

        assertThat(api.calls).isEqualTo(0)
        assertThat(textOf((result as ApiResult.Success).value)).isEqualTo("cached line")
    }

    @Test
    fun `a stale row refetches and overwrites`() = runTest {
        dao.upsert(
            testSource("stale").toEntity(
                trackId = 1,
                fetchedAt = now - 31L * 24 * 60 * 60 * 1000,
            ),
        )

        val result = repo.lyrics(1)

        assertThat(api.calls).isEqualTo(1)
        assertThat(textOf((result as ApiResult.Success).value)).isEqualTo("fresh line")
        // The cache now holds the new source.
        val second = repo.lyrics(1)
        assertThat(api.calls).isEqualTo(1)
        assertThat(textOf((second as ApiResult.Success).value)).isEqualTo("fresh line")
    }

    @Test
    fun `a failed refetch falls back to the stale row`() = runTest {
        dao.upsert(
            testSource("stale").toEntity(
                trackId = 1,
                fetchedAt = now - 31L * 24 * 60 * 60 * 1000,
            ),
        )
        api.result = ApiResult.NetworkError(IOException("offline"))

        val result = repo.lyrics(1)

        assertThat(textOf((result as ApiResult.Success).value)).isEqualTo("stale line")
    }

    @Test
    fun `a miss with a failure passes the failure through`() = runTest {
        api.result = ApiResult.NetworkError(IOException("offline"))

        val result = repo.lyrics(1)

        assertThat(result).isInstanceOf(ApiResult.NetworkError::class.java)
    }

    @Test
    fun `prefetch never throws and caches`() = runTest {
        repo.prefetch(7)

        assertThat(dao.get(7)).isNotNull()

        api.result = ApiResult.NetworkError(IOException("boom"))
        repo.prefetch(8) // must not throw
        assertThat(dao.get(8)).isNull()
    }

    @Test
    fun `trim keeps only the newest rows`() = runTest {
        repeat(10) { dao.upsert(testSource("t").toEntity(trackId = it.toLong(), fetchedAt = it.toLong())) }

        dao.trim(keep = 5)

        assertThat(dao.get(0)).isNull()
        assertThat(dao.get(9)).isNotNull()
        assertThat(dao.get(5)).isNotNull()
        assertThat(dao.get(4)).isNull()
    }
}
