package one.tunkshif.swirlmusic.data.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import one.tunkshif.swirlmusic.data.model.Track

/** Metadata for one track; playability and stream URLs are never cached. */
@Entity(tableName = "track")
data class TrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artistLine: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val fetchedAt: Long,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val albumId: Long? = null,
    val albumName: String? = null,
) {
    fun toTrack(): Track = Track(
        id, title, artistLine, artworkUrl, durationMs, discNumber, trackNumber, albumId, albumName,
    )

    companion object {
        fun of(track: Track, now: Long) = TrackEntity(
            id = track.id,
            title = track.title,
            artistLine = track.artistLine,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs,
            fetchedAt = now,
            discNumber = track.discNumber,
            trackNumber = track.trackNumber,
            albumId = track.albumId,
            albumName = track.albumName,
        )
    }
}

/** One cached detail list: `playlist:<id>`, `album:<id>` or `artist:<id>`. */
@Entity(tableName = "snapshot")
data class SnapshotEntity(
    @PrimaryKey val sourceKey: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val trackCount: Int,
    /** `trackUpdateTime` for playlists; null for albums and artists. */
    val version: Long?,
    val fetchedAt: Long,
    val lastOpenedAt: Long,
    val description: String? = null,
    val dateMillis: Long? = null,
    val company: String? = null,
    val albumType: String? = null,
    /** Newline-joined; playlists only. */
    val tags: String? = null,
)

@Entity(
    tableName = "snapshot_track",
    primaryKeys = ["sourceKey", "position"],
    indices = [Index("trackId"), Index("sourceKey")],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["sourceKey"],
            childColumns = ["sourceKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SnapshotTrackEntity(
    val sourceKey: String,
    val position: Int,
    val trackId: Long,
)
