package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.api.dto.ChartDto
import one.tunkshif.swirlmusic.data.api.dto.PlaylistDto
import one.tunkshif.swirlmusic.data.api.dto.TrackDto
import one.tunkshif.swirlmusic.data.model.ChartSummary
import one.tunkshif.swirlmusic.data.model.PlaylistSummary
import one.tunkshif.swirlmusic.data.model.Track

/** How many charts the home preview shows. */
internal const val CHART_PREVIEW_COUNT = 3

private const val CHART_LINE_PREVIEW = 3

internal fun TrackDto.toDomain(): Track {
    // `/recommend/songs` uses `ar` and `al`; `/search` and `/song/detail` use `artists` and `album`.
    val artists = ar.ifEmpty { artists.orEmpty() }
    val album = al ?: album
    return Track(
        id = id,
        title = name,
        artistLine = artists.joinToString(" / ") { it.name },
        artworkUrl = (album?.picUrl ?: album?.coverImgUrl)?.let { sizedArtwork(it, 300) },
        durationMs = dt,
        discNumber = cd?.trim()?.toIntOrNull()?.takeIf { it > 0 },
        trackNumber = no.takeIf { it > 0 },
    )
}

internal fun PlaylistDto.toDomain(): PlaylistSummary = PlaylistSummary(
    id = id,
    name = name,
    subscribed = subscribed,
    creatorName = creator?.nickname?.takeIf { it.isNotBlank() },
    isLiked = specialType == 5,
    artworkUrl = (picUrl ?: coverImgUrl)?.let { sizedArtwork(it, 300) },
    // NetEase spells this `playcount` here and `playCount` elsewhere; accept both.
    playCount = if (playcount > 0) playcount else playCount,
    trackCount = trackCount,
)

internal fun ChartDto.toDomain(): ChartSummary = ChartSummary(
    id = id,
    name = name,
    artworkUrl = coverImgUrl?.let { sizedArtwork(it, 160) },
    updateFrequency = updateFrequency?.takeIf { it.isNotBlank() },
    previewLines = tracks
        .take(CHART_LINE_PREVIEW)
        .map { line -> line.first + if (line.second.isBlank()) "" else " — " + line.second },
)

/**
 * NetEase serves any size from the original: `?param=WxH`. Asking for the size we draw keeps
 * memory down and the list smooth.
 */
internal fun sizedArtwork(url: String, size: Int): String {
    val https = url.replaceFirst("http://", "https://")
    val separator = if (https.contains('?')) '&' else '?'
    return "$https${separator}param=${size}y$size"
}
