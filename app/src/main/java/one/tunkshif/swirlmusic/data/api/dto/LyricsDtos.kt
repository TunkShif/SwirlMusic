package one.tunkshif.swirlmusic.data.api.dto

import kotlinx.serialization.Serializable

/** `/lyric/new`. Every field defaulted so NetEase can add or rename keys freely. */
@Serializable
data class LyricResponse(
    val code: Int = 0,
    val lrc: LyricBlockDto? = null,
    val tlyric: LyricBlockDto? = null,
    val romalrc: LyricBlockDto? = null,
    val yrc: LyricBlockDto? = null,
    val pureMusic: Boolean = false,
    val nolyric: Boolean = false,
    val uncollected: Boolean = false,
)

@Serializable
data class LyricBlockDto(
    val version: Int = 0,
    val lyric: String? = null,
)
