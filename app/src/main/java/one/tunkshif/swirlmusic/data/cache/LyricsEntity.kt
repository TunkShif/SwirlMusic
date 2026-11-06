package one.tunkshif.swirlmusic.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import one.tunkshif.swirlmusic.data.model.LyricsSource

/**
 * Raw `/lyric/new` strings, kept so a parser fix applies to already-cached rows on the next read.
 * Public content: this table is deliberately NOT part of the account-scoped cache clear.
 */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackId: Long,
    val lrc: String?,
    val translation: String?,
    val romanization: String?,
    val wordTimed: String?,
    val pureMusic: Boolean,
    val noLyric: Boolean,
    val fetchedAt: Long,
)

internal fun LyricsEntity.toSource(): LyricsSource = LyricsSource(
    lrc = lrc,
    translation = translation,
    romanization = romanization,
    wordTimed = wordTimed,
    pureMusic = pureMusic,
    noLyric = noLyric,
)

internal fun LyricsSource.toEntity(trackId: Long, fetchedAt: Long): LyricsEntity = LyricsEntity(
    trackId = trackId,
    lrc = lrc,
    translation = translation,
    romanization = romanization,
    wordTimed = wordTimed,
    pureMusic = pureMusic,
    noLyric = noLyric,
    fetchedAt = fetchedAt,
)
