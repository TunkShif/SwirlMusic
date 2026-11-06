package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.model.LyricsSource

/** Lyrics for a track (`/lyric/new`). */
interface NcmLyricsApi {
    suspend fun lyrics(trackId: Long): ApiResult<LyricsSource>
}
