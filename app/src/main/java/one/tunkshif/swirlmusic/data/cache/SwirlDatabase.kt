package one.tunkshif.swirlmusic.data.cache

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * This database is a cache (list snapshots, track metadata, playback resume, lyrics). Schema
 * bumps drop every table via `fallbackToDestructiveMigration(true)` — losing cached lists and the
 * resume row is acceptable; the next open refetches. Lyrics ride an AutoMigration so existing
 * rows survive.
 */
@Database(
    entities = [
        TrackEntity::class,
        SnapshotEntity::class,
        SnapshotTrackEntity::class,
        QueueItemEntity::class,
        PlaybackResumeEntity::class,
        LyricsEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class SwirlDatabase : RoomDatabase() {
    abstract fun trackListDao(): TrackListDao
    abstract fun lyricsDao(): LyricsDao
}
