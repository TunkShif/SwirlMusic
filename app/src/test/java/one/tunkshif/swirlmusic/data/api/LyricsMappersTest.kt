package one.tunkshif.swirlmusic.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import one.tunkshif.swirlmusic.data.api.dto.LyricResponse
import one.tunkshif.swirlmusic.data.lyrics.LyricsParser
import one.tunkshif.swirlmusic.data.model.Lyrics
import org.junit.Test

/** Synthetic `/lyric/new` fixtures → LyricsSource → parsed Lyrics. */
class LyricsMappersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun source(fixture: String) =
        json.decodeFromString<LyricResponse>(load(fixture)).toLyricsSource()

    @Test
    fun `translated fixture decodes through unknown keys and merges`() {
        val src = source("lyric_new_translated.json")

        assertThat(src.lrc).isNotNull()
        assertThat(src.translation).isNotNull()
        assertThat(src.romanization).isNotNull()
        assertThat(src.noLyric).isFalse()

        val lyrics = LyricsParser.parse(src) as Lyrics.Synced
        assertThat(lyrics.lines.map { it.startMs })
            .containsExactly(11_950L, 20_500L, 40_025L, 65_000L).inOrder()
        assertThat(lyrics.lines[0].translation).isEqualTo("translated first line")
        assertThat(lyrics.lines[0].romanization).isEqualTo("roma first line")
        // The break line at 30 s ends the second line's window.
        assertThat(lyrics.lines[1].endMs).isEqualTo(30_000L)
        assertThat(lyrics.credits).contains("words: synth poet")
    }

    @Test
    fun `word timed fixture yields yrc lines with translation merged by text`() {
        val src = source("lyric_new_word_timed.json")

        assertThat(src.wordTimed).isNotNull()
        val lyrics = LyricsParser.parse(src) as Lyrics.Synced

        assertThat(lyrics.hasWordTiming).isTrue()
        assertThat(lyrics.lines).hasSize(2)
        val first = lyrics.lines[0]
        assertThat(first.startMs).isEqualTo(0L)
        assertThat(first.endMs).isEqualTo(4_800L)
        assertThat(first.words).hasSize(5)
        assertThat(first.text).isEqualTo("synthetic words line one")
        assertThat(first.translation).isEqualTo("translated words one")
        assertThat(lyrics.credits).contains("music: synth composer")
        assertThat(lyrics.credits).contains("yrc credit: synth writer")
    }

    @Test
    fun `pure music fixture is instrumental with credits`() {
        val src = source("lyric_new_pure_music.json")
        val lyrics = LyricsParser.parse(src) as Lyrics.Instrumental

        // lrc credits then yrc credits, deduped.
        assertThat(lyrics.credits).containsExactly(
            "composer: synth keys",
            "arranger: synth pads",
            "yrc credit: synth keys",
        ).inOrder()
    }

    @Test
    fun `blank lyric blocks map to null and uncollected counts as noLyric`() {
        val blank = json.decodeFromString<LyricResponse>(
            """{"code":200,"lrc":{"version":1,"lyric":"  "},"uncollected":true}""",
        ).toLyricsSource()

        assertThat(blank.lrc).isNull()
        assertThat(blank.noLyric).isTrue()
    }

    private fun load(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/ncm/$name")) {
            "missing fixture $name"
        }.readText()
}
