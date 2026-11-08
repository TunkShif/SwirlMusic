package one.tunkshif.swirlmusic.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserPlaylistResponse(
    val code: Int = 0,
    val more: Boolean = false,
    val playlist: List<PlaylistDto> = emptyList(),
)

@Serializable
data class PlaylistDetailResponse(
    val code: Int = 0,
    val playlist: PlaylistDto = PlaylistDto(),
)

@Serializable
data class SongDetailResponse(
    val code: Int = 0,
    val songs: List<TrackDto> = emptyList(),
)

@Serializable
data class LikelistResponse(
    val code: Int = 0,
    val ids: List<Long> = emptyList(),
)

@Serializable
data class LikeResponse(
    val code: Int = 0,
)

/** `/user/record` returns the same shape under `allData` or `weekData`. */
@Serializable
data class UserRecordResponse(
    val code: Int = 0,
    val allData: List<PlayRecordDto> = emptyList(),
    val weekData: List<PlayRecordDto> = emptyList(),
)

@Serializable
data class PlayRecordDto(
    val playCount: Int = 0,
    val score: Long = 0,
    val song: TrackDto? = null,
)

/**
 * NetEase is inconsistent about this one: some builds answer `subList`, others `data`, and the
 * album may sit inside the item or be the item. All variants are read, the first non-empty wins.
 */
@Serializable
data class AlbumSublistResponse(
    val code: Int = 0,
    val subList: List<AlbumSubDto> = emptyList(),
    val sublist: List<AlbumSubDto> = emptyList(),
    val data: List<AlbumSubDto> = emptyList(),
    val hasMore: Boolean = false,
    val count: Int = 0,
)

@Serializable
data class AlbumSubDto(
    val album: AlbumDto? = null,
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val size: Int = 0,
    val artists: List<ArtistDto>? = null,
)

@Serializable
data class AlbumDetailResponse(
    val code: Int = 0,
    val album: AlbumDto = AlbumDto(),
    val songs: List<TrackDto> = emptyList(),
)

@Serializable
data class AlbumDynamicResponse(
    val code: Int = 0,
    val isSub: Boolean = false,
)

@Serializable
data class ArtistDynamicResponse(
    val code: Int = 0,
    val followed: Boolean = false,
)

/** `/album/sub`, `/artist/sub` and friends answer `{"code":200}` on success. */
@Serializable
data class WriteResponse(
    val code: Int = 0,
)

@Serializable
data class ArtistSublistResponse(
    val code: Int = 0,
    val data: List<ArtistDto> = emptyList(),
    val hasMore: Boolean = false,
)

@Serializable
data class ArtistDetailResponse(
    val code: Int = 0,
    val artist: ArtistDto = ArtistDto(),
    val hotSongs: List<TrackDto> = emptyList(),
)

@Serializable
data class ArtistAlbumsResponse(
    val code: Int = 0,
    val hotAlbums: List<AlbumDto> = emptyList(),
    val more: Boolean = false,
)

@Serializable
data class ArtistDescResponse(
    val code: Int = 0,
    val briefDesc: String? = null,
    val introduction: List<IntroDto> = emptyList(),
)

@Serializable
data class IntroDto(
    val ti: String = "",
    val txt: String = "",
)

@Serializable
data class SearchResponse(
    val code: Int = 0,
    val result: SearchResultDto = SearchResultDto(),
)

@Serializable
data class SearchResultDto(
    val songs: List<TrackDto> = emptyList(),
    val songCount: Int = 0,
    val albums: List<AlbumDto> = emptyList(),
    val albumCount: Int = 0,
    val artists: List<ArtistDto> = emptyList(),
    val artistCount: Int = 0,
    val playlists: List<PlaylistDto> = emptyList(),
    val playlistCount: Int = 0,
)

@Serializable
data class ArtistProfileResponse(
    val code: Int = 0,
    val data: ArtistProfileData = ArtistProfileData(),
)

@Serializable
data class ArtistProfileData(
    val artist: ArtistProfileDto = ArtistProfileDto(),
)

@Serializable
data class ArtistProfileDto(
    val id: Long = 0,
    val name: String = "",
    val avatar: String? = null,
    val cover: String? = null,
)
