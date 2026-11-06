package one.tunkshif.swirlmusic.data.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun get(trackId: Long): LyricsEntity?

    @Upsert
    suspend fun upsert(entity: LyricsEntity)

    @Query(
        "DELETE FROM lyrics WHERE trackId NOT IN " +
            "(SELECT trackId FROM lyrics ORDER BY fetchedAt DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)
}
