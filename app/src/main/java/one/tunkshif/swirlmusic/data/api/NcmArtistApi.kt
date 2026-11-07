package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.api.dto.ArtistProfileResponse

internal const val ARTIST_AVATAR_PX = 120

/** Artist profile bits not carried by track payloads (`/artist/detail`). */
interface NcmArtistApi {
    suspend fun artistAvatar(id: Long): ApiResult<String?>
}

/** Square portrait first, the wide banner as fallback; `sizedArtwork` upgrades to https. */
internal fun ArtistProfileResponse.avatarUrl(): String? =
    (data.artist.avatar?.takeIf { it.isNotBlank() } ?: data.artist.cover?.takeIf { it.isNotBlank() })
        ?.let { sizedArtwork(it, ARTIST_AVATAR_PX) }
