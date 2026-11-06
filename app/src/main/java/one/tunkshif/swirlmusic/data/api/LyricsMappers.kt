package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.api.dto.LyricResponse
import one.tunkshif.swirlmusic.data.model.LyricsSource

internal fun LyricResponse.toLyricsSource(): LyricsSource = LyricsSource(
    lrc = lrc?.lyric?.takeIf { it.isNotBlank() },
    translation = tlyric?.lyric?.takeIf { it.isNotBlank() },
    romanization = romalrc?.lyric?.takeIf { it.isNotBlank() },
    wordTimed = yrc?.lyric?.takeIf { it.isNotBlank() },
    pureMusic = pureMusic,
    noLyric = nolyric || uncollected,
)
