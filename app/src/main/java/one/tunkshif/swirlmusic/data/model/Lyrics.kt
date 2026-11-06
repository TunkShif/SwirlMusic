package one.tunkshif.swirlmusic.data.model

/** One word inside a yrc (word-timed) lyric line. */
data class LyricWord(val startMs: Long, val durationMs: Long, val text: String)

data class LyricLine(
    val startMs: Long,
    /**
     * Start of the next timed entry (blank break lines count), yrc start+duration, or
     * [Long.MAX_VALUE] for the last line.
     */
    val endMs: Long,
    val text: String,
    /** Non-empty only when the line came from yrc word timing. */
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>, val credits: List<String>) : Lyrics {
        val hasTranslation: Boolean get() = lines.any { it.translation != null }
        val hasRomanization: Boolean get() = lines.any { it.romanization != null }
        val hasWordTiming: Boolean get() = lines.any { it.words.isNotEmpty() }

        /** Index of the last line with startMs <= [positionMs]; -1 before the first line. */
        fun lineIndexAt(positionMs: Long): Int {
            var index = lines.binarySearch { it.startMs.compareTo(positionMs) }
            if (index < 0) index = -index - 2
            return index.coerceAtMost(lines.lastIndex)
        }
    }

    data class Unsynced(val lines: List<String>, val credits: List<String>) : Lyrics

    data class Instrumental(val credits: List<String>) : Lyrics

    data object None : Lyrics
}

/**
 * Raw strings as NetEase sent them; parsed on every read so parser fixes apply to cached rows.
 */
data class LyricsSource(
    val lrc: String?,
    val translation: String?,
    val romanization: String?,
    val wordTimed: String?,
    val pureMusic: Boolean,
    val noLyric: Boolean,
)
