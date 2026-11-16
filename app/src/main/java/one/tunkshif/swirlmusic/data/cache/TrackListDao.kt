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

    // Collection state (0012-D51)
    @Query("SELECT active FROM collection_state WHERE kind = :kind AND id = :id")
    fun collectionState(kind: String, id: Long): Boolean?

    @Upsert
    fun upsertCollectionStates(states: List<CollectionStateEntity>)

    @Query("DELETE FROM collection_state WHERE kind = :kind")
    fun deleteCollectionStates(kind: String)

    /** A likelist load is the full truth for [kind]: rewrite its rows in one transaction. */
    @Transaction
    fun replaceCollectionStates(kind: String, states: List<CollectionStateEntity>) {
        deleteCollectionStates(kind)
        upsertCollectionStates(states)
    }

    @Query("DELETE FROM collection_state")
    fun clearCollectionStates()

    /** A null version makes the next fetch see `orderChanged` and rewrite the order. */
    @Query("UPDATE snapshot SET version = NULL WHERE sourceKey = :key")
    fun clearSnapshotVersion(key: String)

    @Transaction
    fun clearAll() {
        clearSnapshots()
        clearSnapshotTracks()
        clearTracks()
        clearQueue()
        clearResume()
        clearCollectionStates()
        clearRecentAlbums()
    }

    // Recently played albums (account data — `/record/recent/album`, newest first)

    @Query("SELECT * FROM recent_album ORDER BY position")
    fun recentAlbums(): List<RecentAlbumEntity>

    @Query("DELETE FROM recent_album")
    fun clearRecentAlbums()

    @Transaction
    fun replaceRecentAlbums(albums: List<RecentAlbumEntity>) {
        clearRecentAlbums()
        albums.forEach(::upsertRecentAlbum)
    }

    @androidx.room.Upsert
    fun upsertRecentAlbum(entity: RecentAlbumEntity)

    // Resume playback state

    @Query("DELETE FROM queue_item")
    fun clearQueue()

    @Query("DELETE FROM playback_resume")
    fun clearResume()

    @Query("SELECT * FROM queue_item ORDER BY position")
    fun queueItems(): List<QueueItemEntity>

    @Query("SELECT * FROM playback_resume WHERE id = 0")
    fun resume(): PlaybackResumeEntity?

    /** Each pair is a track id and the album it was played from, if any. */
    @Transaction
    fun replaceQueue(entries: List<Pair<Long, Long?>>) {
        clearQueue()
        entries.forEachIndexed { i, (id, sourceAlbumId) ->
            upsertQueueItem(QueueItemEntity(i, id, sourceAlbumId))
        }
    }

    @androidx.room.Upsert
    fun upsertQueueItem(item: QueueItemEntity)

    @androidx.room.Upsert
    fun saveResume(row: PlaybackResumeEntity)
}
