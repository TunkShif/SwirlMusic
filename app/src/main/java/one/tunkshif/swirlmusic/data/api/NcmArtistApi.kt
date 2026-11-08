package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.api.dto.ArtistDescResponse
import one.tunkshif.swirlmusic.data.api.dto.ArtistProfileResponse
import one.tunkshif.swirlmusic.data.model.ArtistDescription
import one.tunkshif.swirlmusic.data.model.DescriptionSection
import one.tunkshif.swirlmusic.data.model.ReleaseKind
import one.tunkshif.swirlmusic.data.model.ArtistRelease
import one.tunkshif.swirlmusic.data.model.Page

internal const val ARTIST_AVATAR_PX = 120

/** Artist profile bits not carried by track payloads (`/artist/detail`). */
interface NcmArtistApi {
    suspend fun artistAvatar(id: Long): ApiResult<String?>

    /** `/artist/album` pages of the discography, newest first. */
    suspend fun artistAlbums(id: Long, limit: Int, offset: Int): ApiResult<Page<ArtistRelease>>

    /** `/artist/desc` biography sections. */
    suspend fun artistDescription(id: Long): ApiResult<ArtistDescription>
}

/** Square portrait first, the wide banner as fallback; `sizedArtwork` upgrades to https. */
internal fun ArtistProfileResponse.avatarUrl(): String? =
    (data.artist.avatar?.takeIf { it.isNotBlank() } ?: data.artist.cover?.takeIf { it.isNotBlank() })
        ?.let { sizedArtwork(it, ARTIST_AVATAR_PX) }

/** `type` on `/artist/album` items (0010-D43); unknown values are Other, never dropped. */
internal fun releaseKindOf(type: String?): ReleaseKind = when (type?.trim()) {
    "专辑" -> ReleaseKind.Album
    "EP", "EP/Single" -> ReleaseKind.Ep
    "Single" -> ReleaseKind.Single
    else -> ReleaseKind.Other
}

internal fun one.tunkshif.swirlmusic.data.api.dto.AlbumDto.toRelease(): ArtistRelease? {
    if (id <= 0) return null
    return ArtistRelease(
        id = id,
        name = name,
        artworkUrl = (picUrl ?: coverImgUrl)?.let { sizedArtwork(it, 300) },
        publishedAt = publishTime.takeIf { it > 0 },
        kind = releaseKindOf(type),
        trackCount = size,
    )
}

internal fun ArtistDescResponse.toDescription(): ArtistDescription = ArtistDescription(
    brief = briefDesc?.takeIf { it.isNotBlank() },
    sections = introduction
        .filter { it.txt.isNotBlank() }
        .map { DescriptionSection(it.ti.trim(), it.txt.trim()) },
)
