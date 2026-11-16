package one.tunkshif.swirlmusic.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The account's recently played albums (`/record/recent/album`), newest first. Position is the
 * row id: the whole table is rewritten per refresh, so `position` alone is the key.
 */
@Entity(tableName = "recent_album")
data class RecentAlbumEntity(
    @PrimaryKey val position: Int,
    val albumId: Long,
    val name: String,
    val artistLine: String,
    val artworkUrl: String? = null,
    val trackCount: Int,
    val playedAtMillis: Long,
)
