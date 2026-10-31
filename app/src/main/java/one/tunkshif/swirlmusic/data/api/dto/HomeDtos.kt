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
)

@Serializable
data class ArtistDto(
    val id: Long = 0,
    val name: String = "",
)

@Serializable
data class AlbumDto(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
)

/** `/recommend/resource` returns the list at the top level, with `playcount` spelled lower case. */
@Serializable
data class RecommendResourceResponse(
    val code: Int = 0,
    val recommend: List<PlaylistDto> = emptyList(),
)

@Serializable
data class PlaylistDto(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null,
    val playcount: Long = 0,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val copywriter: String? = null,
)

/** `/toplist` and `/toplist/detail` share the `list` shape. */
@Serializable
data class ToplistResponse(
    val code: Int = 0,
    val list: List<ChartDto> = emptyList(),
)

@Serializable
data class ChartDto(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String? = null,
    val updateFrequency: String? = null,
    val trackCount: Int = 0,
    /**
     * Chart previews are text, not tracks: `{"first": "song", "second": "artist"}`.
     * Full track objects appear in other endpoints, never here.
     */
    val tracks: List<ChartLineDto> = emptyList(),
)

@Serializable
data class ChartLineDto(
    val first: String = "",
    val second: String = "",
)
