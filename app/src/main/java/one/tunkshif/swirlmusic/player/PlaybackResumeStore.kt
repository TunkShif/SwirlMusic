package one.tunkshif.swirlmusic.player

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.cache.CacheGate
import one.tunkshif.swirlmusic.data.cache.PlaybackResumeEntity
import one.tunkshif.swirlmusic.data.cache.TrackEntity
import one.tunkshif.swirlmusic.data.cache.TrackListDao
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.di.IoDispatcher

/** Where the restored queue was: the tracks live in the controller's queue, this is the spot. */
data class RestoredQueue(
    val currentIndex: Int,
    val positionMs: Long,
    val loopMode: LoopMode,
)

/**
 * (Stored trackId+sourceAlbumId pairs, metadata, saved index) -> (track+source pairs,
 * adjusted index, stored pairs), dropping rows whose metadata is missing.
 */
internal fun restoreQueue(
    entries: List<Pair<Long, Long?>>,
    metadata: Map<Long, Track>,
    savedIndex: Int,
): Triple<List<Pair<Track, Long?>>, Int, List<Pair<Long, Long?>>>? {
    if (entries.isEmpty()) return null
    var index = 0
    val tracks = mutableListOf<Pair<Track, Long?>>()
    entries.forEachIndexed { i, (id, sourceAlbumId) ->
        val track = metadata[id] ?: return@forEachIndexed
        if (i <= savedIndex) index = tracks.size
        tracks += (track to sourceAlbumId)
    }
    if (tracks.isEmpty()) return null
    return Triple(tracks, index.coerceAtMost(tracks.lastIndex), entries)
}

/** Persistence for the last queue and resume row; all writes go through the cache gate. */
@Singleton
class PlaybackResumeStore @Inject constructor(
    private val dao: TrackListDao,
    private val gate: CacheGate,
    private val timeSource: TimeSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** Load the saved queue + resume row. Null when nothing was playing. */
    suspend fun load(): Pair<List<Pair<Track, Long?>>, RestoredQueue>? = withContext(io) {
        val resume = dao.resume() ?: return@withContext null
        val rows = dao.queueItems()
        if (rows.isEmpty()) return@withContext null
        val metadata = dao.tracksByIds(rows.map { it.trackId }).associate { it.id to it.toTrack() }
        val entries = rows.map { it.trackId to it.sourceAlbumId }
        restoreQueue(entries, metadata, resume.currentIndex)?.let { (tracks, index, _) ->
            tracks to RestoredQueue(
                index,
                resume.positionMs,
                LoopMode.entries.getOrElse(resume.loopMode) { LoopMode.ListLoop },
            )
        }
    }

    /**
     * Save the queue order (also upserts its metadata) and the resume row. Each entry is a
     * track plus the album it was played from, if any. Ordered by the gate's mutex so it
     * can't land in the middle of an account clear.
     */
    suspend fun save(
        queue: List<Pair<Track, Long?>>,
        currentIndex: Int,
        positionMs: Long,
        loopMode: LoopMode,
    ) {
        gate.writeIfCurrent(gate.generation) {
            upsertTracks(queue.map { TrackEntity.of(it.first, timeSource.nowMillis()) })
            replaceQueue(queue.map { it.first.id to it.second })
            saveResume(
                PlaybackResumeEntity(0, currentIndex, positionMs, loopMode.ordinal, timeSource.nowMillis()),
            )
        }
    }

    /** Wipe the persisted queue + resume row, e.g. when the last entry is removed. */
    suspend fun clear() {
        gate.writeIfCurrent(gate.generation) {
            clearQueue()
            clearResume()
        }
    }
}
