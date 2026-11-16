package one.tunkshif.swirlmusic.data.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmLibraryApi
import one.tunkshif.swirlmusic.data.cache.CacheGate
import one.tunkshif.swirlmusic.data.cache.RecentAlbumEntity
import one.tunkshif.swirlmusic.data.cache.TrackListDao
import one.tunkshif.swirlmusic.data.model.AlbumSummary
import one.tunkshif.swirlmusic.data.model.RecentAlbum
import one.tunkshif.swirlmusic.di.IoDispatcher

/**
 * Recently played albums, cache-first. The full fetch is ~3 MB and takes seconds, so callers
 * show [cached] immediately and [refresh] in the background (0013-D56 follow-up). A small
 * refresh (Home's ten rows) merges into the stored list: the server returns newest-first, so
 * the fresh head is authoritative and cached rows not in it keep their order behind it.
 */
@Singleton
class RecentAlbumsRepository @Inject constructor(
    private val api: NcmLibraryApi,
    private val dao: TrackListDao,
    private val gate: CacheGate,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun cached(): List<RecentAlbum> =
        withContext(io) { dao.recentAlbums().map { it.toDomain() } }

    /**
     * Fetch and persist. `limit >= [RECENT_ALBUMS_CAP]` replaces the whole table; a smaller
     * limit merges: fresh rows first, then cached rows they did not cover, capped at the cap.
     * A failure returns the error and leaves the cache alone.
     */
    suspend fun refresh(limit: Int): ApiResult<List<RecentAlbum>> {
        val expected = gate.generation
        val result = api.recentAlbums(limit)
        if (result is ApiResult.Success) {
            val merged = if (limit >= RECENT_ALBUMS_CAP) {
                result.value
            } else {
                val freshIds = result.value.mapTo(HashSet()) { it.album.id }
                (result.value + cached().filterNot { it.album.id in freshIds })
                    .take(RECENT_ALBUMS_CAP)
            }
            gate.writeIfCurrent(expected) {
                replaceRecentAlbums(merged.mapIndexed { i, album -> album.toEntity(i) })
            }
        }
        return result
    }

    companion object {
        /** `/record/recent/album` hard-caps at 100 rows. */
        const val RECENT_ALBUMS_CAP = 100
    }
}

private fun RecentAlbumEntity.toDomain() = RecentAlbum(
    album = AlbumSummary(
        id = albumId,
        name = name,
        artistLine = artistLine,
        artworkUrl = artworkUrl,
        trackCount = trackCount,
    ),
    playedAtMillis = playedAtMillis,
)

private fun RecentAlbum.toEntity(position: Int) = RecentAlbumEntity(
    position = position,
    albumId = album.id,
    name = album.name,
    artistLine = album.artistLine,
    artworkUrl = album.artworkUrl,
    trackCount = album.trackCount,
    playedAtMillis = playedAtMillis,
)
