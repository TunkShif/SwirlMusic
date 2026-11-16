package one.tunkshif.swirlmusic.data.api.dto

import kotlinx.serialization.Serializable

/** `/recommend/songs` wraps the list in `data.dailySongs`. */
@Serializable
data class RecommendSongsResponse(
    val code: Int = 0,
    val data: RecommendSongsBody = RecommendSongsBody(),
)

@Serializable
data class RecommendSongsBody(
    val dailySongs: List<TrackDto> = emptyList(),
)

@Serializable
data class TrackDto(
    val id: Long = 0,
    val name: String = "",
    val ar: List<ArtistDto> = emptyList(),
    val al: AlbumDto? = null,
    val dt: Long = 0,
    val fee: Int = 0,
    /** `/song/detail` spells the same fields differently from `ar`/`al`. */
    val artists: List<ArtistDto>? = null,
    val album: AlbumDto? = null,
    /** Track number on its disc; 0 = unknown. */
    val no: Int = 0,
    /** Disc number as a string ("1", "2", "01", blank); parsed in the mapper. */
    val cd: String? = null,
)

@Serializable
data class ArtistDto(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val img1v1Url: String? = null,
    val albumSize: Int = 0,
    val musicSize: Int = 0,
    val briefDesc: String? = null,
)

@Serializable
data class AlbumDto(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val coverImgUrl: String? = null,
    val size: Int = 0,
    val publishTime: Long = 0,
    val artists: List<ArtistDto>? = null,
    val description: String? = null,
    val company: String? = null,
    val subType: String? = null,
    val type: String? = null,
    val isSub: Boolean = false,
)


@Serializable
data class PlaylistDto(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    /** Playlists from `/user/playlist` carry `coverImgUrl`, not `picUrl`. */
    val coverImgUrl: String? = null,
    val playcount: Long = 0,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val copywriter: String? = null,
    val description: String? = null,
    val creator: CreatorDto? = null,
    /** Bump when the playlist's track set changes; used as the snapshot version. */
    val trackUpdateTime: Long? = null,
    val createTime: Long = 0,
    val tags: List<String> = emptyList(),
    /** False = created by the user, true = saved. `/user/playlist` only. */
    val subscribed: Boolean = false,
    /** 5 = the liked-songs list; other values are normal playlists. */
    val specialType: Int = 0,
    /** Every song id in order, on `/playlist/detail` only — `"tracks": null` elsewhere. */
    val trackIds: List<TrackIdDto>? = null,
    /** The first `n` full tracks on `/playlist/detail` (we send n=50). */
    val tracks: List<TrackDto>? = null,
)

@Serializable
data class TrackIdDto(
    val id: Long = 0,
)

@Serializable
data class CreatorDto(
    val userId: Long = 0,
    val nickname: String = "",
)

