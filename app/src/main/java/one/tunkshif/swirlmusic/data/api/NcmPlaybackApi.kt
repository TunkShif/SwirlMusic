package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.model.PlayableUrl

/**
 * Playback endpoints. `songUrls` takes many ids on purpose: one request per queue, not per track.
 */
interface NcmPlaybackApi {
    suspend fun songUrls(ids: List<Long>, level: String): ApiResult<List<PlayableUrl>>

    suspend fun scrobble(trackId: Long, playedSeconds: Int): ApiResult<Unit>
}
