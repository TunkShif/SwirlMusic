package one.tunkshif.swirlmusic.data.library

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmArtistApi

/**
 * Menu avatars by artist id. Successes are cached — a null avatar is remembered as "none" so it
 * isn't refetched; failures are never cached, so the next call retries them.
 */
@Singleton
class ArtistAvatarRepository @Inject constructor(
    private val api: NcmArtistApi,
) {

    private val cache = ConcurrentHashMap<Long, Result>()

    private sealed interface Result {
        data class Found(val url: String) : Result
        data object None : Result
    }

    suspend fun avatars(ids: List<Long>): Map<Long, String> = coroutineScope {
        ids.distinct()
            .filter { !cache.containsKey(it) }
            .map { id ->
                async {
                    when (val result = api.artistAvatar(id)) {
                        is ApiResult.Success -> {
                            cache[id] = result.value?.let(Result::Found) ?: Result.None
                        }
                        // A failure stays uncached so the next call retries it.
                        else -> Unit
                    }
                }
            }
            .forEach { it.await() }

        ids.mapNotNull { id -> (cache[id] as? Result.Found)?.let { id to it.url } }.toMap()
    }
}
