package one.tunkshif.swirlmusic.data.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface TrackListDao {

    @Query("SELECT * FROM snapshot WHERE sourceKey = :key")
    fun snapshot(key: String): SnapshotEntity?

    @Query("SELECT trackId FROM snapshot_track WHERE sourceKey = :key ORDER BY position")
    fun orderedIds(key: String): List<Long>

    @Query("SELECT * FROM track WHERE id IN (:ids)")
    fun tracksByIds(ids: List<Long>): List<TrackEntity>

    @Upsert
    fun upsertTracks(tracks: List<TrackEntity>)

    @Transaction
    fun replaceSnapshot(snapshot: SnapshotEntity, ids: List<Long>) {
        upsertSnapshot(snapshot)
        deleteSnapshotTracks(snapshot.sourceKey)
        insertSnapshotTracks(ids.mapIndexed { i, id -> SnapshotTrackEntity(snapshot.sourceKey, i, id) })
    }

    @Upsert
    fun upsertSnapshot(snapshot: SnapshotEntity)

    @Query("DELETE FROM snapshot_track WHERE sourceKey = :key")
    fun deleteSnapshotTracks(key: String)

    @Query("INSERT INTO snapshot_track VALUES (:sourceKey, :position, :trackId)")
    fun insertSnapshotTrack(sourceKey: String, position: Int, trackId: Long)

    fun insertSnapshotTracks(rows: List<SnapshotTrackEntity>) {
        rows.forEach { insertSnapshotTrack(it.sourceKey, it.position, it.trackId) }
    }

    @Query("UPDATE snapshot SET lastOpenedAt = :now WHERE sourceKey = :key")
    fun touch(key: String, now: Long)

    @Transaction
    fun evict(keep: Int) {
        evictOldSnapshots(keep)
        deleteOrphanTracks()
    }

    @Query("DELETE FROM snapshot WHERE sourceKey NOT IN (SELECT sourceKey FROM snapshot ORDER BY lastOpenedAt DESC LIMIT :keep)")
    fun evictOldSnapshots(keep: Int)

    @Query("DELETE FROM track WHERE id NOT IN (SELECT trackId FROM snapshot_track UNION SELECT trackId FROM queue_item)")
    fun deleteOrphanTracks()

    @Query("DELETE FROM snapshot")
    fun clearSnapshots()

    @Query("DELETE FROM snapshot_track")
    fun clearSnapshotTracks()

    @Query("DELETE FROM track")
    fun clearTracks()

    @Transaction
    fun clearAll() {
        clearSnapshots()
        clearSnapshotTracks()
        clearTracks()
        clearQueue()
        clearResume()
    }

    // Resume playback state

    @Query("DELETE FROM queue_item")
    fun clearQueue()

    @Query("DELETE FROM playback_resume")
    fun clearResume()

    @Query("SELECT trackId FROM queue_item ORDER BY position")
    fun queueIds(): List<Long>

    @Query("SELECT * FROM playback_resume WHERE id = 0")
    fun resume(): PlaybackResumeEntity?

    @Transaction
    fun replaceQueue(trackIds: List<Long>) {
        clearQueue()
        trackIds.forEachIndexed { i, id -> upsertQueueItem(QueueItemEntity(i, id)) }
    }

    @androidx.room.Upsert
    fun upsertQueueItem(item: QueueItemEntity)

    @androidx.room.Upsert
    fun saveResume(row: PlaybackResumeEntity)
}
