package one.tunkshif.swirlmusic.data.cache

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.tunkshif.swirlmusic.di.IoDispatcher

/**
 * Guards cache writes against an in-flight account clear. Readers and writers capture
 * [generation] before their work; [clearAll] bumps it under the mutex so any fetch that
 * completes after a sign-out lands in a fresh generation and is dropped.
 */
@Singleton
class CacheGate @Inject constructor(
    private val dao: TrackListDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val mutex = Mutex()

    @Volatile
    var generation: Int = 0
        private set

    /** Run [block] only when [expected] still equals the current generation. */
    suspend fun <T> writeIfCurrent(expected: Int, block: suspend TrackListDao.() -> T): T? =
        mutex.withLock {
            if (generation != expected) return null
            withContext(io) { dao.block() }
        }

    /** Wipe the whole cache and bump the generation under the same mutex. */
    suspend fun clearAll() {
        mutex.withLock {
            generation++
            withContext(io) { dao.clearAll() }
        }
    }
}
