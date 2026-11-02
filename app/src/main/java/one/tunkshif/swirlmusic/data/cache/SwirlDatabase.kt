package one.tunkshif.swirlmusic.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * This database is a cache (list snapshots, track metadata, playback resume). Schema bumps
 * drop every table via `fallbackToDestructiveMigration(true)` — losing cached lists and the
 * resume row is acceptable; the next open refetches.
 */
@Database(
    entities = [
        TrackEntity::class,
        SnapshotEntity::class,
        SnapshotTrackEntity::class,
        QueueItemEntity::class,
        PlaybackResumeEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class SwirlDatabase : RoomDatabase() {
    abstract fun trackListDao(): TrackListDao
}
