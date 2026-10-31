package one.tunkshif.swirlmusic.data.cache

import javax.inject.Inject

/** Clears per-account cached data (decision 0006-D18: the list cache is personal data). */
fun interface AccountScopedCache {
    suspend fun clear()
}

class RoomAccountScopedCache @Inject constructor(
    private val gate: CacheGate,
) : AccountScopedCache {
    override suspend fun clear() = gate.clearAll()
}
