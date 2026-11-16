package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.model.PlayableUrl

/**
 * Playback endpoints. `songUrls` takes many ids on purpose: one request per queue, not per track.
 */
interface NcmPlaybackApi {
    suspend fun songUrls(ids: List<Long>, level: String): ApiResult<List<PlayableUrl>>

    /**
     * Report a listen. [sourceAlbumId] marks the play as started from that album screen, which
     * is what NetEase's recently-played-albums record keys on.
     */
    suspend fun scrobble(trackId: Long, playedSeconds: Int, sourceAlbumId: Long?): ApiResult<Unit>
}
