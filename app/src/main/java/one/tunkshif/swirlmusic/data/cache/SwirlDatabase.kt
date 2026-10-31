package one.tunkshif.swirlmusic.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, SnapshotEntity::class, SnapshotTrackEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SwirlDatabase : RoomDatabase() {
    abstract fun trackListDao(): TrackListDao
}
