package one.tunkshif.swirlmusic.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The saved playback queue: ordered track ids, metadata lives in `track`. */
@Entity(tableName = "queue_item")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val trackId: Long,
)

/** The one resume row: where the queue's current track left off. */
@Entity(tableName = "playback_resume")
data class PlaybackResumeEntity(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
    /** [LoopMode][one.tunkshif.swirlmusic.player.LoopMode] ordinal. */
    val loopMode: Int,
    val savedAt: Long,
)
