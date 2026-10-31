package one.tunkshif.swirlmusic.ui.tracks

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import one.tunkshif.swirlmusic.data.model.Track

sealed interface AlbumRow {
    data class Disc(val number: Int) : AlbumRow
    data class Song(val track: Track, val index: Int, val number: Int) : AlbumRow
}

/**
 * Insert "Disc N" headers at each disc change — only when more than one distinct
 * discNumber exists. Song `number` falls back to per-disc position.
 */
internal fun albumRows(tracks: List<Track>): List<AlbumRow> {
    val discs = tracks.mapNotNull { it.discNumber }.distinct()
    if (discs.size <= 1) {
        return tracks.mapIndexed { i, t -> AlbumRow.Song(t, i, t.trackNumber ?: (i + 1)) }
    }
    val rows = mutableListOf<AlbumRow>()
    var lastDisc: Int? = null
    var discPos = 0
    tracks.forEachIndexed { index, track ->
        if (track.discNumber != lastDisc) {
            if (track.discNumber != null) rows += AlbumRow.Disc(track.discNumber)
            lastDisc = track.discNumber
            discPos = 0
        }
        discPos++
        rows += AlbumRow.Song(track, index, track.trackNumber ?: discPos)
    }
    return rows
}

/** Line-2 date under a detail header. */
enum class DateKind { Created, Released }

/**
 * `Created` is an instant in [zone] (the device zone by default). `Released` is a UTC DATE
 * (midnight epoch ms) and must not shift a day in western zones.
 */
internal fun formatDetailDate(
    kind: DateKind,
    millis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val date = when (kind) {
        DateKind.Created -> Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        DateKind.Released -> Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
    }
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}
