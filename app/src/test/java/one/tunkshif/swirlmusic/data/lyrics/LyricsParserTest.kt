package one.tunkshif.swirlmusic.data.lyrics

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.Lyrics
import one.tunkshif.swirlmusic.data.model.LyricsSource
import org.junit.Test

/**
 * Synthetic LRC/YRC only — invented lyrics that exercise the same quirks as the real payload
 * (stamp styles, JSON credits, breaks, drifted translations).
 */
class LyricsParserTest {

    private fun source(
        lrc: String? = null,
        translation: String? = null,
        romanization: String? = null,
        wordTimed: String? = null,
        pureMusic: Boolean = false,
        noLyric: Boolean = false,
    ) = LyricsSource(lrc, translation, romanization, wordTimed, pureMusic, noLyric)

    private fun synced(source: LyricsSource): Lyrics.Synced =
        LyricsParser.parse(source) as Lyrics.Synced

    @Test
    fun `timestamp forms all parse`() {
        val lyrics = synced(
            source(
                lrc = "[00:11:95]colon centiseconds\n[00:12.025]dot millis\n[01:02.5]one digit",
            ),
        )

        assertThat(lyrics.lines.map { it.startMs })
            .containsExactly(11_950L, 12_025L, 62_500L).inOrder()
        assertThat(lyrics.lines.map { it.text })
            .containsExactly("colon centiseconds", "dot millis", "one digit").inOrder()
    }

    @Test
    fun `a line with several stamps yields one entry per stamp`() {
        val lyrics = synced(source(lrc = "[00:12.00][01:30.00]repeated chorus"))

        assertThat(lyrics.lines.map { it.startMs }).containsExactly(12_000L, 90_000L).inOrder()
        assertThat(lyrics.lines.map { it.text }).containsExactly("repeated chorus", "repeated chorus")
    }

    @Test
    fun `the offset tag shifts every entry`() {
        val lyrics = synced(source(lrc = "[offset:+500]\n[00:10.00]shifted\n[00:20.00]also"))

        assertThat(lyrics.lines.map { it.startMs }).containsExactly(9_500L, 19_500L).inOrder()
    }

    @Test
    fun `metadata tags are ignored`() {
        val lyrics = synced(
            source(lrc = "[ti:An invented title]\n[ar:A made-up artist]\n[by:transcriber]\n[00:01.00]only line"),
        )

        assertThat(lyrics.lines).hasSize(1)
        assertThat(lyrics.lines[0].text).isEqualTo("only line")
    }

    @Test
    fun `json credit lines feed credits even with negative t and malformed json is ignored`() {
        val lyrics = synced(
            source(
                lrc = "{\"t\":0,\"c\":[{\"tx\":\"words: \"},{\"tx\":\"synth poet\"}]}\n" +
                    "{\"t\":-3,\"c\":[{\"tx\":\"music: \"},{\"tx\":\"synth composer\"}]}\n" +
                    "{not valid json\n" +
                    "[00:01.00]a line",
            ),
        )

        assertThat(lyrics.credits).containsExactly("words: synth poet", "music: synth composer").inOrder()
        assertThat(lyrics.lines).hasSize(1)
    }

    @Test
    fun `a blank timed line is a break that ends the previous line`() {
        val lyrics = synced(
            source(lrc = "[00:05.00]first\n[00:10.00]\n[00:20.00]second"),
        )

        assertThat(lyrics.lines).hasSize(2)
        assertThat(lyrics.lines[0].endMs).isEqualTo(10_000L)
        assertThat(lyrics.lines[1].endMs).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `unsorted input is sorted by start`() {
        val lyrics = synced(
            source(lrc = "[00:30.00]third\n[00:10.00]first\n[00:20.00]second"),
        )

        assertThat(lyrics.lines.map { it.startMs })
            .containsExactly(10_000L, 20_000L, 30_000L).inOrder()
    }

    @Test
    fun `translation merges onto the nearest lrc line`() {
        val lyrics = synced(
            source(
                lrc = "[00:10.00]first\n[00:20.00]second",
                translation = "[00:10.40]first translated\n[00:21.00]second translated",
            ),
        )

        assertThat(lyrics.lines[0].translation).isEqualTo("first translated")
        assertThat(lyrics.lines[1].translation).isEqualTo("second translated")
        assertThat(lyrics.hasTranslation).isTrue()
    }

    @Test
    fun `romanization merges when it is a few dozen milliseconds off`() {
        val lyrics = synced(
            source(
                lrc = "[00:10.000]first line",
                romanization = "[00:10.075]roma first line",
            ),
        )

        assertThat(lyrics.lines[0].romanization).isEqualTo("roma first line")
        assertThat(lyrics.hasRomanization).isTrue()
    }

    @Test
    fun `a translation identical to the line text is skipped`() {
        val lyrics = synced(
            source(
                lrc = "[00:10.00]same words",
                translation = "[00:10.05]same words",
            ),
        )

        assertThat(lyrics.lines[0].translation).isNull()
    }

    @Test
    fun `a whitespace-only or distant translation is ignored`() {
        val lyrics = synced(
            source(
                lrc = "[00:10.00]line one",
                translation = "[00:10.10]   \n[01:40.00]too far away",
            ),
        )

        assertThat(lyrics.lines[0].translation).isNull()
    }

    @Test
    fun `yrc words and timing become the primary lines`() {
        val lyrics = synced(
            source(
                lrc = "[00:06.60]ignored lrc text",
                wordTimed = "[0,7590](0,470,0)let(470,2610,0) me(3080,900,0) go",
            ),
        )

        assertThat(lyrics.lines).hasSize(1)
        val line = lyrics.lines[0]
        assertThat(line.startMs).isEqualTo(0L)
        assertThat(line.endMs).isEqualTo(7_590L)
        assertThat(line.words.map { it.text }).containsExactly("let", " me", " go").inOrder()
        assertThat(line.words[0].startMs).isEqualTo(0L)
        assertThat(line.words[1].durationMs).isEqualTo(2_610L)
        assertThat(line.text).isEqualTo("let me go")
        assertThat(lyrics.hasWordTiming).isTrue()
    }

    @Test
    fun `yrc takes the lrc translation by text when starts differ by seconds`() {
        val lyrics = synced(
            source(
                lrc = "[00:06.60]same words here\n[00:15.00]other words",
                translation = "[00:06.62]same words translated\n[00:15.05]other translated",
                // yrc's first line starts at 0 — far from the lrc start of the same text.
                wordTimed = "[0,5000](0,1000,0)same(1000,500,0) words(1500,500,0) here",
            ),
        )

        assertThat(lyrics.lines).hasSize(1)
        assertThat(lyrics.lines[0].translation).isEqualTo("same words translated")
    }

    @Test
    fun `yrc with no parseable lines falls back to lrc`() {
        val lyrics = synced(
            source(
                lrc = "[00:05.00]lrc line",
                wordTimed = "total garbage\n[not,a,yrc,line]",
            ),
        )

        assertThat(lyrics.lines).hasSize(1)
        assertThat(lyrics.lines[0].text).isEqualTo("lrc line")
        assertThat(lyrics.lines[0].words).isEmpty()
    }

    @Test
    fun `pure music is instrumental with credits only`() {
        val lyrics = LyricsParser.parse(
            source(
                lrc = "{\"t\":0,\"c\":[{\"tx\":\"composer: \"},{\"tx\":\"synth keys\"}]}\n[00:05.00]instrumental placeholder",
                pureMusic = true,
            ),
        ) as Lyrics.Instrumental

        assertThat(lyrics.credits).containsExactly("composer: synth keys")
    }

    @Test
    fun `no lyric means none`() {
        assertThat(LyricsParser.parse(source(noLyric = true))).isEqualTo(Lyrics.None)
        assertThat(LyricsParser.parse(source(noLyric = true, lrc = "[00:01.00]text"))).isEqualTo(Lyrics.None)
        assertThat(LyricsParser.parse(source())).isEqualTo(Lyrics.None)
    }

    @Test
    fun `untimed text without stamps is unsynced`() {
        val lyrics = LyricsParser.parse(
            source(lrc = "an untimed first line\nan untimed second line"),
        ) as Lyrics.Unsynced

        assertThat(lyrics.lines).containsExactly("an untimed first line", "an untimed second line").inOrder()
    }

    @Test
    fun `lineIndexAt binary search`() {
        val lyrics = synced(
            source(lrc = "[00:10.00]a\n[00:20.00]b\n[00:30.00]c"),
        )

        assertThat(lyrics.lineIndexAt(5_000)).isEqualTo(-1)
        assertThat(lyrics.lineIndexAt(10_000)).isEqualTo(0)
        assertThat(lyrics.lineIndexAt(15_000)).isEqualTo(0)
        assertThat(lyrics.lineIndexAt(20_000)).isEqualTo(1)
        assertThat(lyrics.lineIndexAt(25_000)).isEqualTo(1)
        assertThat(lyrics.lineIndexAt(99_999)).isEqualTo(2)
    }

    @Test
    fun `lineIndexAt with duplicate starts stays in range`() {
        val lyrics = synced(
            source(lrc = "[00:12.00][00:12.00]dup\n[00:30.00]next"),
        )

        assertThat(lyrics.lineIndexAt(12_000)).isAtLeast(0)
        assertThat(lyrics.lineIndexAt(12_000)).isAtMost(lyrics.lines.lastIndex)
    }

    @Test
    fun `one translation lands on one line only`() {
        val lyrics = synced(
            source(
                lrc = "[00:10.00]first\n[00:10.60]second",
                translation = "[00:10.10]only first translated",
            ),
        )

        assertThat(lyrics.lines[0].translation).isEqualTo("only first translated")
        assertThat(lyrics.lines[1].translation).isNull()
    }
}