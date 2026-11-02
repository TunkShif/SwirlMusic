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

/** (Stored ids, metadata, saved index) -> (tracks, adjusted index), dropping missing rows. */
internal fun restoreQueue(
    ids: List<Long>,
    metadata: Map<Long, Track>,
    savedIndex: Int,
): Triple<List<Track>, Int, List<Long>>? {
    if (ids.isEmpty()) return null
    var index = 0
    val tracks = mutableListOf<Track>()
    ids.forEachIndexed { i, id ->
        val track = metadata[id] ?: return@forEachIndexed
        if (i <= savedIndex) index = tracks.size
        tracks += track
    }
    if (tracks.isEmpty()) return null
    return Triple(tracks, index.coerceAtMost(tracks.lastIndex), ids)
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
    suspend fun load(): Pair<List<Track>, RestoredQueue>? = withContext(io) {
        val resume = dao.resume() ?: return@withContext null
        val ids = dao.queueIds()
        if (ids.isEmpty()) return@withContext null
        val metadata = dao.tracksByIds(ids).associate { it.id to it.toTrack() }
        restoreQueue(ids, metadata, resume.currentIndex)?.let { (tracks, index, _) ->
            tracks to RestoredQueue(
                index,
                resume.positionMs,
                LoopMode.entries.getOrElse(resume.loopMode) { LoopMode.ListLoop },
            )
        }
    }

    /**
     * Save the queue order (also upserts its metadata) and the resume row. Ordered by the
     * gate's mutex so it can't land in the middle of an account clear.
     */
    suspend fun save(
        queue: List<Track>,
        currentIndex: Int,
        positionMs: Long,
        loopMode: LoopMode,
    ) {
        gate.writeIfCurrent(gate.generation) {
            upsertTracks(queue.map { TrackEntity.of(it, timeSource.nowMillis()) })
            replaceQueue(queue.map { it.id })
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
