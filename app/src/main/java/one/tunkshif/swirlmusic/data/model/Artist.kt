package one.tunkshif.swirlmusic.data.model

/** One release on the artist page; the type filter (0010-D43) groups these client-side. */
enum class ReleaseKind { Album, Ep, Single, Other }

data class ArtistRelease(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
    /** `publishTime` is UTC midnight; the UI formats the year in UTC. */
    val publishedAt: Long?,
    val kind: ReleaseKind,
    val trackCount: Int,
    /** `/artist/album` `isSub`: the album is in the library (0012-D51). */
    val saved: Boolean = false,
)

data class ArtistDescription(
    val brief: String?,
    val sections: List<DescriptionSection>,
)

data class DescriptionSection(
    val title: String,
    val body: String,
)
