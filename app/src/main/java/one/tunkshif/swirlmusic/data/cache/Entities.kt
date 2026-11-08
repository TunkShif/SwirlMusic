package one.tunkshif.swirlmusic.data.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.tunkshif.swirlmusic.data.model.ArtistRef
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
    /** Compact encoding of the artist list; [artistLine] stays the display string. */
    val artists: String? = null,
) {
    fun toTrack(): Track = Track(
        id, title, artistLine, artworkUrl, durationMs, discNumber, trackNumber, albumId, albumName,
        artists = decodeArtistRefs(artists),
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
            artists = encodeArtistRefs(track.artists),
        )
    }
}

@Serializable
private data class ArtistRefJson(val id: Long, val name: String)

private val artistJson = Json { ignoreUnknownKeys = true }

/** Compact JSON for an artist list; null when empty so the column stays sparse. */
private fun encodeArtistRefs(artists: List<ArtistRef>): String? =
    artists.takeIf { it.isNotEmpty() }
        ?.let { list -> artistJson.encodeToString(list.map { ArtistRefJson(it.id, it.name) }) }

/** Malformed JSON (or null) reads back as an empty list, never a crash. */
internal fun decodeArtistRefs(encoded: String?): List<ArtistRef> = runCatching {
    encoded?.let { artistJson.decodeFromString<List<ArtistRefJson>>(it) }
        .orEmpty()
        .map { ArtistRef(it.id, it.name) }
}.getOrDefault(emptyList())

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
    /** Album header artists, same JSON encoding as [TrackEntity.artists]. */
    val artists: String? = null,
) {
    fun artistRefs(): List<ArtistRef> = decodeArtistRefs(artists)

    companion object {
        fun encodedArtists(artists: List<ArtistRef>): String? = encodeArtistRefs(artists)
    }
}

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

/** Last-known save/follow state per collection item (0012-D51). Account-scoped. */
@Entity(tableName = "collection_state", primaryKeys = ["kind", "id"])
data class CollectionStateEntity(
    val kind: String,
    val id: Long,
    val active: Boolean,
    val updatedAt: Long,
)
