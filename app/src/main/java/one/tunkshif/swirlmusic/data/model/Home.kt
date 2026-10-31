package one.tunkshif.swirlmusic.data.model

/** A track as the lists on the home screen need it. Full models arrive with the player. */
data class Track(
    val id: Long,
    val title: String,
    val artistLine: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
    val playCount: Long,
    val trackCount: Int,
    val subscribed: Boolean = false,
    val creatorName: String? = null,
    /** `specialType == 5`: the account's liked-songs list. */
    val isLiked: Boolean = false,
)

data class ChartSummary(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
    val updateFrequency: String?,
    val previewLines: List<String>,
)
