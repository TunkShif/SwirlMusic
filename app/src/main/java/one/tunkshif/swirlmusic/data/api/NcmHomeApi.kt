package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.model.Track

/**
 * The home screen endpoints, as an interface so tests replace them without a network stack.
 */
interface NcmHomeApi {
    suspend fun dailySongs(): ApiResult<List<Track>>
}
