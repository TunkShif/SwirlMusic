package one.tunkshif.swirlmusic.player

import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmPlaybackApi
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.model.PlayableUrl

/**
 * Audio quality. Phase 4 uses one level for everyone; Phase 7 turns the VIP policy and a setting
 * into a real choice.
 */
enum class AudioQuality(val level: String) {
    Standard("standard"),
    Higher("higher"),
    Exhigh("exhigh"),
    Lossless("lossless"),
}

/**
 * Resolves signed media URLs and keeps them until shortly before they expire.
 *
 * One request covers a whole queue: `/song/url/v1` accepts a comma-separated id list. A queue of
 * 30 tracks therefore costs one call, not thirty, which matters against the server's rate limit.
 */
class SongUrlResolver(
    private val api: NcmPlaybackApi,
    private val timeSource: TimeSource,
    private val quality: AudioQuality = AudioQuality.Exhigh,
    private val expiryMarginMillis: Long = 60_000,
) {

    private val cache = mutableMapOf<Long, PlayableUrl>()

    suspend fun resolve(
        ids: List<Long>,
        forceRefresh: Boolean = false,
    ): Map<Long, PlayableUrl> {
        if (ids.isEmpty()) return emptyMap()

        val now = timeSource.nowMillis()
        val known = mutableMapOf<Long, PlayableUrl>()
        val missing = mutableListOf<Long>()

        ids.distinct().forEach { id ->
            val cached = cache[id]
            val stillValid = cached != null &&
                cached.isPlayable &&
                cached.expiresAtMillis - expiryMarginMillis > now
            if (!forceRefresh && stillValid) {
                known[id] = requireNotNull(cached)
            } else {
                missing += id
            }
        }

        if (missing.isEmpty()) return known

        missing.chunked(MAX_IDS_PER_REQUEST).forEach { chunk ->
            when (val result = api.songUrls(chunk, quality.level)) {
                is ApiResult.Success -> result.value.forEach { playable ->
                    cache[playable.trackId] = playable
                    known[playable.trackId] = playable
                }

                // A failed chunk leaves the rest usable; the caller sees a missing id and reports it.
                is ApiResult.Unauthorized, is ApiResult.HttpError, is ApiResult.NetworkError -> Unit
            }
        }

        return known
    }

    fun cached(trackId: Long): PlayableUrl? = cache[trackId]

    private companion object {
        const val MAX_IDS_PER_REQUEST = 50
    }
}
