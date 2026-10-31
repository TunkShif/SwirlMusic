package one.tunkshif.swirlmusic.data.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmLibraryApi
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.api.inIdOrder
import one.tunkshif.swirlmusic.data.cache.SnapshotEntity
import one.tunkshif.swirlmusic.data.cache.TrackEntity
import one.tunkshif.swirlmusic.data.cache.TrackListDao
import one.tunkshif.swirlmusic.data.model.Track

/** A cached detail list: header fields plus its ordered ids and the resolved head rows. */
data class CachedList(
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val version: Long?,
    val orderedIds: List<Long>,
    val firstTracks: List<Track>,
)

/** A fresh fetch: header, ordered ids, the bundled tracks, and whether the order was rewritten. */
data class FetchedList(
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val version: Long?,
    val orderedIds: List<Long>,
    val firstTracks: List<Track>,
    val orderChanged: Boolean,
)

/**
 * Detail list data, stale-while-revalidate on Room (decision 0006): snapshots keep the
 * header and ordered ids, the track table keeps metadata. Stream URLs are never cached.
 */
@Singleton
class TrackListRepository @Inject constructor(
    private val api: NcmLibraryApi,
    private val dao: TrackListDao,
    private val timeSource: TimeSource,
    @one.tunkshif.swirlmusic.di.IoDispatcher private val io: CoroutineDispatcher,
) {

    companion object {
        const val PAGE_SIZE = 100
        private const val KEEP_SNAPSHOTS = 30

        fun playlistKey(id: Long) = "playlist:$id"
        fun albumKey(id: Long) = "album:$id"
        fun artistKey(id: Long) = "artist:$id"
    }

    /** Snapshot header + ordered ids + the first [limit] ids resolved from the track table. */
    suspend fun cached(sourceKey: String, limit: Int = PAGE_SIZE): CachedList? {
        val snapshot = withContext(io) { dao.snapshot(sourceKey) } ?: return null
        val ids = withContext(io) { dao.orderedIds(sourceKey) }
        val resolved = withContext(io) { dao.tracksByIds(ids.take(limit)) }.inEntityOrder(ids.take(limit))
        return CachedList(
            title = snapshot.title,
            subtitle = snapshot.subtitle,
            artworkUrl = snapshot.artworkUrl,
            version = snapshot.version,
            orderedIds = ids,
            firstTracks = resolved.map { it.toTrack() },
        )
    }

    /**
     * Fetch a playlist detail (n=50). If there was no snapshot or `trackUpdateTime` changed,
     * the ordered ids are rewritten and [FetchedList.orderChanged] is true.
     */
    suspend fun fetchPlaylist(id: Long): ApiResult<FetchedList> {
        val key = playlistKey(id)
        return when (val result = api.playlistDetail(id)) {
            is ApiResult.Success -> {
                val contents = result.value
                withContext(io) { dao.upsertTracks(contents.firstTracks.map { TrackEntity.of(it, timeSource.nowMillis()) }) }
                val previous = withContext(io) { dao.snapshot(key) }
                val orderChanged = previous == null || previous.version != contents.trackUpdateTime
                val now = timeSource.nowMillis()
                val entity = SnapshotEntity(
                    sourceKey = key,
                    title = contents.detail.name,
                    subtitle = playlistSubtitle(contents.detail.creatorName, contents.detail.trackCount),
                    artworkUrl = contents.detail.artworkUrl,
                    trackCount = contents.detail.trackCount,
                    version = contents.trackUpdateTime,
                    fetchedAt = now,
                    lastOpenedAt = now,
                )
                if (orderChanged) {
                    withContext(io) { dao.replaceSnapshot(entity, contents.trackIds) }
                } else {
                    withContext(io) { dao.upsertSnapshot(entity) }
                }
                withContext(io) { dao.touch(key, now) }
                withContext(io) { dao.evict(KEEP_SNAPSHOTS) }
                ApiResult.Success(
                    FetchedList(
                        title = contents.detail.name,
                        subtitle = playlistSubtitle(contents.detail.creatorName, contents.detail.trackCount),
                        artworkUrl = contents.detail.artworkUrl,
                        version = contents.trackUpdateTime,
                        orderedIds = contents.trackIds,
                        firstTracks = contents.firstTracks,
                        orderChanged = orderChanged,
                    ),
                )
            }

            is ApiResult.NetworkError -> result
            is ApiResult.HttpError -> result
            is ApiResult.Unauthorized -> result
        }
    }

    suspend fun fetchAlbum(id: Long): ApiResult<FetchedList> {
        val key = albumKey(id)
        return when (val result = api.albumDetail(id)) {
            is ApiResult.Success -> {
                val album = result.value
                val subtitle = listOfNotNull(
                    album.artistLine.takeIf { it.isNotBlank() },
                    album.publishYear?.toString(),
                ).joinToString(" · ")
                store(key, album.name, subtitle, album.artworkUrl, album.tracks.size, null, album.tracks)
                ApiResult.Success(
                    FetchedList(album.name, subtitle, album.artworkUrl, null, album.tracks.map { it.id }, album.tracks, true),
                )
            }

            is ApiResult.NetworkError -> result
            is ApiResult.HttpError -> result
            is ApiResult.Unauthorized -> result
        }
    }

    suspend fun fetchArtist(id: Long): ApiResult<FetchedList> {
        val key = artistKey(id)
        return when (val result = api.artistDetail(id)) {
            is ApiResult.Success -> {
                val artist = result.value
                store(key, artist.name, null, artist.artworkUrl, artist.tracks.size, null, artist.tracks)
                ApiResult.Success(
                    FetchedList(artist.name, null, artist.artworkUrl, null, artist.tracks.map { it.id }, artist.tracks, true),
                )
            }

            is ApiResult.NetworkError -> result
            is ApiResult.HttpError -> result
            is ApiResult.Unauthorized -> result
        }
    }

    /** Write tracks + a full snapshot, then touch and evict. */
    private suspend fun store(
        key: String,
        title: String,
        subtitle: String?,
        artworkUrl: String?,
        trackCount: Int,
        version: Long?,
        tracks: List<Track>,
    ) {
        val now = timeSource.nowMillis()
        withContext(io) { dao.upsertTracks(tracks.map { TrackEntity.of(it, now) }) }
        withContext(io) {
            dao.replaceSnapshot(
                SnapshotEntity(key, title, subtitle, artworkUrl, trackCount, version, now, now),
                tracks.map { it.id },
            )
        }
        withContext(io) { dao.touch(key, now) }
        withContext(io) { dao.evict(KEEP_SNAPSHOTS) }
    }

    /**
     * Resolve ids cache-first: DB hits, a `songsByIds` call only for the misses, upserted,
     * merged back in requested order. A missing id is dropped but still counts as consumed.
     */
    suspend fun tracksByIds(ids: List<Long>): ApiResult<List<Track>> {
        if (ids.isEmpty()) return ApiResult.Success(emptyList())
        val hits = withContext(io) { dao.tracksByIds(ids) }.associateBy { it.id }
        val misses = ids.filter { it !in hits }
        val fetched = if (misses.isEmpty()) {
            emptyList()
        } else {
            when (val result = api.songsByIds(misses)) {
                is ApiResult.Success -> {
                    withContext(io) { dao.upsertTracks(result.value.map { TrackEntity.of(it, timeSource.nowMillis()) }) }
                    result.value
                }

                is ApiResult.NetworkError -> return result
                is ApiResult.HttpError -> return result
                is ApiResult.Unauthorized -> return result
            }
        }
        val all = (hits.values.map { it.toTrack() } + fetched).inIdOrder(ids)
        return ApiResult.Success(all)
    }

    private fun List<TrackEntity>.inEntityOrder(ids: List<Long>): List<TrackEntity> {
        val byId = associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    private fun playlistSubtitle(creatorName: String?, trackCount: Int): String = listOfNotNull(
        creatorName,
        "$trackCount tracks",
    ).joinToString(" · ")

}
