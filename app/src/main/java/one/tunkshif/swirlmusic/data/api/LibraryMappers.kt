package one.tunkshif.swirlmusic.data.api

import java.time.Instant
import java.time.ZoneOffset
import one.tunkshif.swirlmusic.data.api.dto.AlbumDto
import one.tunkshif.swirlmusic.data.api.dto.AlbumSubDto
import one.tunkshif.swirlmusic.data.api.dto.ArtistDto
import one.tunkshif.swirlmusic.data.api.dto.PlayRecordDto
import one.tunkshif.swirlmusic.data.api.dto.PlaylistDto
import one.tunkshif.swirlmusic.data.model.AlbumSummary
import one.tunkshif.swirlmusic.data.model.ArtistSummary
import one.tunkshif.swirlmusic.data.model.PlayedEntry
import one.tunkshif.swirlmusic.data.model.PlaylistDetail
import one.tunkshif.swirlmusic.data.model.PlaylistSummary

internal fun PlaylistDto.toDetail(): PlaylistDetail = PlaylistDetail(
    id = id,
    name = name,
    artworkUrl = (picUrl ?: coverImgUrl)?.let { sizedArtwork(it, 400) },
    trackCount = trackCount,
    playCount = if (playcount > 0) playcount else playCount,
    creatorName = creator?.nickname?.takeIf { it.isNotBlank() },
    description = description?.takeIf { it.isNotBlank() },
)

internal fun AlbumDto.toSummary(): AlbumSummary = AlbumSummary(
    id = id,
    name = name,
    artistLine = artists.orEmpty().joinToString(" / ") { it.name },
    artworkUrl = (picUrl ?: coverImgUrl)?.let { sizedArtwork(it, 300) },
    trackCount = size,
)

/**
 * `/album/sublist` answers three different shapes depending on the NetEase side. Read them all;
 * the first usable one wins.
 */
internal fun AlbumSubDto.toSummary(): AlbumSummary? {
    val source = album
    if (source != null) return source.toSummary()
    if (id == 0L) return null
    return AlbumSummary(
        id = id,
        name = name,
        artistLine = artists.orEmpty().joinToString(" / ") { it.name },
        artworkUrl = picUrl?.let { sizedArtwork(it, 300) },
        trackCount = size,
    )
}

internal fun ArtistDto.toSummary(): ArtistSummary = ArtistSummary(
    id = id,
    name = name,
    artworkUrl = (picUrl ?: img1v1Url)?.let { sizedArtwork(it, 300) },
)

internal fun PlayRecordDto.toEntry(): PlayedEntry? {
    val track = song?.toDomain() ?: return null
    return PlayedEntry(track = track, playCount = playCount)
}

internal fun publishYearOf(publishTimeMillis: Long): Int =
    Instant.ofEpochMilli(publishTimeMillis).atZone(ZoneOffset.UTC).year
