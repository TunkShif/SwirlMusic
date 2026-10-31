package one.tunkshif.swirlmusic.data.cache

import javax.inject.Inject

/** Clears per-account cached data (decision 0006-D18: the list cache is personal data). */
fun interface AccountScopedCache {
    suspend fun clear()
}

class RoomAccountScopedCache @Inject constructor(
    private val dao: TrackListDao,
    @one.tunkshif.swirlmusic.di.IoDispatcher private val io: kotlinx.coroutines.CoroutineDispatcher,
) : AccountScopedCache {
    override suspend fun clear() = kotlinx.coroutines.withContext(io) { dao.clearAll() }
}
