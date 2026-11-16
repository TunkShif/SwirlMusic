package one.tunkshif.swirlmusic.player

import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.model.Track

/**
 * A [PlayerController.TrackPager] over a fixed id list: [pageSize] ids per call, resolved
 * cache-first through [resolve]. A slice that resolves to nothing is skipped (never ends the
 * queue); a failed call does.
 */
fun idTrackPager(
    ids: List<Long>,
    pageSize: Int,
    resolve: suspend (List<Long>) -> ApiResult<List<Track>>,
): PlayerController.TrackPager {
    var offset = 0
    return PlayerController.TrackPager {
        while (offset < ids.size) {
            val slice = ids.drop(offset).take(pageSize)
            offset += slice.size
            when (val result = resolve(slice)) {
                is ApiResult.Success -> if (result.value.isNotEmpty()) return@TrackPager result.value
                else -> return@TrackPager null
            }
        }
        null
    }
}
