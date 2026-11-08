package one.tunkshif.swirlmusic.data.model

/** One page of a paginated list. */
data class Page<T>(
    val items: List<T>,
    val hasMore: Boolean,
)

data class AlbumSummary(
    val id: Long,
    val name: String,
    val artistLine: String,
    val artworkUrl: String?,
    val trackCount: Int,
)

data class ArtistSummary(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
)

/**
 * One `/playlist/detail` response: the header, the complete ordered id list, and the first
 * full tracks (the fork sends `n` of them; we ask for 50). Ids beyond `firstTracks` are
 * filled in lazily by `songsByIds` chunks.
 */
data class PlaylistContents(
    val detail: PlaylistDetail,
    val trackIds: List<Long>,
    val firstTracks: List<Track>,
    /** `trackUpdateTime` from the detail payload; null when the server omits it. */
    val trackUpdateTime: Long? = null,
)

data class PlaylistDetail(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
    val trackCount: Int,
    val playCount: Long,
    val creatorName: String?,
    val description: String?,
    val createdAt: Long? = null,
    val tags: List<String> = emptyList(),
)

data class PlayedEntry(
    val track: Track,
    val playCount: Int,
)

data class AlbumDetail(
    val id: Long,
    val name: String,
    val artistLine: String,
    val artworkUrl: String?,
    val publishYear: Int?,
    val tracks: List<Track>,
    val description: String? = null,
    val releasedAt: Long? = null,
    val company: String? = null,
    val albumType: String? = null,
    val artists: List<ArtistRef> = emptyList(),
)

data class ArtistDetail(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
    val tracks: List<Track>,
    val briefDesc: String? = null,
    val songCount: Int? = null,
    val albumCount: Int? = null,
)

enum class SearchType(val apiValue: Int) {
    Songs(1),
    Albums(10),
    Artists(100),
    Playlists(1000),
}

data class SearchResults(
    val songs: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val hasMore: Boolean = false,
)
