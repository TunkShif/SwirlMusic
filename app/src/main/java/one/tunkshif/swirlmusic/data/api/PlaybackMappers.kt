package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.api.dto.SongUrlDto
import one.tunkshif.swirlmusic.data.model.PlayableUrl

/** Used when NetEase omits `expi`: still short enough to be safe. */
private const val FALLBACK_TTL_MILLIS = 10 * 60 * 1_000L

internal fun SongUrlDto.toDomain(nowMillis: Long): PlayableUrl {
    val ttlMillis = if (expi > 0) expi * 1_000 else FALLBACK_TTL_MILLIS
    return PlayableUrl(
        trackId = id,
        url = url?.replaceFirst("http://", "https://"),
        expiresAtMillis = nowMillis + ttlMillis,
        isTrial = freeTrialInfo != null,
        level = level ?: "",
        bitrate = br,
    )
}
