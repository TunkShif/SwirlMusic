package one.tunkshif.swirlmusic.data.lyrics

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmLyricsApi
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.cache.LyricsDao
import one.tunkshif.swirlmusic.data.cache.toEntity
import one.tunkshif.swirlmusic.data.cache.toSource
import one.tunkshif.swirlmusic.data.model.Lyrics
import one.tunkshif.swirlmusic.data.model.LyricsSource
import one.tunkshif.swirlmusic.di.IoDispatcher

/**
 * Cache-first lyrics. The cache holds the raw strings and is re-parsed on every read, so parser
 * improvements reach already-fetched rows. A fresh row is 30 days; a failed refetch falls back
 * to a stale row.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val api: NcmLyricsApi,
    private val dao: LyricsDao,
    private val timeSource: TimeSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun lyrics(trackId: Long): ApiResult<Lyrics> {
        val cached = withContext(io) { dao.get(trackId) }
        val now = timeSource.nowMillis()
        if (cached != null && now - cached.fetchedAt < FRESH_MS) {
            return ApiResult.Success(parseOnDefault(cached.toSource()))
        }
        return when (val result = api.lyrics(trackId)) {
            is ApiResult.Success -> {
                withContext(io) {
                    dao.upsert(result.value.toEntity(trackId, fetchedAt = now))
                    dao.trim(MAX_ROWS)
                }
                ApiResult.Success(parseOnDefault(result.value))
            }
            is ApiResult.Unauthorized ->
                cached?.let { ApiResult.Success(parseOnDefault(it.toSource())) }
                    ?: result
            is ApiResult.HttpError ->
                cached?.let { ApiResult.Success(parseOnDefault(it.toSource())) }
                    ?: result
            is ApiResult.NetworkError ->
                cached?.let { ApiResult.Success(parseOnDefault(it.toSource())) }
                    ?: result
        }
    }

    /** Same as [lyrics] but drops the result and swallows every error but cancellation. */
    suspend fun prefetch(trackId: Long) {
        try {
            lyrics(trackId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Best-effort warm-up only; the foreground fetch reports real errors.
        }
    }

    private suspend fun parseOnDefault(source: LyricsSource): Lyrics =
        withContext(Dispatchers.Default) { LyricsParser.parse(source) }

    private companion object {
        const val FRESH_MS = 30L * 24 * 60 * 60 * 1_000
        const val MAX_ROWS = 500
    }
}
