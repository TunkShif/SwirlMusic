package one.tunkshif.swirlmusic.data.lyrics

import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import one.tunkshif.swirlmusic.data.model.LyricLine
import one.tunkshif.swirlmusic.data.model.LyricWord
import one.tunkshif.swirlmusic.data.model.Lyrics
import one.tunkshif.swirlmusic.data.model.LyricsSource

/**
 * Turns the raw LRC/TLYRIC/ROMALRC/YRC blocks NetEase returns into [Lyrics]. Pure and fast
 * enough to run on every read of a cached row.
 *
 * Quirks handled: `[mm:ss:cc]` and `[mm:ss.mmm]` stamps, several stamps on one line, the
 * `[offset:±N]` tag, `{"t":…,"c":[{"tx":…}]}` JSON credit lines (negative `t` included),
 * whitespace-only timed lines as breaks, and yrc `[s,d](ws,wd,i)word` word timing.
 */
object LyricsParser {

    private val json = Json

    private val STAMP = Regex("""\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val TAG = Regex("""^\[([a-zA-Z]+):([^\]]*)\]""")
    private val OFFSET = Regex("""^([+-]?\d+)""")
    private val YRC_LINE = Regex("""^\[(\d+),(\d+)\]""")
    private val YRC_WORD = Regex("""\((\d+),(\d+),(\d*)\)""")

    private const val TRANSLATE_WINDOW_MS = 1_000L
    private const val TEXT_MATCH_SCAN = 5

    /** A timed slot from one stamp; blank [text] means a break line, not a lyric line. */
    private data class TimedEntry(val startMs: Long, val text: String)

    private data class LrcBlock(
        /** Timed entries in file order, including break lines. */
        val timed: List<TimedEntry>,
        /** Untimed non-blank lines, in file order. */
        val plain: List<String>,
        val credits: List<String>,
    )

    fun parse(source: LyricsSource): Lyrics {
        val lrcBlock = source.lrc?.let(::parseLrc)
        val yrcBlock = source.wordTimed?.let(::parseLrc)
        val credits = ((lrcBlock?.credits).orEmpty() + (yrcBlock?.credits).orEmpty()).distinct()

        if (source.pureMusic) return Lyrics.Instrumental(credits)
        if (source.noLyric || (source.lrc == null && source.wordTimed == null)) return Lyrics.None

        val lrcLines = lrcBlock?.let { mergeSecondaries(it, source) }.orEmpty()

        val yrcLines = source.wordTimed
            ?.let(::parseYrc)
            .orEmpty()

        return when {
            yrcLines.isNotEmpty() -> {
                Lyrics.Synced(
                    lines = attachToYrc(yrcLines, lrcLines),
                    credits = credits,
                )
            }
            lrcLines.isNotEmpty() -> Lyrics.Synced(lines = lrcLines, credits = credits)
            !lrcBlock?.plain.isNullOrEmpty() ->
                Lyrics.Unsynced(lines = lrcBlock.plain, credits = credits)
            else -> Lyrics.None
        }
    }

    /** One LRC-flavoured block (lrc, tlyric or romalrc share the grammar). */
    private fun parseLrc(raw: String): LrcBlock {
        val timed = mutableListOf<TimedEntry>()
        val plain = mutableListOf<String>()
        val credits = mutableListOf<String>()
        var offset = 0L

        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("{")) {
                parseCredit(line)?.let { if (it.isNotBlank()) credits += it }
                continue
            }

            // Consume every leading [mm:ss(.|:)frac] stamp; a line may carry several.
            var position = 0
            val stamps = mutableListOf<Long>()
            while (position < line.length && line[position] == '[') {
                val match = STAMP.matchAt(line, position) ?: break
                stamps += toMs(match)
                position += match.value.length
            }

            if (stamps.isEmpty()) {
                val tag = TAG.find(line)
                if (tag != null) {
                    if (tag.groupValues[1].equals("offset", ignoreCase = true)) {
                        offset = OFFSET.find(tag.groupValues[2])?.value?.toLongOrNull() ?: 0L
                    }
                    continue
                }
                if (line.isNotBlank()) plain += line
                continue
            }

            val text = line.substring(position).trim()
            for (stamp in stamps) timed += TimedEntry(stamp - offset, text)
        }
        return LrcBlock(timed, plain, credits)
    }

    private fun toMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        val fracMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return minutes * 60_000 + seconds * 1_000 + fracMs
    }

    /** `{"t":0,"c":[{"tx":"…"},…]}` → the concatenated credit text, or null when malformed. */
    private fun parseCredit(line: String): String? = runCatching {
        val element = json.parseToJsonElement(line).jsonObject
        element["c"]?.jsonArray
            ?.mapNotNull { it.jsonObject["tx"]?.jsonPrimitive?.content }
            ?.joinToString("")
            ?.trim()
    }.getOrNull()

    /** Sort timed entries, drop blank break lines, and give each line the next entry's start. */
    private fun toLines(block: LrcBlock): List<LyricLine> {
        val sorted = block.timed.sortedBy { it.startMs }
        val lines = mutableListOf<LyricLine>()
        for ((index, entry) in sorted.withIndex()) {
            if (entry.text.isBlank()) continue
            // The next timed slot (break lines included) closes this line's window.
            val endMs = sorted.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE
            lines += LyricLine(startMs = entry.startMs, endMs = endMs, text = entry.text)
        }
        return lines
    }

    /**
     * Merge translation/romanization onto the primary LRC lines: each secondary timed entry
     * goes to the primary line with the nearest start within [TRANSLATE_WINDOW_MS], first come
     * first served, and an identical copy of the line text is skipped.
     */
    private fun mergeSecondaries(lrc: LrcBlock, source: LyricsSource): List<LyricLine> {
        val lines = toLines(lrc).toMutableList()
        if (lines.isEmpty()) return lines
        applySecondary(
            lines,
            source.translation?.let(::parseLrc)?.timed.orEmpty(),
            current = { it.translation },
            set = { line, value -> line.copy(translation = value) },
        )
        applySecondary(
            lines,
            source.romanization?.let(::parseLrc)?.timed.orEmpty(),
            current = { it.romanization },
            set = { line, value -> line.copy(romanization = value) },
        )
        return lines
    }

    /**
     * Each non-blank secondary entry attaches to the nearest primary line within
     * [TRANSLATE_WINDOW_MS] that has no value for that kind and whose own text differs. An entry
     * can land on only one line, so adjacent lines never duplicate it.
     */
    private inline fun applySecondary(
        lines: MutableList<LyricLine>,
        entries: List<TimedEntry>,
        current: (LyricLine) -> String?,
        set: (LyricLine, String) -> LyricLine,
    ) {
        for (entry in entries) {
            if (entry.text.isBlank()) continue
            val index = lines.indices
                .filter { abs(lines[it].startMs - entry.startMs) <= TRANSLATE_WINDOW_MS }
                .minByOrNull { abs(lines[it].startMs - entry.startMs) } ?: continue
            val line = lines[index]
            if (current(line) != null || entry.text.trim() == line.text.trim()) continue
            lines[index] = set(line, entry.text)
        }
    }

    /** yrc: `[s,d](ws,wd,_)word…`. Returns parsed lines; empty when nothing parses. */
    private fun parseYrc(raw: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("{")) continue
            val head = YRC_LINE.find(line) ?: continue
            val startMs = head.groupValues[1].toLong()
            val durationMs = head.groupValues[2].toLong()

            val words = mutableListOf<LyricWord>()
            var position = head.value.length
            while (position < line.length) {
                val wordMatch = YRC_WORD.matchAt(line, position) ?: break
                position += wordMatch.value.length
                var end = line.indexOf('(', position)
                if (end < 0) end = line.length
                words += LyricWord(
                    startMs = wordMatch.groupValues[1].toLong(),
                    durationMs = wordMatch.groupValues[2].toLong(),
                    text = line.substring(position, end),
                )
                position = end
            }
            if (words.isEmpty()) continue

            // Words keep their inner spaces; only the line's outer padding is stripped.
            val text = words.joinToString("") { it.text }.trim()
            if (text.isBlank()) continue
            lines += LyricLine(
                startMs = startMs,
                endMs = startMs + durationMs,
                text = text,
                words = words,
            )
        }
        return lines.sortedBy { it.startMs }
    }

    /**
     * Each yrc line borrows the translation/romanization of the merged LRC line with the same
     * normalized text (scanning forward from the last match, within [TEXT_MATCH_SCAN] lines);
     * when no text matches, the nearest LRC start within [TRANSLATE_WINDOW_MS] wins.
     */
    private fun attachToYrc(yrc: List<LyricLine>, lrc: List<LyricLine>): List<LyricLine> {
        if (lrc.isEmpty()) return yrc
        var lastMatch = 0
        return yrc.map { line ->
            val normalized = normalize(line.text)
            var matched: LyricLine? = null
            if (normalized.isNotEmpty()) {
                val start = lastMatch
                val end = minOf(lrc.size, start + TEXT_MATCH_SCAN)
                for (index in start until end) {
                    if (normalize(lrc[index].text) == normalized) {
                        matched = lrc[index]
                        lastMatch = index
                        break
                    }
                }
            }
            if (matched == null) {
                matched = lrc
                    .filter { abs(it.startMs - line.startMs) <= TRANSLATE_WINDOW_MS }
                    .minByOrNull { abs(it.startMs - line.startMs) }
            }
            line.copy(
                translation = matched?.translation,
                romanization = matched?.romanization,
            )
        }
    }

    private fun normalize(text: String): String =
        text.filter { !it.isWhitespace() }.lowercase()
}
